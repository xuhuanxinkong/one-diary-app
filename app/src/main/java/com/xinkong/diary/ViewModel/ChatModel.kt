package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.Http.AiHttp
import com.xinkong.diary.Data.AiResponse
import com.xinkong.diary.Data.AiState
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
        val messages: List<Map<String, String>>
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
                messages = messages,
                allowToolRequest = true
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
        val messages = listOf(mapOf("role" to "user", "content" to "你好"))
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

            val continuedMessages = pending.messages + mapOf(
                "role" to "system",
                "content" to buildToolResultPrompt(pending.keyword, toolResult)
            )

            _aiState.value = AiState.Loading
            val result = aiHttp.chatWithAi(aiConfig, continuedMessages)
            handleAiResponse(
                chatId = pending.chatId,
                result = result,
                messages = continuedMessages,
                allowToolRequest = false
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
    private suspend fun buildMessages(chatId: Long, currentContent: String): List<Map<String, String>> {
        val messages = mutableListOf<Map<String, String>>()

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
    当你需要读取本地笔记才能回答用户问题时，你必须输出以下格式的JSON（只能输出这一行，不要有其他文字）：
    {"tool":"read_notes","keyword":"关键词","limit":3}
    
    示例1：
    用户问：“我昨天写了什么？”
    你应该输出：{"tool":"read_notes","keyword":"昨天","limit":3}
    
    示例2：
    用户问：“关于旅行的笔记有哪些？”
    你应该输出：{"tool":"read_notes","keyword":"旅行","limit":3}
    
    重要规则：
    1. keyword必须是中文或英文单词，不能包含标点
    2. 如果不需要调用工具，直接正常对话
    3. 如果需要调用工具，必须只输出JSON，不能有任何其他文字
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
        messages: List<Map<String, String>>,
        allowToolRequest: Boolean
    ) {
        result.fold(
            onSuccess = { response ->
                when (response) {
                    is AiResponse.Text -> {
                        val request = if (allowToolRequest) parseReadNotesToolRequest(response.content) else null
                        if (request != null) {
                            _pendingDiaryRead.value = PendingDiaryReadAction(
                                chatId = chatId,
                                keyword = request.keyword,
                                limit = request.limit,
                                messages = messages
                            )
                            _aiState.value = AiState.Idle
                            return@fold
                        }

                        val aiMsg = ChatMessage(
                            chatId = chatId,
                            role = "assistant",
                            content = response.content,
                            date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
                        )
                        chatDao.insertMessage(aiMsg)
                        _aiState.value = AiState.Success(result = response.content)
                    }
                    // 未来扩展：处理技能调用
                    // is AiResponse.SkillAction -> { ... }
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
        val limit: Int
    )

    private fun parseReadNotesToolRequest(content: String): ReadNotesToolRequest? {
        val raw = content.trim()
        if (!raw.startsWith("{") || !raw.endsWith("}")) return null
        return try {
            val json = JSONObject(raw)
            if (json.optString("tool") != "read_notes") return null
            val keyword = json.optString("keyword").trim()
            if (keyword.isEmpty() || !keyword.matches(Regex("^[\\w\\u4e00-\\u9fa5]+$"))) return null
            val limit = json.optInt("limit", 3).coerceIn(1, 5)
            ReadNotesToolRequest(keyword = keyword, limit = limit)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatDiaryToolResult(diaries: List<com.xinkong.diary.repository.Diary>): String {
        if (diaries.isEmpty()) {
            return "未找到匹配笔记。"
        }
        return diaries.joinToString("\n\n") { diary ->
            "[id=${diary.id}] ${diary.title}\n${diary.text.ifEmpty { diary.content }}"
        }
    }

    private fun buildToolResultPrompt(keyword: String, toolResult: String): String {
        return "你请求了读取本地笔记，关键词为：$keyword。以下是查询结果，请基于结果继续回答用户问题：\n$toolResult"
    }
}

