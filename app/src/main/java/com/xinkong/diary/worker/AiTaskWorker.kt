package com.xinkong.diary.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xinkong.diary.receiver.ChainAlarmHelper
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.Chat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

class AiTaskWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val alarmId = inputData.getInt("ALARM_ID", -1)
        if (alarmId == -1) return Result.failure()

        val db = AppDatabase.getDatabase(context)
        val alarmDao = db.alarmDao()

        // 1. 拿到初始快照，后续尽量基于此对象操作
        var currentAlarm = alarmDao.getAlarmByIdSync(alarmId) ?: return Result.failure()

        // 检查是否超时（无网络延迟执行超过1小时）
        val triggerTime = inputData.getLong("TRIGGER_TIME", -1L)
        val now = System.currentTimeMillis()
        if (triggerTime > 0 && now - triggerTime > 60 * 60 * 1000L) {
            // 写入AI失败消息
            val chatDao = db.chatDao()
            val chatId = 1L // TODO: 可根据实际业务逻辑获取
            val chat = chatDao.findChatById(chatId).firstOrNull() ?: com.xinkong.diary.repository.Chat(id = chatId, title = "AI提醒", date = "", unreadCount = 0)
            val failMsg = com.xinkong.diary.repository.ChatMessage(
                chatId = chat.id,
                role = "AI",
                content = "过去一小时未连接到网络，AI任务执行失败",
                date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                photoUris = "[]",
                toolExecutions = "[]",
                aiId = null
            )
            chatDao.insertMessage(failMsg)
            val updatedChat = chat.copy(unreadCount = chat.unreadCount + 1)
            chatDao.updateChat(updatedChat)
            return Result.failure()
        }

        // 防僵尸任务逻辑 (保持不变)
        val currentTime = System.currentTimeMillis()
        if (currentAlarm.taskStatus == "RUNNING" && currentTime - currentAlarm.lastHeartbeat < 3600000) {
            return Result.retry()
        }

        // 更新为运行中
        currentAlarm = currentAlarm.copy(
            taskStatus = "RUNNING",
            lastHeartbeat = currentTime,
            retryCount = currentAlarm.retryCount + 1
        )
        alarmDao.updateAlarm(currentAlarm)

        return try {
            val success = withTimeoutOrNull(5 * 60 * 1000L) {
                // TODO: AI 逻辑处理
                delay(2000) // 模拟
                true
            } ?: false

            if (success) {
                // 2. 闭环处理：同时处理周期和状态
                if (currentAlarm.repeatDays.isEmpty()) {
                    currentAlarm = currentAlarm.copy(isActive = false, taskStatus = "COMPLETED")
                } else {
                    // 在 Worker 线程调用是安全的
                    ChainAlarmHelper.scheduleNextAlarm(context, currentAlarm)
                    currentAlarm = currentAlarm.copy(taskStatus = "COMPLETED")
                }
                currentAlarm = currentAlarm.copy(lastHeartbeat = System.currentTimeMillis())
                alarmDao.updateAlarm(currentAlarm)

                // ====== 新增：AI消息推送与未读处理 ======
                val db = AppDatabase.getDatabase(context)
                val chatDao = db.chatDao()
                // 这里假设每个闹钟有唯一chatId，若无可用chatId可用默认1或查找第一个Chat
                val chatId = 1L // TODO: 可根据实际业务逻辑获取
                val chat = chatDao.findChatById(chatId).firstOrNull() ?: Chat(
                    id = chatId,
                    title = "AI提醒",
                    date = "",
                    unreadCount = 0
                )
                // 插入AI消息
                val aiMsg = com.xinkong.diary.repository.ChatMessage(
                    chatId = chat.id,
                    role = "AI",
                    content = "AI 提醒任务已完成！",
                    date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                    photoUris = "[]",
                    toolExecutions = "[]",
                    aiId = null
                )
                chatDao.insertMessage(aiMsg)
                // 更新未读数
                val updatedChat = chat.copy(unreadCount = chat.unreadCount + 1)
                chatDao.updateChat(updatedChat)
                // 发送AI通知
                try {
                    com.xinkong.diary.utils.NotificationHelper.sendAiMessageNotification(
                        context,
                        notificationId = chat.id.toInt(),
                        senderName = "AI助手",
                        messageText = "AI 提醒任务已完成！",
                        isHighPriority = true
                    )
                } catch (e: Exception) {
                    Log.e("AiTaskWorker", "通知发送失败", e)
                }
                // ====== END ======

                Result.success()
            } else {
                // 3. 失败处理：检查重试次数，防止耗尽用户流量/电量
                if (runAttemptCount > 3) {
                    alarmDao.updateAlarm(currentAlarm.copy(taskStatus = "FAILED"))
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e("AiTaskWorker", "Error executing AI task", e)
            // 尽量捕获最新状态写入失败
            currentAlarm.let {
                alarmDao.updateAlarm(it.copy(taskStatus = "FAILED"))
            }
            Result.failure()
        }
    }
}