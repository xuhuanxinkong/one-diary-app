package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.Http.AiHttp
import com.xinkong.diary.Data.AiResponse
import com.xinkong.diary.Data.AiState
import com.xinkong.diary.Data.AiToolCall
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.repository.UserChatConfig
import com.xinkong.diary.repository.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatDao()
    private val diaryDao = db.diaryDao()
    private val aiHttp = AiHttp()

    // ---- 对话列表状态 ----
    private val _chatListState = MutableStateFlow(listOf<Chat>())
    val chatListState: StateFlow<List<Chat>> = _chatListState.asStateFlow()

    // ---- AI 状态 ----
    private val _aiState = MutableStateFlow<AiState>(AiState.Idle)
    val aiState: StateFlow<AiState> = _aiState.asStateFlow()

    data class PendingDiaryReadAction(
        val chatId: Long,
        val keyword: String,
        val limit: Int,
        val toolCallId: String,
        val messages: List<Map<String, Any>>
    )

    private val _pendingDiaryRead = MutableStateFlow<PendingDiaryReadAction?>(null)
    val pendingDiaryRead: StateFlow<PendingDiaryReadAction?> = _pendingDiaryRead.asStateFlow()

    init {
        viewModelScope.launch {
            chatDao.getAllChat().collect { chats ->
                _chatListState.update { chats }
            }
        }
    }

    // ========== 对话 CRUD ==========

    fun addChat(title: String, tag: String?): Chat {
        val chat = Chat(
            title = title,
            date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
            tag = tag ?: "未分类"
        )
        viewModelScope.launch {
            val chatId = chatDao.insertChat(chat)
            chatDao.insertAiConfig(AiChatConfig(chatId = chatId))
            chatDao.insertUserConfig(UserChatConfig(chatId = chatId))
        }
        return chat
    }

    fun deleteChat(chat: Chat) {
        viewModelScope.launch { chatDao.deleteChat(chat) }
    }

    fun updateChat(chat: Chat) {
        viewModelScope.launch { chatDao.updateChat(chat) }
    }

    fun findChat(chatId: Long): Flow<Chat> = chatDao.findChatById(chatId)

    // ========== 消息 ==========

    fun getMessages(chatId: Long): Flow<List<ChatMessage>> = chatDao.getMessages(chatId)

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch { chatDao.deleteMessage(message) }
    }

    /**
     * 发送消息并调用 AI
     * 自动从 DB 读取该对话的 AI 配置、历史消息和上下文
     */
    fun sendMessage(chatId: Long, content: String) {
        viewModelScope.launch {
            // 1. 保存用户消息
            val userMsg = ChatMessage(
                chatId = chatId,
                role = "user",
                content = content,
                date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
            )
            chatDao.insertMessage(userMsg)

            // 2. 获取该对话的 AI 配置
            val aiConfig = chatDao.getAiConfigOnce(chatId) ?: AiChatConfig(chatId = chatId)

            // 3. 构建完整消息列表（system + history + 当前消息）
            val messages = buildMessages(chatId, content)

            // 4. 调用 AI
            _aiState.value = AiState.Loading
            val result = aiHttp.chatWithAi(aiConfig, messages)

            // 5. 处理响应
            handleAiResponse(
                chatId = chatId,
                result = result,
                messages = messages
            )
        }
    }

    // ========== 配置 ==========

    fun findAiConfig(chatId: Long): Flow<AiChatConfig> = chatDao.findAiConfig(chatId)

    fun updateAiConfig(config: AiChatConfig) {
        viewModelScope.launch { chatDao.updateAiConfig(config) }
    }

    fun findUserConfig(chatId: Long): Flow<UserChatConfig> = chatDao.findUserConfig(chatId)

    fun updateUserConfig(config: UserChatConfig) {
        viewModelScope.launch { chatDao.updateUserConfig(config) }
    }

    // ========== AI 工具方法 ==========

    /**
     * 测试 AI 连接（供 SettingScreen 使用）
     */
    suspend fun testConnection(config: AiChatConfig): Result<AiResponse> {
        val messages = listOf(mapOf<String, Any>("role" to "user", "content" to "你好"))
        return aiHttp.chatWithAi(config, messages)
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> {
        return aiHttp.getModels(baseUrl, apiKey)
    }

    fun confirmPendingDiaryRead() {
        viewModelScope.launch {
            val pending = _pendingDiaryRead.value ?: return@launch
            _pendingDiaryRead.value = null

            val aiConfig = chatDao.getAiConfigOnce(pending.chatId) ?: AiChatConfig(chatId = pending.chatId)
            val diaries = diaryDao.searchByKeyword(pending.keyword, pending.limit)
            val toolResult = formatDiaryToolResult(diaries)

            val continuedMessages = pending.messages + buildToolResultMessage(
                toolCallId = pending.toolCallId,
                keyword = pending.keyword,
                toolResult = toolResult
            )

            _aiState.value = AiState.Loading
            val result = aiHttp.chatWithAi(aiConfig, continuedMessages)
            handleAiResponse(
                chatId = pending.chatId,
                result = result,
                messages = continuedMessages
            )
        }
    }

    fun cancelPendingDiaryRead() {
        viewModelScope.launch {
            val pending = _pendingDiaryRead.value ?: return@launch
            _pendingDiaryRead.value = null
            val aiMsg = ChatMessage(
                chatId = pending.chatId,
                role = "assistant",
                content = "已取消读取笔记，我会基于当前对话继续回答。",
                date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
            )
            chatDao.insertMessage(aiMsg)
            _aiState.value = AiState.Idle
        }
    }

    fun resetAiState() {
        _aiState.value = AiState.Idle
    }

    // ========== 私有：消息构建 ==========

    /**
     * 构建发送给 AI 的完整消息列表：
     * [system(上下文)] + [历史消息] + [当前用户消息]
     */
    private suspend fun buildMessages(chatId: Long, currentContent: String): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()

        // system 消息：上下文资料
        val systemContent = buildSystemMessage(chatId)
        if (systemContent.isNotEmpty()) {
            messages.add(mapOf("role" to "system", "content" to systemContent))
        }

        // 历史消息（不含刚插入的当前消息）
        val history = chatDao.getMessagesOnce(chatId).dropLast(1)
        history.forEach { messages.add(mapOf("role" to it.role, "content" to it.content)) }

        // 当前用户消息
        messages.add(mapOf("role" to "user", "content" to currentContent))

        return messages
    }

    /**
     * 构建 system 消息：用户资料 + AI 资料
     */
    private suspend fun buildSystemMessage(chatId: Long): String {
        val userContext = buildContextFromConfig(
            chatDao.getUserConfigOnce(chatId)?.referencedDiaryId
        )
        val aiContext = buildContextFromConfig(
            chatDao.getAiConfigOnce(chatId)?.referencedDiaryId
        )
        return buildString {
            if (userContext.isNotEmpty()) append("【用户资料】\n$userContext\n\n")
            if (aiContext.isNotEmpty()) append("【AI资料】\n$aiContext\n\n")
            append(
                """
    【工具协议】
    你可使用函数工具 read_notes。参数格式：
    - keyword: string（中英文关键词，不含标点）
    - limit: integer（1 到 5）
    
    当需要查询笔记时，请返回标准 tool_calls（由系统注入），并可同时给出简短说明。
    若不需要工具，直接给出正常回答。
    """.trimIndent()
            )
        }.trim()
    }

    private suspend fun buildContextFromConfig(referencedDiaryId: String?): String {
        if (referencedDiaryId.isNullOrEmpty()) return ""
        val ids = try {
            Json.decodeFromString<List<Long>>(referencedDiaryId)
        } catch (e: Exception) {
            emptyList()
        }
        if (ids.isEmpty()) return ""
        val diaries = diaryDao.getDiariesByIds(ids)
        return diaries.joinToString("\n") { "${it.title}: ${it.text}" }
    }

    // ========== 私有：响应处理 ==========

    /**
     * 处理 AI 响应（未来可扩展技能调用解析）
     */
    private suspend fun handleAiResponse(
        chatId: Long,
        result: Result<AiResponse>,
        messages: List<Map<String, Any>>
    ) {
        result.fold(
            onSuccess = { response ->
                when (response) {
                    is AiResponse.Message -> {
                        val request = parseReadNotesToolRequest(response.toolCalls)
                        if (request != null) {
                            val assistantToolCallMessage = buildAssistantToolCallMessage(
                                content = response.content,
                                toolCall = request.toolCall
                            )

                            if (response.content.isNotBlank()) {
                                val aiMsg = ChatMessage(
                                    chatId = chatId,
                                    role = "assistant",
                                    content = response.content,
                                    date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
                                )
                                chatDao.insertMessage(aiMsg)
                            }

                            _pendingDiaryRead.value = PendingDiaryReadAction(
                                chatId = chatId,
                                keyword = request.keyword,
                                limit = request.limit,
                                toolCallId = request.toolCall.id,
                                messages = messages + assistantToolCallMessage
                            )
                            _aiState.value = AiState.Idle
                            return@fold
                        }

                        val displayContent = response.content.ifBlank { "(空回复)" }
                        val aiMsg = ChatMessage(
                            chatId = chatId,
                            role = "assistant",
                            content = displayContent,
                            date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
                        )
                        chatDao.insertMessage(aiMsg)
                        _aiState.value = AiState.Success(result = displayContent)
                    }
                }
            },
            onFailure = { error ->
                val errMsg = ChatMessage(
                    chatId = chatId,
                    role = "assistant",
                    content = error.message ?: "AI回复失败",
                    date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
                )
                chatDao.insertMessage(errMsg)
                _aiState.value = AiState.Error(message = error.message ?: "AI回复失败")
            }
        )
    }

    private data class ReadNotesToolRequest(
        val keyword: String,
        val limit: Int,
        val toolCall: AiToolCall
    )

    private fun parseReadNotesToolRequest(toolCalls: List<AiToolCall>): ReadNotesToolRequest? {
        val call = toolCalls.firstOrNull { it.type == "function" && it.functionName == "read_notes" }
            ?: return null
        return try {
            val json = JSONObject(call.arguments)
            val keyword = json.optString("keyword").trim()
            if (keyword.isEmpty() || !keyword.matches(Regex("^[\\w\\u4e00-\\u9fa5]+$"))) return null
            val limit = json.optInt("limit", 3).coerceIn(1, 5)
            ReadNotesToolRequest(keyword = keyword, limit = limit, toolCall = call)
        } catch (e: Exception) {
            null
        }
    }

    private fun buildAssistantToolCallMessage(content: String, toolCall: AiToolCall): Map<String, Any> {
        return mapOf(
            "role" to "assistant",
            "content" to content,
            "tool_calls" to listOf(
                mapOf(
                    "id" to toolCall.id,
                    "type" to toolCall.type,
                    "function" to mapOf(
                        "name" to toolCall.functionName,
                        "arguments" to toolCall.arguments
                    )
                )
            )
        )
    }

    private fun formatDiaryToolResult(diaries: List<com.xinkong.diary.repository.Diary>): String {
        if (diaries.isEmpty()) {
            return "未找到匹配笔记。"
        }
        return diaries.joinToString("\n\n") { diary ->
            "[id=${diary.id}] ${diary.title}\n${diary.text.ifEmpty { diary.content }}"
        }
    }

    private fun buildToolResultMessage(toolCallId: String, keyword: String, toolResult: String): Map<String, Any> {
        return mapOf(
            "role" to "tool",
            "tool_call_id" to toolCallId,
            "name" to "read_notes",
            "content" to "关键词：$keyword\n$toolResult"
        )
    }
}

