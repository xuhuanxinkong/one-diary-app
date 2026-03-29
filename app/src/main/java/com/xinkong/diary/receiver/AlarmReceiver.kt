package com.xinkong.diary.receiver

import android.app.AlarmManager

import android.app.PendingIntent
import androidx.work.Data
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.xinkong.diary.data.AlarmEntity
import com.xinkong.diary.worker.AiTaskWorker
import com.xinkong.diary.utils.NotificationHelper
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val ALARM_CHANNEL_ID = "ALARM_CHANNEL"
        private const val ALARM_CHANNEL_NAME = "闹钟提醒"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val actionType = intent.getStringExtra("ACTION_TYPE") ?: "REMIND"
        val alarmName = intent.getStringExtra("ALARM_NAME") ?: "提醒"

        if (alarmId == -1) return

        if (actionType == "REMIND") {
            // 1. 纯提醒：直接弹通知（不需要联网，不需要查库，最快响应）
            NotificationHelper.sendAlarmNotification(context, alarmId, alarmName, true)

            // 2. 周期处理：依然启动一个 Worker 来静默更新数据库（比如更新下次闹钟时间）
            // 这样可以确保 Receiver 瞬间结束，不会被系统拦截
        }

        // 无论是 REMIND 后的周期更新，还是 PROCESS_NOTE 的 AI 任务
        // 统统交给 WorkManager
        val inputData = Data.Builder()
            .putInt("ALARM_ID", alarmId)
            .putString("ACTION_TYPE", actionType)
            .putLong("TRIGGER_TIME", System.currentTimeMillis())
            .build()

        val workRequest = OneTimeWorkRequestBuilder<AiTaskWorker>()
            .setInputData(inputData)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(
                if (actionType == "PROCESS_NOTE") NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
            ).build())
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}

object ChainAlarmHelper {
    
    fun scheduleNextAlarm(context: Context, alarm: AlarmEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_NAME", alarm.name)
            // 关键：必须带上这个，否则 Receiver 里的 actionType 永远是默认值
            putExtra("ACTION_TYPE", alarm.actionType)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 计算触发时间
        val targetTime = calculateNextTriggerTime(alarm)
        
        // 确保权限，如果是在 Android 12 (S) 及以上，需要申请 SCHEDULE_EXACT_ALARM 权限
        // 但是对于普通精确闹钟，可以用 setExactAndAllowWhileIdle
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetTime,
                pendingIntent
            )
            Log.d("ChainAlarmHelper", "Scheduled next alarm for ID: ${alarm.id} at $targetTime")
        } catch (e: SecurityException) {
            Log.e("ChainAlarmHelper", "Missing exact alarm permission", e)
        }
    }
    
    fun cancelAlarm(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun calculateNextTriggerTime(alarm: AlarmEntity): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val now = Calendar.getInstance()
        
        // If time has already passed today, advance to tomorrow first
        if (calendar.before(now)) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        // If it's a repeating alarm, we must ensure the day of week is in repeatDays
        if (alarm.repeatDays.isNotEmpty()) {
            while (!alarm.repeatDays.contains(calendar.get(Calendar.DAY_OF_WEEK))) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        return calendar.timeInMillis
    }
}