package com.xinkong.diary.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xinkong.diary.Http.AiHttp
import com.xinkong.diary.data.AiResponse
import com.xinkong.diary.data.AlarmEntity
import com.xinkong.diary.receiver.ChainAlarmHelper
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.DiaryDao
import com.xinkong.diary.repository.ChatMessage
import com.xinkong.diary.utils.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiTaskWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AiTaskWorker"
    }

    override suspend fun doWork(): Result {
        val alarmId = inputData.getInt("ALARM_ID", -1)
        if (alarmId == -1) return Result.failure()
        val actionType = inputData.getString("ACTION_TYPE") ?: "REMIND"

        val db = AppDatabase.getDatabase(context)
        val alarmDao = db.alarmDao()
        val chatDao = db.chatDao()
        val diaryDao = db.diaryDao()

        var currentAlarm = alarmDao.getAlarmByIdSync(alarmId) ?: return Result.failure()

        val triggerTime = inputData.getLong("TRIGGER_TIME", -1L)
        val now = System.currentTimeMillis()
        if (actionType == "PROCESS_NOTE" && triggerTime > 0 && now - triggerTime > 60 * 60 * 1000L) {
            currentAlarm = currentAlarm.copy(taskStatus = "FAILED", lastHeartbeat = now)
            alarmDao.updateAlarm(currentAlarm)
            NotificationHelper.sendAlarmNotification(context, currentAlarm.id, "${currentAlarm.name}：任务超时未执行", true)
            return Result.failure()
        }

        val currentTime = System.currentTimeMillis()
        if (currentAlarm.taskStatus == "RUNNING" && currentTime - currentAlarm.lastHeartbeat < 3600000) {
            return Result.retry()
        }

        currentAlarm = currentAlarm.copy(
            taskStatus = "RUNNING",
            lastHeartbeat = currentTime,
            retryCount = currentAlarm.retryCount + 1
        )
        alarmDao.updateAlarm(currentAlarm)
        if (actionType == "PROCESS_NOTE") {
            NotificationHelper.sendAlarmNotification(context, currentAlarm.id, "${currentAlarm.name}：AI任务开始执行", true)
        }

        return try {
            val success = withTimeoutOrNull(5 * 60 * 1000L) {
                when (actionType) {
                    "PROCESS_NOTE" -> executeAiReminder(chatDao, diaryDao, currentAlarm)
                    else -> {
                        delay(50)
                        true
                    }
                }
            } ?: false

            if (success) {
                if (currentAlarm.repeatDays.isEmpty()) {
                    currentAlarm = currentAlarm.copy(isActive = false, taskStatus = "COMPLETED")
                } else {
                    ChainAlarmHelper.scheduleNextAlarm(context, currentAlarm)
                    currentAlarm = currentAlarm.copy(taskStatus = "COMPLETED")
                }
                currentAlarm = currentAlarm.copy(lastHeartbeat = System.currentTimeMillis())
                alarmDao.updateAlarm(currentAlarm)
                Result.success()
            } else {
                if (runAttemptCount > 3) {
                    alarmDao.updateAlarm(currentAlarm.copy(taskStatus = "FAILED"))
                    NotificationHelper.sendAlarmNotification(context, currentAlarm.id, "${currentAlarm.name}：AI任务执行失败", true)
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing task", e)
            alarmDao.updateAlarm(currentAlarm.copy(taskStatus = "FAILED", lastHeartbeat = System.currentTimeMillis()))
            NotificationHelper.sendAlarmNotification(context, currentAlarm.id, "${currentAlarm.name}：AI任务异常", true)
            Result.failure()
        }
    }

    private suspend fun executeAiReminder(
        chatDao: com.xinkong.diary.repository.ChatDao,
        diaryDao: DiaryDao,
        alarm: AlarmEntity
    ): Boolean {
        val aiId = parseAiId(alarm.taskPayload)
        val aiConfig = when {
            aiId != null -> chatDao.getAiConfigById(aiId)
            else -> null
        } ?: chatDao.getFirstAiConfig() ?: return false
        val chat = chatDao.getChatByIdSuspend(aiConfig.chatId) ?: return false
        val contextText = buildAiContextText(aiConfig.referencedDiaryId, diaryDao)
        val taskPrompt = alarm.remark.ifBlank { "请总结今天新增或最近更新的笔记，并给出3条行动建议。" }
        val systemInstruction = buildString {
            append("你正在执行一个由闹钟触发的定时AI任务。")
            append("任务名称：${alarm.name}。")
            append("当前时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}。")
            append("必须优先完成“任务指令”，再参考上下文输出结果。")
            append("输出要求：先给结论，再给关键要点，语言简洁。")
            if (contextText.isNotBlank()) {
                append("\n【可用上下文】\n$contextText")
            }
        }
        val messages = listOf(
            mapOf("role" to "system", "content" to systemInstruction),
            mapOf("role" to "user", "content" to "任务指令（最高优先级）：$taskPrompt")
        )
        val result = AiHttp().chatWithAi(aiConfig, messages)
        if (result.isFailure) {
            Log.e(TAG, "AI request failed: ${result.exceptionOrNull()?.message}")
            return false
        }

        val response = result.getOrNull()
        val content = (response as? AiResponse.Message)?.content?.ifBlank { "提醒：${alarm.name}" } ?: "提醒：${alarm.name}"
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        chatDao.insertMessage(
            ChatMessage(
                chatId = chat.id,
                role = "assistant",
                content = content,
                date = now,
                photoUris = "[]",
                toolExecutions = "[]",
                aiId = aiConfig.id
            )
        )
        chatDao.updateChat(chat.copy(unreadCount = chat.unreadCount + 1))
        NotificationHelper.sendAiMessageNotification(
            context = context,
            notificationId = chat.id.toInt(),
            senderName = aiConfig.name,
            messageText = content,
            isHighPriority = true
        )
        NotificationHelper.sendAlarmNotification(context, alarm.id, "${alarm.name}：AI任务已完成", true)
        return true
    }

    private fun parseAiId(taskPayload: String?): Long? {
        if (taskPayload.isNullOrBlank()) return null
        return try {
            val id = JSONObject(taskPayload).optLong("aiId", -1L)
            id.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun buildAiContextText(referencedDiaryId: String, diaryDao: DiaryDao): String {
        if (referencedDiaryId.isBlank()) return ""
        val ids = try {
            Json.decodeFromString<List<Long>>(referencedDiaryId)
        } catch (_: Exception) {
            emptyList()
        }
        if (ids.isEmpty()) return ""
        val diaries = diaryDao.getDiariesByIds(ids)
        if (diaries.isEmpty()) return ""
        return diaries.joinToString("\n\n") {
            "标题：${it.title}\n时间：${it.date}\n内容：${it.text.ifEmpty { it.content }}"
        }
    }
}
