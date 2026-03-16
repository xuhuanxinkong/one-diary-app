package com.xinkong.diary.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    //对话相关 Chat
    @Insert
    suspend fun insertChat(chat: Chat): Long

    @Update
    suspend fun updateChat(chat: Chat)

    @Delete
    suspend fun deleteChat(chat: Chat)

    @Query("SELECT * FROM chats")
    fun getAllChat(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun findChatById(chatId: Long): Flow<Chat>

    @Query("SELECT * FROM chats WHERE tag =:tag")
    fun getChatByTag(tag: String): Flow<List<Chat>>


    //消息相关 ChatMessage
    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Insert
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Query("SELECT * FROM chat_messages WHERE chatId =:chatId")
    fun getMessages(chatId:Long):Flow<List<ChatMessage>>

    @Delete
    suspend fun deleteMessage(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY id ASC")
    suspend fun getMessagesOnce(chatId: Long): List<ChatMessage>


    //配置相关 AiChatConfig
    @Insert
    suspend fun insertAiConfig(config: AiChatConfig)

    @Update
    suspend fun updateAiConfig(config: AiChatConfig)

    @Query("SELECT * FROM ai_chat_configs WHERE chatId = :chatId")
    fun findAiConfig(chatId: Long): Flow<AiChatConfig>

    @Query("SELECT * FROM ai_chat_configs WHERE chatId = :chatId LIMIT 1")
    suspend fun getAiConfigOnce(chatId: Long): AiChatConfig?

    //配置相关 UserChatConfig
    @Insert
    suspend fun insertUserConfig(config: UserChatConfig)

    @Update
    suspend fun updateUserConfig(config: UserChatConfig)

    @Query("SELECT * FROM user_chat_configs WHERE chatId = :chatId")
    fun findUserConfig(chatId: Long): Flow<UserChatConfig>

    @Query("SELECT * FROM user_chat_configs WHERE chatId = :chatId LIMIT 1")
    suspend fun getUserConfigOnce(chatId: Long): UserChatConfig?
}