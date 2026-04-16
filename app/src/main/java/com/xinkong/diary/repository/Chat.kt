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
    val tag:String = "未分类",
    val tagFolder: String = "我的笔记",
    val backgroundUri: String = "",
    val unreadCount: Int = 0, // 未读消息计数
    val historyRounds: Int = 12, // 历史对话轮数，默认12轮
    val isGroupChat: Boolean = false, // 是否为群聊
    val groupAvatarUri: String = "" // 群聊头像
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
    val date: String,
    val photoUris: String = "[]", // To store local file paths of images
    val toolExecutions: String = "[]", // JSON string of List<String>
    val aiId: Long? = null, // 用于记录是哪个 AI 发送的消息
    val reasoningContent: String? = null // 存储深度思考的内容
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
    val name: String = "Ai助手",
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val avatarUri: String = "",
    val referencedDiaryId:String = "[]",
    val enableReadNotes: Boolean = true,
    val enableWriteNote: Boolean = true,
    val enableEditNote: Boolean = true,
    val enableStream: Boolean = false,
    val enableImageSupport: Boolean = false,
    val enableWebSearch: Boolean = false,
    val enableSetAlarm: Boolean = true,
    val enableDeepThink: Boolean = false,
    val isEnabled: Boolean = true,
    val replyOrder: Int = 0,
    // AI绑定的文件夹（AI对笔记的操作限定在此文件夹）
    val boundFolder: String = ""
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
    val name: String = "用户",
    val avatarUri: String = "",
    val referencedDiaryId: String = ""
)

/**
 * 群聊成员关系表：记录群聊中包含哪些AI（通过引用原始AI的ID）
 * 这样可以在群聊设置中从AI列表选择AI添加到群聊
 * 群聊特有的设置（如回复顺序、是否启用）保存在此表中
 */
@Entity(
    tableName = "group_chat_members",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["groupChatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupChatId"])]
)
data class GroupChatMember(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupChatId: Long,   // 群聊的ID
    val sourceAiId: Long,    // 引用的原始AI配置ID（来自AI列表）
    val replyOrder: Int = 0, // 群聊中的回复顺序
    val isEnabled: Boolean = true // 该AI在群聊中是否启用
)







// 定义 Chat 的 Saver
val ChatSaver = Saver<Chat, Map<String, Any>>(
    save = { chat ->
        mapOf(
            "id" to chat.id,
            "title" to chat.title,
            "date" to chat.date,
            "tag" to chat.tag,
            "tagFolder" to chat.tagFolder,
            "backgroundUri" to chat.backgroundUri,
            "historyRounds" to chat.historyRounds
        )
    },
    restore = { map ->
        Chat(
            id = (map["id"] as Number).toLong(),
            title = map["title"] as String,
            date = map["date"] as String,
            tag = map["tag"] as String,
            tagFolder = map["tagFolder"] as? String ?: "我的笔记",
            backgroundUri = map["backgroundUri"] as? String ?: "",
            historyRounds = (map["historyRounds"] as? Number)?.toInt() ?: 12
        )
    }
)