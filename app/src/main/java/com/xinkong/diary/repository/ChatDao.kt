package com.xinkong.diary.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    // 在 ChatDao 中增加一个 suspend 方法，专门用于这种一次性的逻辑操作
    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatByIdSuspend(chatId: Long): Chat?

    @Query("SELECT * FROM chats WHERE tag =:tag")
    fun getChatByTag(tag: String): Flow<List<Chat>>

    @Query(
        """
        SELECT * FROM chats
        WHERE title LIKE '%' || :keyword || '%'
           OR tag LIKE '%' || :keyword || '%'
        ORDER BY id DESC
        """
    )
    fun searchAllByKeyword(keyword: String): Flow<List<Chat>>


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
    @Delete
    suspend fun deleteAiConfig(config: AiChatConfig)
    @Query("SELECT * FROM ai_chat_configs WHERE chatId = :chatId")
    fun findAiConfig(chatId: Long): Flow<List<AiChatConfig>>

    @Query("SELECT * FROM ai_chat_configs WHERE chatId = :chatId")
    suspend fun getAiConfigOnce(chatId: Long): List<AiChatConfig>

    @Query("SELECT * FROM ai_chat_configs")
    fun getAllAiConfigs(): Flow<List<AiChatConfig>>

    @Query("SELECT * FROM ai_chat_configs WHERE id = :aiId LIMIT 1")
    suspend fun getAiConfigById(aiId: Long): AiChatConfig?

    @Query("SELECT * FROM ai_chat_configs ORDER BY id ASC LIMIT 1")
    suspend fun getFirstAiConfig(): AiChatConfig?

    //配置相关 UserChatConfig
    @Insert
    suspend fun insertUserConfig(config: UserChatConfig)

    @Update
    suspend fun updateUserConfig(config: UserChatConfig)

    @Query("SELECT * FROM user_chat_configs WHERE chatId = :chatId")
    fun findUserConfig(chatId: Long): Flow<UserChatConfig>

    @Query("SELECT * FROM user_chat_configs WHERE chatId = :chatId LIMIT 1")
    suspend fun getUserConfigOnce(chatId: Long): UserChatConfig?
    
    // ========== 群聊成员相关 GroupChatMember ==========
    @Insert
    suspend fun insertGroupChatMember(member: GroupChatMember)
    
    @Update
    suspend fun updateGroupChatMember(member: GroupChatMember)
    
    @Delete
    suspend fun deleteGroupChatMember(member: GroupChatMember)
    
    @Query("SELECT * FROM group_chat_members WHERE groupChatId = :groupChatId ORDER BY replyOrder ASC")
    fun getGroupChatMembers(groupChatId: Long): Flow<List<GroupChatMember>>
    
    @Query("SELECT * FROM group_chat_members WHERE groupChatId = :groupChatId ORDER BY replyOrder ASC")
    suspend fun getGroupChatMembersOnce(groupChatId: Long): List<GroupChatMember>
    
    @Query("DELETE FROM group_chat_members WHERE groupChatId = :groupChatId AND sourceAiId = :sourceAiId")
    suspend fun removeGroupChatMember(groupChatId: Long, sourceAiId: Long)
    
    @Query("SELECT * FROM group_chat_members WHERE groupChatId = :groupChatId AND sourceAiId = :sourceAiId LIMIT 1")
    suspend fun getGroupChatMemberBySourceAi(groupChatId: Long, sourceAiId: Long): GroupChatMember?
}
