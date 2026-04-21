package com.xinkong.diary.repository

import androidx.room.Entity

@Entity(
    tableName = "tag_folders",
    primaryKeys = ["name", "type"] // type could be "Diary" or "Chat"
)
data class TagFolder(
    val name: String,
    val type: String,
    val isHidden: Boolean = false,
    val isAiBound: Boolean = false, // AI绑定的文件夹，不允许用户删除
    val orderIndex: Int = 0 // 用于自定义排序
)

@Entity(
    tableName = "diary_tags",
    primaryKeys = ["name", "folder"]
)
data class DiaryTag(
    val name: String,
    val colorInt: Int,
    val bg2Int: Int,
    val border2Int: Int,
    val folder: String = "我的笔记"
)

@Entity(
    tableName = "chat_tags",
    primaryKeys = ["name", "folder"]
)
data class ChatTag(
    val name: String,
    val colorInt: Int,
    val bg2Int: Int,
    val border2Int: Int,
    val folder: String = "我的笔记"
)
