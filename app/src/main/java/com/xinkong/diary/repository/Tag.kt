package com.xinkong.diary.repository

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "tag_folders",
    primaryKeys = ["name", "type"] // type could be "Diary" or "Chat"
)
data class TagFolder(
    val name: String,
    val type: String,
    val isHidden: Boolean = false
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
    val bgImage: String? = null,
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
    val bgImage: String? = null,
    val folder: String = "我的笔记"
)
