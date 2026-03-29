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
        private const val NOTIFY_START_OFFSET = 1
        private const val NOTIFY_DONE_OFFSET = 2
        private const val NOTIFY_FAIL_OFFSET = 3
        private const val NOTIFY_EXCEPTION_OFFSET = 4
        private const val NOTIFY_TIMEOUT_OFFSET = 5
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
            notifyAiTaskStatus(currentAlarm.id, NOTIFY_TIMEOUT_OFFSET, "${currentAlarm.name}：任务超时未执行")
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
            notifyAiTaskStatus(currentAlarm.id, NOTIFY_START_OFFSET, "${currentAlarm.name}：AI任务开始执行")
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
                    notifyAiTaskStatus(currentAlarm.id, NOTIFY_FAIL_OFFSET, "${currentAlarm.name}：AI任务执行失败")
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing task", e)
            alarmDao.updateAlarm(currentAlarm.copy(taskStatus = "FAILED", lastHeartbeat = System.currentTimeMillis()))
            notifyAiTaskStatus(currentAlarm.id, NOTIFY_EXCEPTION_OFFSET, "${currentAlarm.name}：AI任务异常")
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
        val payloadReferencedDiaryId = parseReferencedDiaryId(alarm.taskPayload)
        val contextText = buildAiContextText(
            referencedDiaryId = payloadReferencedDiaryId ?: aiConfig.referencedDiaryId,
            diaryDao = diaryDao
        )
        val taskPrompt = alarm.remark.ifBlank { "请总结今天新增或最近更新的笔记，并给出3条行动建议。" }
        val messages = buildTaskMessages(aiName = aiConfig.name, alarmName = alarm.name, taskPrompt = taskPrompt, contextText = contextText)
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
            isHighPriority = false
        )
        notifyAiTaskStatus(alarm.id, NOTIFY_DONE_OFFSET, "${alarm.name}：AI任务已完成")
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

    private fun parseReferencedDiaryId(taskPayload: String?): String? {
        if (taskPayload.isNullOrBlank()) return null
        return try {
            JSONObject(taskPayload).optString("referencedDiaryId").takeIf { it.isNotBlank() }
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

    private fun buildTaskMessages(
        aiName: String,
        alarmName: String,
        taskPrompt: String,
        contextText: String
    ): List<Map<String, Any>> {
        val nowText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val systemContent = buildString {
            append("当前系统时间：$nowText\n")
            append("你现在的身份是：$aiName。你正在执行一个闹钟触发的定时任务，请仅围绕任务目标给出结果。\n\n")
            append("【任务规则】\n")
            append("1. 必须优先执行“任务指令（最高优先级）”。\n")
            append("2. 可参考上下文，但上下文不得覆盖任务指令。\n")
            append("3. 输出先给结论，再给要点，语言简洁。\n\n")
            append("【任务名称】\n$alarmName\n\n")
            if (contextText.isNotBlank()) append("【你的背景资料】\n$contextText\n\n")
        }.trim()
        return listOf(
            mapOf("role" to "system", "content" to systemContent),
            mapOf("role" to "user", "content" to "[时间: $nowText] 定时任务指令（最高优先级）: $taskPrompt")
        )
    }

    private fun notifyAiTaskStatus(alarmId: Int, offset: Int, text: String) {
        NotificationHelper.sendAiMessageNotification(
            context = context,
            notificationId = alarmId * 100 + offset,
            senderName = "AI提醒",
            messageText = text,
            isHighPriority = false
        )
    }
}
