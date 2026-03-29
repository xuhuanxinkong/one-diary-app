package com.xinkong.diary.worker

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.data.AlarmEntity
import com.xinkong.diary.receiver.ChainAlarmHelper
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.utils.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

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
        val chatViewModel = ChatViewModel(context.applicationContext as Application)

        var currentAlarm = alarmDao.getAlarmByIdSync(alarmId) ?: return Result.failure()
        val alarmAvatarUri = parseAvatarUri(currentAlarm.taskPayload)

        val triggerTime = inputData.getLong("TRIGGER_TIME", -1L)
        val now = System.currentTimeMillis()
        if (actionType == "PROCESS_NOTE" && triggerTime > 0 && now - triggerTime > 60 * 60 * 1000L) {
            currentAlarm = currentAlarm.copy(taskStatus = "FAILED", lastHeartbeat = now)
            alarmDao.updateAlarm(currentAlarm)
            notifyAiTaskStatus(currentAlarm.id, NOTIFY_TIMEOUT_OFFSET, "${currentAlarm.name}：任务超时未执行", alarmAvatarUri)
            return Result.failure()
        }

        val currentTime = System.currentTimeMillis()
        if (currentAlarm.taskStatus == "RUNNING" && runAttemptCount == 0 && currentTime - currentAlarm.lastHeartbeat < 3600000) {
            return Result.retry()
        }

        currentAlarm = currentAlarm.copy(
            taskStatus = "RUNNING",
            lastHeartbeat = currentTime,
            retryCount = currentAlarm.retryCount + 1
        )
        alarmDao.updateAlarm(currentAlarm)
        if (actionType == "PROCESS_NOTE") {
            notifyAiTaskStatus(currentAlarm.id, NOTIFY_START_OFFSET, "${currentAlarm.name}：AI任务开始执行", alarmAvatarUri)
        }

        return try {
            val success = withTimeoutOrNull(5 * 60 * 1000L) {
                when (actionType) {
                    "PROCESS_NOTE" -> executeAiReminder(chatDao, chatViewModel, currentAlarm)
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
                    notifyAiTaskStatus(currentAlarm.id, NOTIFY_FAIL_OFFSET, "${currentAlarm.name}：AI任务执行失败", alarmAvatarUri)
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing task", e)
            alarmDao.updateAlarm(currentAlarm.copy(taskStatus = "FAILED", lastHeartbeat = System.currentTimeMillis()))
            notifyAiTaskStatus(currentAlarm.id, NOTIFY_EXCEPTION_OFFSET, "${currentAlarm.name}：AI任务异常", alarmAvatarUri)
            Result.failure()
        }
    }

    private suspend fun executeAiReminder(
        chatDao: com.xinkong.diary.repository.ChatDao,
        chatViewModel: ChatViewModel,
        alarm: AlarmEntity
    ): Boolean {
        val aiId = parseAiId(alarm.taskPayload)
        val aiConfig = when {
            aiId != null -> chatDao.getAiConfigById(aiId)
            else -> null
        } ?: chatDao.getFirstAiConfig() ?: return false
        val chat = chatDao.getChatByIdSuspend(aiConfig.chatId) ?: return false
        val payloadReferencedDiaryId = parseReferencedDiaryId(alarm.taskPayload)
        val payloadAvatarUri = parseAvatarUri(alarm.taskPayload)
        val resolvedAvatarUri = payloadAvatarUri ?: aiConfig.avatarUri
        val taskPrompt = alarm.remark.ifBlank { "请总结今天新增或最近更新的笔记，并给出3条行动建议。" }
        val result = chatViewModel.sendAlarmTaskMessage(
            chatId = chat.id,
            aiConfig = aiConfig,
            taskPrompt = taskPrompt,
            referencedDiaryIdOverride = payloadReferencedDiaryId
        )
        if (result.isFailure) {
            Log.e(TAG, "AI request failed: ${result.exceptionOrNull()?.message}")
            NotificationHelper.sendAiMessageNotification(
                context = context,
                notificationId = alarm.id * 100 + NOTIFY_FAIL_OFFSET,
                senderName = aiConfig.name,
                messageText = "${alarm.name}：${result.exceptionOrNull()?.message ?: "执行失败"}",
                isHighPriority = false,
                avatarUri = resolvedAvatarUri
            )
            return false
        }
        val content = result.getOrDefault("提醒：${alarm.name}")
        chatDao.updateChat(chat.copy(unreadCount = chat.unreadCount + 1))
        NotificationHelper.sendAiMessageNotification(
            context = context,
            notificationId = chat.id.toInt(),
            senderName = aiConfig.name,
            messageText = content,
            isHighPriority = false,
            avatarUri = resolvedAvatarUri
        )
        notifyAiTaskStatus(alarm.id, NOTIFY_DONE_OFFSET, "${alarm.name}：AI任务已完成", resolvedAvatarUri)
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

    private fun parseAvatarUri(taskPayload: String?): String? {
        if (taskPayload.isNullOrBlank()) return null
        return try {
            JSONObject(taskPayload).optString("avatarUri").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun notifyAiTaskStatus(alarmId: Int, offset: Int, text: String, avatarUri: String? = null) {
        NotificationHelper.sendAiMessageNotification(
            context = context,
            notificationId = alarmId * 100 + offset,
            senderName = "AI提醒",
            messageText = text,
            isHighPriority = false,
            avatarUri = avatarUri
        )
    }
}
