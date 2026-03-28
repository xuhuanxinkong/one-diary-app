package com.xinkong.diary.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "闹钟",
    val hour: Int,
    val minute: Int,
    val isActive: Boolean = true,
    // Days of the week (1=Mon, 7=Sun). Empty means runs only once or chained from a specific date.
    val repeatDays: List<Int> = emptyList(),
    // Duration to ring in minutes
    val ringDuration: Int = 5,
    // Interval before ringing again in minutes
    val snoozeInterval: Int = 10,
    // Number of times it can snooze
    val snoozeCount: Int = 3,
    // Any remark from user or AI
    val remark: String = "",
    // When 12-hour format is required in memory, though we save time as 24-hr format.
    val isAm: Boolean = hour < 12,
    // Custom ringtone URI if set, empty if default
    val ringtoneUri: String = ""
)
