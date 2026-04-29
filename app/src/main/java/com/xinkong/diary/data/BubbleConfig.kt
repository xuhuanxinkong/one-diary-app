package com.xinkong.diary.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bubble_config")
data class BubbleConfig(
    @PrimaryKey
    val id: Int = 1,
    val textSize: Float = 16f,
    val showRagResult: Boolean = true,
    val showToolResult: Boolean = true,
    val showVisibility: Boolean = true
)
