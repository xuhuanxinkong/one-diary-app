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
import com.xinkong.diary.repository.ChatMessage
import com.xinkong.diary.utils.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
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

        var currentAlarm = alarmDao.getAlarmByIdSync(alarmId) ?: return Result.failure()

        val triggerTime = inputData.getLong("TRIGGER_TIME", -1L)
        val now = System.currentTimeMillis()
        if (actionType == "PROCESS_NOTE" && triggerTime > 0 && now - triggerTime > 60 * 60 * 1000L) {
            currentAlarm = currentAlarm.copy(taskStatus = "FAILED", lastHeartbeat = now)
            alarmDao.updateAlarm(currentAlarm)
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

        return try {
            val success = withTimeoutOrNull(5 * 60 * 1000L) {
                when (actionType) {
                    "PROCESS_NOTE" -> executeAiReminder(chatDao, currentAlarm)
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
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing task", e)
            alarmDao.updateAlarm(currentAlarm.copy(taskStatus = "FAILED", lastHeartbeat = System.currentTimeMillis()))
            Result.failure()
        }
    }

    private suspend fun executeAiReminder(
        chatDao: com.xinkong.diary.repository.ChatDao,
        alarm: AlarmEntity
    ): Boolean {
        val aiId = parseAiId(alarm.taskPayload) ?: return false
        val aiConfig = chatDao.getAiConfigById(aiId) ?: return false
        val chat = chatDao.getChatByIdSuspend(aiConfig.chatId) ?: return false

        val prompt = alarm.remark.ifBlank {
            "请以${aiConfig.name}的身份，结合当前时间，给我一条简洁提醒：${alarm.name}"
        }
        val messages = listOf(mapOf("role" to "user", "content" to prompt))
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
}
