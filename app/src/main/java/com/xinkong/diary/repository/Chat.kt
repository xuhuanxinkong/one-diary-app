package com.xinkong.diary.repository

import androidx.compose.runtime.saveable.Saver
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey(autoGenerate = true)
    val id:Long=0,
    val title: String ="",
    val date: String = "",
    val tag:String = "未分类"
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chatId"])] // 为 chatId 添加索引
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long,
    val role: String,
    val content: String,
    val date: String
)

@Entity(
    tableName = "ai_chat_configs",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chatId"])] // 为 chatId 添加索引
)
data class AiChatConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long,
    val baseUrl: String = "http://10.0.2.2:11434/v1/chat/completions",
    val model: String = "deepseek-r1:7b",
    val apiKey: String = "",
    val avatarUri: String = "",
    val referencedDiaryId:String = "[]",
    val enableAllNotes: Boolean = false
)



@Entity(
    tableName = "user_chat_configs",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chatId"])] // 为 chatId 添加索引
)
data class UserChatConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val chatId: Long,
    val avatarUri: String = "",
    val referencedDiaryId: String = ""
)







// 定义 Chat 的 Saver
val ChatSaver = Saver<Chat, Map<String, Any>>(
    save = { chat ->
        mapOf(
            "id" to chat.id,
            "title" to chat.title,
            "date" to chat.date,
            "tag" to chat.tag
        )
    },
    restore = { map ->
        Chat(
            id = (map["id"] as Number).toLong(),
            title = map["title"] as String,
            date = map["date"] as String,
            tag = map["tag"] as String
        )
    }
)