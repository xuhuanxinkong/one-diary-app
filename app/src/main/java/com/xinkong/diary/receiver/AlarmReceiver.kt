package com.xinkong.diary.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xinkong.diary.data.AlarmEntity
import com.xinkong.diary.utils.NotificationHelper
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val ALARM_CHANNEL_ID = "ALARM_CHANNEL"
        private const val ALARM_CHANNEL_NAME = "闹钟提醒"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val alarmName = intent.getStringExtra("ALARM_NAME") ?: "闹钟"
        
        Log.d("AlarmReceiver", "Alarm triggered! ID: $alarmId, Name: $alarmName")
        
        // 使用 NotificationHelper 发送“通知级别的闹钟”（微信风格）
        // 这里默认 enableFullScreenAlarm = true（如果屏熄也会尝试拉起锁屏 Activity）
        NotificationHelper.sendAlarmNotification(context, alarmId, alarmName, true)

        // 2. 链式计算下一个闹钟时间并重新注册
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.xinkong.diary.repository.AppDatabase.getDatabase(context)
                val alarm = db.alarmDao().getAlarmByIdSync(alarmId)
                if (alarm != null && alarm.isActive) {
                    if (alarm.repeatDays.isEmpty()) {
                        db.alarmDao().updateAlarm(alarm.copy(isActive = false))
                    } else {
                        ChainAlarmHelper.scheduleNextAlarm(context, alarm)
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error scheduling next alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object ChainAlarmHelper {
    
    fun scheduleNextAlarm(context: Context, alarm: AlarmEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_NAME", alarm.name)
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