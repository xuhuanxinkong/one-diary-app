package com.xinkong.diary.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.xinkong.diary.MainActivity
import com.xinkong.diary.R
import com.xinkong.diary.receiver.AlarmRingActivity

object NotificationHelper {

    private const val ALARM_CHANNEL_ID = "ALARM_CHANNEL_V2"
    private const val ALARM_CHANNEL_NAME = "重要提醒(闹钟与AI消息)"
    private const val MESSAGE_CHANNEL_ID = "MESSAGE_CHANNEL_V2"
    private const val MESSAGE_CHANNEL_NAME = "普通消息"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. 高优渠道（用于闹钟或需要强提醒的AI消息），带有锁屏展示和声音
            val alarmChannel = NotificationChannel(ALARM_CHANNEL_ID, ALARM_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setBypassDnd(true) // 允许绕过免打扰
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                setSound(
                    alarmUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM) // 采用闹钟音频通道，保证音量
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            // 2. 普通消息渠道
            val msgChannel = NotificationChannel(MESSAGE_CHANNEL_ID, MESSAGE_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(msgChannel)
        }
    }

    /**
     * 发送类似微信的AI消息通知（基于 MessagingStyle）
     */
    fun sendAiMessageNotification(context: Context, notificationId: Int, senderName: String, messageText: String, isHighPriority: Boolean = false) {
        createChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构造用户或AI的头像 (示例用系统图标兜底，可换成AI特有头像)
        val aiPerson = Person.Builder()
            .setName(senderName)
            .build()
            
        val messagingStyle = NotificationCompat.MessagingStyle(aiPerson)
            .addMessage(messageText, System.currentTimeMillis(), aiPerson)

        val channelId = if (isHighPriority) ALARM_CHANNEL_ID else MESSAGE_CHANNEL_ID

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round) // 这里需要使用应用的实际图标
            .setStyle(messagingStyle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * 发送闹钟的高优先级弹窗通知，可结合全屏意图
     */
    fun sendAlarmNotification(context: Context, alarmId: Int, alarmName: String, enableFullScreenAlarm: Boolean) {
        createChannels(context)

        val aiPerson = Person.Builder()
            .setName("日历提醒")
            .build()
            
        val messagingStyle = NotificationCompat.MessagingStyle(aiPerson)
            .addMessage("您有一个新的闹钟：$alarmName", System.currentTimeMillis(), aiPerson)

        val builder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setStyle(messagingStyle)
            .setContentTitle("闹钟提醒")
            .setContentText(alarmName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)

        // 如果用户在系统设置里开启了强拉闹钟模式，并且屏幕处于熄屏状态（全屏意图才能生效）
        if (enableFullScreenAlarm) {
            val ringIntent = Intent(context, AlarmRingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("ALARM_ID", alarmId)
                putExtra("ALARM_NAME", alarmName)
            }
            val fullScreenIntent = PendingIntent.getActivity(
                context,
                alarmId,
                ringIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(fullScreenIntent, true)
        } else {
            // 点击只打开 MainActivity
            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                alarmId,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(alarmId, builder.build())
    }
}
