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
    // 持续时间
    val ringDuration: Int = 5,
    // 间隔
    val snoozeInterval: Int = 10,
    // 重响次数
    val snoozeCount: Int = 3,
    // 备注
    val remark: String = "",
    // 12/24时制
    val isAm: Boolean = hour < 12,
    // 铃声
    val ringtoneUri: String = "",
    
    // ======== AI后台任务与提醒扩展 ========
    // 动作类型: "REMIND"(纯提醒/闹钟), "PROCESS_NOTE"(后台AI处理笔记), 等
    val actionType: String = "REMIND",
    // 任务状态: "PENDING", "RUNNING", "COMPLETED", "FAILED"
    val taskStatus: String = "PENDING",
    // 任务执行所需的上下文 (JSON 等格式)
    val taskPayload: String? = null,
    // 最后活跃时间，用于检测“僵尸任务”
    val lastHeartbeat: Long = 0L,
    // 重试次数
    val retryCount: Int = 0
)
