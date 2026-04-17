package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.Http.AiHttp
import com.xinkong.diary.data.AiResponse
import com.xinkong.diary.data.AiState
import com.xinkong.diary.data.AiToolCall
import com.xinkong.diary.data.AlarmEntity
import com.xinkong.diary.data.ToolTask
import com.xinkong.diary.receiver.ChainAlarmHelper
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.repository.UserChatConfig
import com.xinkong.diary.repository.ChatMessage
import com.xinkong.diary.repository.GroupChatMember
import com.xinkong.diary.repository.TagFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import android.util.Log
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    fun clearUnreadCount(chatId: Long) {
        viewModelScope.launch {
            // 1. 直接通过非 Flow 的同步方法获取当前对象（或者在 Dao 里写一个 suspend 函数）
            val chat = chatDao.getChatByIdSuspend(chatId)

            // 2. 判断并更新
            if (chat != null && chat.unreadCount != 0) {
                chatDao.updateChat(chat.copy(unreadCount = 0))
            }
        }
    }
    companion object {
        private const val TAG = "ChatViewModel"
        private const val PREFS_NAME = "chat_settings"
        private const val KEY_HISTORY_ROUNDS = "history_rounds"
        private const val DEFAULT_HISTORY_ROUNDS = 12
    }

    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatDao()
    private val diaryDao = db.diaryDao()
    private val alarmDao = db.alarmDao()

    private val tagDao = db.tagDao()
    
    private val aiHttp = AiHttp()
    private val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    
    // 获取历史消息轮数设置
    fun getHistoryRounds(): Int = prefs.getInt(KEY_HISTORY_ROUNDS, DEFAULT_HISTORY_ROUNDS)
    
    fun setHistoryRounds(rounds: Int) {
        prefs.edit().putInt(KEY_HISTORY_ROUNDS, rounds.coerceIn(1, 50)).apply()
    }

    // ---- 对话列表状态 ----
    val chatListState: StateFlow<List<Chat>> = chatDao.getAllChat()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 派生 AI 列表：避免按会话逐个查询，直接基于全量配置和会话列表映射
    val allAiConfigsState: StateFlow<List<AiChatConfig>> = chatDao.getAllAiConfigs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 派生每个对话的首个 AI（供首页等轻量场景使用）
    val AiListState: StateFlow<List<AiChatConfig>> = combine(
        chatListState,
        allAiConfigsState
    ) { chats, allConfigs ->
        val chatIds = chats.map { it.id }.toSet()
        allConfigs
            .asSequence()
            .filter { it.chatId in chatIds }
            .groupBy { it.chatId }
            .mapNotNull { (_, configs) -> configs.firstOrNull() }
            .distinctBy { it.id }
            .toList()
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 群聊成员数量映射（groupChatId -> 成员数量）
    val groupMemberCountState: StateFlow<Map<Long, Int>> = chatDao.getAllGroupChatMembers()
        .map { members ->
            members.groupBy { it.groupChatId }.mapValues { it.value.size }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // ---- AI 状态 ----
    private val _aiState = MutableStateFlow<AiState>(AiState.Idle)
    val aiState: StateFlow<AiState> = _aiState

    private val _currentTypingAi = MutableStateFlow<AiChatConfig?>(null)
    val currentTypingAi: StateFlow<AiChatConfig?> = _currentTypingAi.asStateFlow()

    data class PendingToolUIState(
        val title: String,
        val description: String,
        val showDontAskAgain: Boolean
    )

    private val _pendingToolUI = MutableStateFlow<PendingToolUIState?>(null)
    val pendingToolUI: StateFlow<PendingToolUIState?> = _pendingToolUI.asStateFlow()

    private var autoConfirmTools = false

    var aiCurrentFolder: String = "我的笔记"

    private val userRole = "user"
    private val assistantRole = "assistant"



    private data class ToolBatchContext(
        val chatId: Long,
        val aiConfig: AiChatConfig,
        val enabledTools: Set<String>,
        val baseMessages: List<Map<String, Any>>,
        val assistantMessage: Map<String, Any>,
        val allTasks: List<ToolTask>,
        val completedResults: List<Map<String, Any>>,
        val executedTools: List<String> = emptyList()
    )

    private var currentBatch: ToolBatchContext? = null


    // ========== 对话 CRUD ==========

    fun addChat(title: String, tag: String, tagFolder: String = "我的笔记"): Chat {
        val chat = Chat(
            title = title,
            date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
            tag = tag,
            tagFolder = tagFolder
        )
        viewModelScope.launch {
            val chatId = chatDao.insertChat(chat)
            chatDao.insertAiConfig(AiChatConfig(chatId = chatId))
            chatDao.insertUserConfig(UserChatConfig(chatId = chatId))
        }
        return chat
    }

    fun deleteChat(chat: Chat) {
        viewModelScope.launch {
            try {
                // 获取该Chat下所有AI配置，删除它们绑定的文件夹
                val aiConfigs = chatDao.getAiConfigOnce(chat.id)
                for (config in aiConfigs) {
                    if (config.boundFolder.isNotBlank()) {
                        // 查找并删除AI绑定的文件夹
                        val folders = tagDao.getAllTagFolders().first()
                        val folderToDelete = folders.find { 
                            it.name == config.boundFolder && it.isAiBound 
                        }
                        if (folderToDelete != null) {
                            // 同步解绑而不是删除文件夹
                            tagDao.insertTagFolder(folderToDelete.copy(isAiBound = false))
                        }
                    }
                }
                // 删除Chat（级联删除会自动删除关联的AI配置和消息）
                chatDao.deleteChat(chat)
            } catch (e: Exception) {
                e.printStackTrace()
                // 即使文件夹删除失败，也尝试删除Chat
                chatDao.deleteChat(chat)
            }
        }
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
    fun sendMessage(chatId: Long, content: String, selectedAIs: List<AiChatConfig>, imageBase64: String? = null, imageUriString: String? = null) {
        if (selectedAIs.isEmpty()) return
        viewModelScope.launch {
            autoConfirmTools = false
            saveReplySelection(chatId, selectedAIs)
            
            // 1. 保存用户消息
            val photoUris = if (imageUriString != null) Json.encodeToString(listOf(imageUriString)) else "[]"
            val userMsg = ChatMessage(
                chatId = chatId,
                role = "user",
                content = content,
                date = SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(Date()),
                photoUris = photoUris
            )
            chatDao.insertMessage(userMsg)

            // 2. 轮流调用 AI
            for (config in selectedAIs) {
                _aiState.value = AiState.Loading
                _currentTypingAi.value = config

                val enabledTools = mutableSetOf<String>().apply {
                    add("query_chat_history")
                    if (config.enableWebSearch) add("web_search_baidu")
                    if (config.enableImageSupport) add("image_recognition")
                    // 每个 AI 都有笔记工具权限（已隔离文件夹）
                    if (config.enableReadNotes) add("read_notes")
                    if (config.enableWriteNote) add("write_note")
                    if (config.enableEditNote) add("edit_note")
                    if (config.enableSetAlarm) {
                        add("create_plan")
                        add("cancel_plan")
                    }
                }

                // 构加上当前最新上下文，每个 AI 都能看见前面 AI 发送的消息
                val messages = buildContextMessages(chatId, config, enabledTools).toMutableList()
                
                // 若携带图片，替换最近一条用户消息为多模态 content（text + image_url）
                if (imageBase64 != null && config.enableImageSupport && messages.isNotEmpty()) {
                    val userMsgIndex = messages.indexOfLast { it["role"] == userRole }
                    if (userMsgIndex >= 0) {
                        val current = messages[userMsgIndex].toMutableMap()
                        val textContent = (current["content"] as? String)?.ifBlank { "请识别这张图片。" }
                            ?: "请识别这张图片。"
                        current["content"] = listOf(
                            mapOf("type" to "text", "text" to textContent),
                            mapOf("type" to "image_url", "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64"))
                        )
                        messages[userMsgIndex] = current
                    }
                }

                val result = requestAiResponse(config, messages, enabledTools)
                
                // 处理并保证入库完成，再走下一个循环
                handleAiResponse(chatId, result, messages, enabledTools, config = config)
                _currentTypingAi.value = null
            }
            _aiState.value = AiState.Idle
        }
    }

    /**
     * 后台定时任务版发送：单 AI、无图片、保留工具调用能力
     */
    suspend fun sendAlarmTaskMessage(
        chatId: Long,
        aiConfig: AiChatConfig,
        taskPrompt: String,
        referencedDiaryIdOverride: String? = null
    ): Result<String> {
        val enabledTools = mutableSetOf<String>().apply {
            add("query_chat_history")
            if (aiConfig.enableWebSearch) add("web_search_baidu")
            // 每个 AI 都有笔记工具权限（已隔离文件夹）
            if (aiConfig.enableReadNotes) add("read_notes")
            if (aiConfig.enableWriteNote) add("write_note")
            if (aiConfig.enableEditNote) add("edit_note")
            if (aiConfig.enableSetAlarm) {
                add("create_plan")
                add("cancel_plan")
            }
        }

        val chat = chatDao.getChatByIdSuspend(chatId) ?: return Result.failure(Exception("会话不存在"))
        val messages = buildAlarmContextMessages(
            chatId = chatId,
            currentAiConfig = aiConfig,
            enabledTools = enabledTools,
            taskPrompt = taskPrompt,
            referencedDiaryIdOverride = referencedDiaryIdOverride
        ).toMutableList()
        val executedTools = mutableListOf<String>()

        repeat(12) {
            val result = requestAiResponse(aiConfig, messages, enabledTools)
            if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: Exception("AI回复失败"))
            val response = result.getOrNull() as? AiResponse.Message ?: return Result.failure(Exception("AI回复格式异常"))

            val tasks = parseToolTasks(response.toolCalls, enabledTools, aiConfig.boundFolder)
            if (tasks.isEmpty()) {
                var displayContent = response.content.ifBlank { "(空回复)" }
                val aiNameTemp = aiConfig.name
                val prefixRegex = Regex("^(?:(?:(?i)${Regex.escape(aiNameTemp)})|(?i)ai)[:：]\\s*")
                while (prefixRegex.containsMatchIn(displayContent)) {
                    displayContent = displayContent.replaceFirst(prefixRegex, "").trimStart()
                }
                // 移除时间前缀 [时间: yyyy-MM-dd HH:mm]
                val timePrefixRegex = Regex("^\\[时间:\\s*\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}\\]\\s*")
                while (timePrefixRegex.containsMatchIn(displayContent)) {
                    displayContent = displayContent.replaceFirst(timePrefixRegex, "").trimStart()
                }
                val aiMsg = ChatMessage(
                    chatId = chatId,
                    role = "assistant",
                    content = displayContent,
                    date = SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(Date()),
                    toolExecutions = Json.encodeToString(executedTools),
                    aiId = aiConfig.id
                )
                chatDao.insertMessage(aiMsg)
                return Result.success(displayContent)
            }

            messages.add(buildAssistantToolCallMessage(response.content, tasks.map { it.toolCall }))
            for (task in tasks) {
                val toolResult = executeBackgroundToolTask(task, chatId, aiConfig.id, chat.tagFolder)
                executedTools.add(task.title)
                messages.add(toolResult)
            }
        }

        return Result.failure(Exception("工具调用轮次过多，已中止"))
    }

    // ========== 配置 ==========

    fun findAiConfig(chatId: Long): Flow<List<AiChatConfig>> = chatDao.findAiConfig(chatId)
    
    suspend fun findAiConfigOnce(chatId: Long): List<AiChatConfig> = chatDao.getAiConfigOnce(chatId)
    
    suspend fun getAiConfigById(aiId: Long): AiChatConfig? = chatDao.getAiConfigById(aiId)

    fun addAiConfig(chatId: Long) {
        viewModelScope.launch { chatDao.insertAiConfig(AiChatConfig(chatId = chatId)) }
    }

    fun updateAiConfig(config: AiChatConfig) {
        viewModelScope.launch { chatDao.updateAiConfig(config) }
    }

    fun deleteAiConfig(config: AiChatConfig) {
        viewModelScope.launch { chatDao.deleteAiConfig(config) }
    }
    
    /**
     * 从群聊中移除AI（不删除AI本身，只从当前聊天移除）
     */
    fun removeAiFromChat(config: AiChatConfig) {
        viewModelScope.launch { chatDao.deleteAiConfig(config) }
    }
    
    /**
     * 删除AI并解绑其绑定的文件夹（不自动删除文件夹）
     */
    fun deleteAiConfigWithFolder(config: AiChatConfig) {
        viewModelScope.launch {
            // 先删除AI配置，再判断该文件夹是否还有AI占用，避免状态判断误差
            chatDao.deleteAiConfig(config)

            if (config.boundFolder.isBlank()) return@launch

            val stillBound = chatDao.getAllAiConfigs().first().any {
                it.boundFolder == config.boundFolder
            }
            if (stillBound) return@launch

            val folders = tagDao.getAllTagFolders().first()
            folders
                .filter { it.name == config.boundFolder && it.isAiBound }
                .forEach { folder ->
                    tagDao.insertTagFolder(folder.copy(isAiBound = false))
                }
        }
    }

    fun findUserConfig(chatId: Long): Flow<UserChatConfig> = chatDao.findUserConfig(chatId)

    fun updateUserConfig(config: UserChatConfig) {
        viewModelScope.launch { chatDao.updateUserConfig(config) }
    }
    
    fun searchChat(keyword: String): Flow<List<Chat>> = chatDao.searchAllByKeyword(keyword)

    // ========== 创建AI对话 ==========

    /**
     * 创建新的AI对话（单聊）：直接创建一个新Chat并关联一个新的AI，同时创建绑定的文件夹
     */
    fun createAiChatWithNewAi(aiName: String = "AI助手", folderName: String) {
        viewModelScope.launch {
            // 1. 先创建AI绑定的文件夹（type为"Diary"，用于日记笔记）
            val folder = TagFolder(
                name = folderName,
                type = "Diary",
                isHidden = false,
                isAiBound = true
            )
            tagDao.insertTagFolder(folder)
            
            // 2. 创建Chat
            val chat = Chat(
                title = aiName,
                date = SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(Date()),
                tag = "未分类",
                tagFolder = aiCurrentFolder
            )
            val chatId = chatDao.insertChat(chat)
            
            // 3. 创建AI配置，绑定文件夹
            chatDao.insertAiConfig(AiChatConfig(
                chatId = chatId, 
                name = aiName,
                boundFolder = folderName
            ))
            chatDao.insertUserConfig(UserChatConfig(chatId = chatId))
        }
    }

    /**
     * 创建群聊：选择多个已有的AI配置，通过引用模式关联到新群聊（不复制配置）
     */
    fun createGroupChat(selectedAis: List<AiChatConfig>) {
        if (selectedAis.size < 2) return
        viewModelScope.launch {
            val groupName = selectedAis.take(3).joinToString("、") { it.name }
            val chat = Chat(
                title = "$groupName 群聊",
                date = SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(Date()),
                tag = "未分类",
                tagFolder = aiCurrentFolder,
                isGroupChat = true  // 标记为群聊
            )
            val chatId = chatDao.insertChat(chat)
            
            // 只记录成员关系（引用模式），不复制AI配置
            selectedAis.forEachIndexed { index, aiConfig ->
                chatDao.insertGroupChatMember(
                    GroupChatMember(
                        groupChatId = chatId, 
                        sourceAiId = aiConfig.id,
                        replyOrder = index,
                        isEnabled = true
                    )
                )
            }
            chatDao.insertUserConfig(UserChatConfig(chatId = chatId))
        }
    }
    
    /**
     * 从AI列表中添加AI到群聊（引用模式）
     */
    fun addAiToGroupChat(groupChatId: Long, sourceAiConfig: AiChatConfig) {
        viewModelScope.launch {
            // 获取当前群聊中成员数量作为replyOrder
            val existingMembers = chatDao.getGroupChatMembersOnce(groupChatId)
            val newOrder = existingMembers.size
            
            // 只记录成员关系，不复制AI配置
            chatDao.insertGroupChatMember(
                GroupChatMember(
                    groupChatId = groupChatId, 
                    sourceAiId = sourceAiConfig.id,
                    replyOrder = newOrder,
                    isEnabled = true
                )
            )
        }
    }
    
    /**
     * 获取所有可用的AI配置（来自AI列表，即单聊的第一个AI）
     */
    fun getAllAvailableAis(): Flow<List<AiChatConfig>> {
        return chatDao.getAllChat().map { chats ->
            chats.filter { !it.isGroupChat }.mapNotNull { chat ->
                chatDao.getAiConfigOnce(chat.id).firstOrNull()
            }
        }
    }
    
    // ========== 群聊成员管理（引用模式） ==========
    
    /**
     * 获取群聊成员列表（包含 replyOrder、isEnabled 等群聊特有设置）
     */
    fun getGroupChatMembers(groupChatId: Long): Flow<List<GroupChatMember>> = 
        chatDao.getGroupChatMembers(groupChatId)
    
    /**
     * 获取群聊中引用的源AI配置列表（按回复顺序排列）
     * 通过成员关系表查询对应的源AI配置
     */
    fun getGroupChatSourceAiConfigs(groupChatId: Long): Flow<List<AiChatConfig>> {
        return chatDao.getGroupChatMembers(groupChatId).map { members ->
            members.filter { it.isEnabled }.mapNotNull { member ->
                chatDao.getAiConfigById(member.sourceAiId)
            }
        }
    }
    
    /**
     * 获取群聊中引用的源AI配置列表（一次性获取）
     */
    suspend fun getGroupChatSourceAiConfigsOnce(groupChatId: Long): List<AiChatConfig> {
        val members = chatDao.getGroupChatMembersOnce(groupChatId)
        return members.filter { it.isEnabled }.mapNotNull { member ->
            chatDao.getAiConfigById(member.sourceAiId)
        }
    }
    
    /**
     * 获取群聊成员对应的源AI所在的Chat（用于导航到源AI设置界面）
     */
    suspend fun getSourceChatForAi(sourceAiId: Long): Chat? {
        val config = chatDao.getAiConfigById(sourceAiId) ?: return null
        return chatDao.getChatByIdSuspend(config.chatId)
    }
    
    /**
     * 更新群聊成员设置（回复顺序、启用状态等）
     */
    fun updateGroupChatMember(member: GroupChatMember) {
        viewModelScope.launch { chatDao.updateGroupChatMember(member) }
    }
    
    /**
     * 从群聊中移除成员（引用模式）
     */
    fun removeAiFromGroupChat(groupChatId: Long, sourceAiId: Long) {
        viewModelScope.launch { chatDao.removeGroupChatMember(groupChatId, sourceAiId) }
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

    private fun buildStreamingDisplayContent(fullContent: String, config: AiChatConfig): String {
        val aiNameTemp = config.name
        val prefixRegex = Regex("^(?:(?:(?i)${Regex.escape(aiNameTemp)})|(?i)ai)[:：]\\s*")
        var displayContent = fullContent
        while (prefixRegex.containsMatchIn(displayContent)) {
            displayContent = displayContent.replaceFirst(prefixRegex, "")
        }
        // 移除时间前缀 [时间: yyyy-MM-dd HH:mm]
        val timePrefixRegex = Regex("^\\[时间:\\s*\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}\\]\\s*")
        while (timePrefixRegex.containsMatchIn(displayContent)) {
            displayContent = displayContent.replaceFirst(timePrefixRegex, "").trimStart()
        }
        return displayContent
    }

    private suspend fun requestAiResponse(
        config: AiChatConfig,
        messages: List<Map<String, Any>>,
        enabledTools: Set<String>
    ): Result<AiResponse> {
        if (config.baseUrl.isBlank()) return Result.failure(Exception("AI请求地址未配置或无效"))
        if (!config.enableStream) {
            Log.d(TAG, "requestAiResponse: stream disabled, use non-stream directly")
            return aiHttp.chatWithAi(config, messages, enabledTools)
        }

        var fullContent = ""
        var fullReasoning = ""
        var capturedToolCalls = listOf<AiToolCall>()
        var chunkCount = 0

        return try {
            Log.d(TAG, "requestAiResponse: start stream request, enabledTools=$enabledTools")
            aiHttp.chatWithAiStream(config, messages, enabledTools).collect { chunk ->
                chunkCount++
                when (chunk) {
                    is AiResponse.StreamChunk.ReasoningContent -> {
                        if (chunk.text.isNotEmpty()) {
                            fullReasoning += chunk.text
                            _aiState.value = AiState.Streaming(
                                partialContent = buildStreamingDisplayContent(fullContent, config),
                                partialReasoning = fullReasoning
                            )
                        }
                    }
                    is AiResponse.StreamChunk.Content -> {
                        if (chunk.text.isNotEmpty()) {
                            fullContent += chunk.text
                            _aiState.value = AiState.Streaming(
                                partialContent = buildStreamingDisplayContent(fullContent, config),
                                partialReasoning = if (fullReasoning.isNotEmpty()) fullReasoning else null
                            )
                        }
                    }
                    is AiResponse.StreamChunk.ToolCalls -> {
                        capturedToolCalls = chunk.toolCalls
                    }
                    AiResponse.StreamChunk.End -> {}
                }
            }
            Log.d(
                TAG,
                "requestAiResponse: stream finished, chunks=$chunkCount, contentLen=${fullContent.length}, reasoningLen=${fullReasoning.length}, toolCalls=${capturedToolCalls.size}"
            )
            if (fullContent.isBlank() && fullReasoning.isBlank() && capturedToolCalls.isEmpty()) {
                // Some providers ignore stream mode and return non-SSE payload; fallback in that case.
                Log.w(TAG, "requestAiResponse: stream yielded no content/toolCalls, fallback to non-stream")
                aiHttp.chatWithAi(config, messages, enabledTools)
            } else {
                Log.d(TAG, "requestAiResponse: use stream result directly")
                Result.success(AiResponse.Message(
                    content = fullContent, 
                    toolCalls = capturedToolCalls, 
                    reasoningContent = if (fullReasoning.isNotEmpty()) fullReasoning else null
                ))
            }
        } catch (e: Exception) {
            // If we already got stream chunks, prefer partial stream result and avoid duplicate requests.
            if (fullContent.isNotBlank() || fullReasoning.isNotBlank() || capturedToolCalls.isNotEmpty()) {
                Log.w(
                    TAG,
                    "stream ended with exception but has partial result, use partial stream output: ${e.message}",
                    e
                )
                Result.success(AiResponse.Message(
                    content = fullContent, 
                    toolCalls = capturedToolCalls, 
                    reasoningContent = if (fullReasoning.isNotEmpty()) fullReasoning else null
                ))
            } else {
                // Some providers fail on stream mode or tool+stream combinations; fallback improves reliability.
                Log.e(TAG, "stream request failed, fallback to non-stream: ${e.message}", e)
                aiHttp.chatWithAi(config, messages, enabledTools)
            }
        }
    }

    fun confirmPendingToolAction(dontAskAgain: Boolean = false) {
        if (dontAskAgain) autoConfirmTools = true
        _pendingToolUI.value = null
        val batch = currentBatch ?: return
        val currentTask = batch.allTasks[batch.completedResults.size]

        viewModelScope.launch {
            // 使用AI配置的绑定文件夹，如果为空则使用默认文件夹
            val targetFolder = batch.aiConfig.boundFolder.ifBlank { "我的笔记" }
            
            val resultMessage = when (currentTask) {
                is ToolTask.ReadNotes -> {
                    val diaries = diaryDao.searchByKeyword(currentTask.keyword, currentTask.limit)
                        .filter { it.tagFolder == targetFolder }
                    buildToolResultMessage(
                        toolName = currentTask.toolCall.functionName,
                        toolCallId = currentTask.toolCall.id,
                        keyword = currentTask.keyword,
                        toolResult = formatDiaryToolResult(diaries)
                    )
                }
                is ToolTask.WriteNote -> {
                    val newDiary = com.xinkong.diary.repository.Diary(
                        title = currentTask.noteTitle,
                        text = currentTask.content,
                        content = "",
                        date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                        tag = currentTask.tag,
                        tagFolder = targetFolder
                    )
                    diaryDao.insert(newDiary)
                    buildToolResultMessage(
                        toolName = currentTask.toolCall.functionName,
                        toolCallId = currentTask.toolCall.id,
                        keyword = "",
                        toolResult = "笔记新增成功: ${currentTask.noteTitle}"
                    )
                }
                is ToolTask.EditNote -> {
                    val existing = diaryDao.getDiaryById(currentTask.id)
                    val resultText = if (existing != null) {
                        val newText = currentTask.content ?: existing.text
                        val updated = existing.copy(
                            title = currentTask.noteTitle ?: existing.title,
                            text = newText,
                            content = "",
                            tag = currentTask.tag ?: existing.tag
                        )
                        diaryDao.update(updated)
                        "笔记修改成功: ID=${currentTask.id}"
                    } else {
                        "修改失败：找不到 ID=${currentTask.id} 的笔记"
                    }
                    buildToolResultMessage(
                        toolName = currentTask.toolCall.functionName,
                        toolCallId = currentTask.toolCall.id,
                        keyword = "",
                        toolResult = resultText
                    )
                }
                is ToolTask.BatchEditNote -> {
                    val existing = diaryDao.getDiaryById(currentTask.id)
                    val resultText = if (existing != null) {
                        var currentText = existing.text
                        var currentTitle = existing.title
                        val results = mutableListOf<String>()
                        for (op in currentTask.operations) {
                            when (op.op) {
                                "set_title" -> {
                                    op.value?.let {
                                        currentTitle = it
                                        results.add("标题已改为「$it」")
                                    }
                                }
                                "append" -> {
                                    op.value?.let {
                                        currentText = if (currentText.isEmpty()) it else "$currentText\n$it"
                                        results.add("已追加内容")
                                    }
                                }
                                "replace" -> {
                                    val oldText = op.old
                                    val newText = op.new ?: ""
                                    if (oldText != null && currentText.contains(oldText)) {
                                        currentText = currentText.replaceFirst(oldText, newText)
                                        results.add("已替换「${oldText.take(15)}...」")
                                    } else {
                                        results.add("未找到「${oldText?.take(15)}...」")
                                    }
                                }
                            }
                        }
                        val updated = existing.copy(title = currentTitle, text = currentText, content = "")
                        diaryDao.update(updated)
                        "批量编辑完成: ${results.joinToString("; ")}"
                    } else {
                        "编辑失败：找不到 ID=${currentTask.id} 的笔记"
                    }
                    buildToolResultMessage(
                        toolName = currentTask.toolCall.functionName,
                        toolCallId = currentTask.toolCall.id,
                        keyword = "",
                        toolResult = resultText
                    )
                }
                is ToolTask.QueryChatHistory -> {
                    val messages = chatDao.getMessagesOnce(batch.chatId)
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    val startTimeDate = currentTask.startDate?.let { try { format.parse(it) } catch (e: Exception) { null } }
                    val endTimeDate = currentTask.endDate?.let { try { format.parse(it) } catch (e: Exception) { null } }
                    
                    val timeFiltered = messages.filter { msg ->
                        try {
                            val msgDate = format.parse(msg.date)
                            val passStart = startTimeDate == null || msgDate == null || !msgDate.before(startTimeDate)
                            val passEnd = endTimeDate == null || msgDate == null || !msgDate.after(endTimeDate)
                            passStart && passEnd
                        } catch (e: Exception) {
                            true
                        }
                    }
                    
                    val matched = if (currentTask.keyword.isNotEmpty()) {
                        timeFiltered.filter { it.content.contains(currentTask.keyword, ignoreCase = true) }
                    } else timeFiltered
                    
                    val results = matched.takeLast(currentTask.limit)
                    val resultText = if (results.isEmpty()) {
                        val k = currentTask.keyword.ifEmpty { "任意" }
                        "未找到符合条件(时间和关键词 '${k}') 的历史对话记录。"
                    } else {
                        results.joinToString("\n\n") { "(${it.date}) ${it.role}: ${it.content}" }
                    }
                    buildToolResultMessage(
                        toolName = currentTask.toolCall.functionName,
                        toolCallId = currentTask.toolCall.id,
                        keyword = currentTask.keyword,
                        toolResult = resultText
                    )
                }
                is ToolTask.WebSearchBaidu -> {
                    val prefs = getApplication<Application>().getSharedPreferences("api_keys", android.content.Context.MODE_PRIVATE)
                    val apiKey = prefs.getString("baidu_api_key", "") ?: ""
                    
                    val res = if (apiKey.isEmpty()) {
                        Result.success("说明：由于用户未配置百度千帆大模型API Key，联网搜索请求失败。")
                    } else {
                        aiHttp.performBaiduWebSearch(currentTask.keyword, apiKey)
                    }
                    
                    val resultStr = res.getOrElse { "搜索出错: ${it.message}" }
                    buildToolResultMessage(
                        toolName = currentTask.toolCall.functionName,
                        toolCallId = currentTask.toolCall.id,
                        keyword = currentTask.keyword,
                        toolResult = "网络搜索结果：\n$resultStr"
                    )
                }
                is ToolTask.SetAlarm -> {
                    val resultText = try {
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val targetDate = format.parse(currentTask.dateTime)
                        if (targetDate != null) {
                            val calendar = Calendar.getInstance().apply { time = targetDate }
                            val hour = calendar.get(Calendar.HOUR_OF_DAY)
                            val minute = calendar.get(Calendar.MINUTE)
                            
                            // 构建taskPayload JSON（始终为AI任务）
                            val taskPayload = JSONObject().apply {
                                put("aiId", batch.aiConfig.id)
                                put("avatarUri", batch.aiConfig.avatarUri)
                            }.toString()
                            
                            val alarm = AlarmEntity(
                                name = currentTask.name,
                                hour = hour,
                                minute = minute,
                                isActive = true,
                                repeatDays = currentTask.repeatDays,
                                remark = currentTask.taskPrompt,
                                aiConfigId = batch.aiConfig.id,
                                chatId = batch.chatId,
                                actionType = "PROCESS_NOTE",
                                taskPayload = taskPayload
                            )
                            val newId = alarmDao.insertAlarm(alarm).toInt()
                            val savedAlarm = alarm.copy(id = newId)
                            ChainAlarmHelper.scheduleNextAlarm(getApplication(), savedAlarm)
                            
                            val repeatInfo = if (currentTask.repeatDays.isNotEmpty()) {
                                val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
                                "，重复：${currentTask.repeatDays.mapNotNull { dayNames.getOrNull(it) }.joinToString(",")}"
                            } else ""
                            "AI任务设置成功：「${currentTask.name}」将于 ${currentTask.dateTime} 执行任务$repeatInfo"
                        } else {
                            "提醒设置失败：无法解析时间格式 ${currentTask.dateTime}"
                        }
                    } catch (e: Exception) {
                        "提醒设置失败：${e.message}"
                    }
                    buildToolResultMessage(
                        toolName = currentTask.toolCall.functionName,
                        toolCallId = currentTask.toolCall.id,
                        keyword = "",
                        toolResult = resultText
                    )
                }
                is ToolTask.CancelAlarm -> {
                    val resultText = try {
                        val alarm = alarmDao.getAlarmByIdSync(currentTask.alarmId)
                        if (alarm != null && alarm.aiConfigId == batch.aiConfig.id) {
                            ChainAlarmHelper.cancelAlarm(getApplication(), alarm.id)
                            alarmDao.deleteAlarmById(currentTask.alarmId)
                            "已取消任务提醒：「${alarm.name}」(ID: ${currentTask.alarmId})"
                        } else if (alarm == null) {
                            "取消失败：未找到ID为 ${currentTask.alarmId} 的提醒"
                        } else {
                            "取消失败：无权取消其他AI的提醒"
                        }
                    } catch (e: Exception) {
                        "取消失败：${e.message}"
                    }
                    buildToolResultMessage(
                        toolName = currentTask.toolCall.functionName,
                        toolCallId = currentTask.toolCall.id,
                        keyword = "",
                        toolResult = resultText
                    )
                }
            }
            currentBatch = batch.copy(
                completedResults = batch.completedResults + resultMessage,
                executedTools = batch.executedTools + currentTask.title
            )
            processNextToolInBatch()
        }
    }

    fun cancelPendingToolAction() {
        _pendingToolUI.value = null
        val batch = currentBatch ?: return
        val currentTask = batch.allTasks[batch.completedResults.size]

        val resultMessage = buildToolResultMessage(
            toolName = currentTask.toolCall.functionName,
            toolCallId = currentTask.toolCall.id,
            keyword = "",
            toolResult = "用户拒绝了该工具调用。"
        )
        currentBatch = batch.copy(
            completedResults = batch.completedResults + resultMessage,
            executedTools = batch.executedTools + "${currentTask.title} (已拒绝)"
        )
        processNextToolInBatch()
    }
    
    private fun processNextToolInBatch() {
        val batch = currentBatch ?: return
        if (batch.completedResults.size == batch.allTasks.size) {
            val finalMessages = batch.baseMessages + listOf(batch.assistantMessage) + batch.completedResults
            val finalExecutedTools = batch.executedTools
            currentBatch = null
            _aiState.value = AiState.Loading
            viewModelScope.launch {
                val result = requestAiResponse(batch.aiConfig, finalMessages, batch.enabledTools)

                handleAiResponse(
                    chatId = batch.chatId, 
                    result = result, 
                    messages = finalMessages, 
                    enabledTools = batch.enabledTools,
                    config = batch.aiConfig,
                    executedTools = finalExecutedTools
                )
            }
            return
        }

        val nextTask = batch.allTasks[batch.completedResults.size]
        val shouldAutoConfirm =
            batch.aiConfig.enableStream ||
                autoConfirmTools ||
                nextTask is ToolTask.QueryChatHistory ||
                nextTask is ToolTask.ReadNotes

        if (shouldAutoConfirm) {
            confirmPendingToolAction(dontAskAgain = false)
        } else {
            // Show UI
            _pendingToolUI.value = PendingToolUIState(
                title = nextTask.title,
                description = nextTask.description,
                showDontAskAgain = batch.allTasks.size > 1 || !autoConfirmTools
            )
            _aiState.value = AiState.Idle
        }
    }

    fun resetAiState() {
        _aiState.value = AiState.Idle
    }

    // ========== 私有：消息构建 ==========

    /**
     * 构建发送给 AI 的完整消息列表：
     * [system(静态规则)] + [user(动态上下文)] + [历史消息]
     */
    private suspend fun buildContextMessages(chatId: Long, currentAiConfig: AiChatConfig, enabledTools: Set<String>): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()

        // 获取全部配置用于映射名字
        val userConfig = chatDao.getUserConfigOnce(chatId)
        val aiConfigs = chatDao.getAiConfigOnce(chatId)
        
        // 获取当前对话的历史轮数设置
        val chat = chatDao.getChatByIdSuspend(chatId)
        val historyRounds = chat?.historyRounds ?: DEFAULT_HISTORY_ROUNDS

        // 历史消息（此时已经包含了最新的用户消息或者其他 AI 的回复）
        // 使用用户设置的轮数，每轮包含一来一回，所以乘2再加1确保最新消息
        val history = chatDao.getMessagesOnce(chatId).takeLast(historyRounds * 2 + 1)

        // system 消息：静态规则
        val staticSystemContent = buildSystemMessage(chatId, currentAiConfig, enabledTools)
        if (staticSystemContent.isNotEmpty()) {
            messages.add(mapOf("role" to "system", "content" to staticSystemContent))
        }

        // user 消息：动态上下文（会随时间、检索和提醒变化）
        val dynamicSystemContent = buildDynamicContextMessage(currentAiConfig, enabledTools, history)
        if (dynamicSystemContent.isNotEmpty()) {
            messages.add(mapOf("role" to userRole, "content" to dynamicSystemContent))
        }

        history.forEach { msg ->
            val isUserMessage = msg.role == userRole
            val isCurrentAiMessage =
                msg.role == assistantRole &&
                    msg.aiId == currentAiConfig.id

            val senderName = if (isUserMessage) {
                userConfig?.name ?: "我"
            } else {
                aiConfigs.find { it.id == msg.aiId }?.name ?: "AI"
            }
            
            // 使得当前 AI 认为其他 AI 的消息也是以不同名字发送的 user 消息
            val actualRole = if (isCurrentAiMessage) {
                assistantRole
            } else {
                userRole
            }
            
            // 时间和发送者信息已在系统提示词中提供，消息内容保持简洁
            val formattedContent = msg.content
            messages.add(mapOf("role" to actualRole, "content" to formattedContent))
        }

        return messages
    }

    /**
     * 构建静态 system 消息：身份、背景、工具规范（不放动态数据）
     */
    private suspend fun buildSystemMessage(chatId: Long, currentAiConfig: AiChatConfig, enabledTools: Set<String>): String {
        val userContext = buildContextFromConfig(
            chatDao.getUserConfigOnce(chatId)?.referencedDiaryId
        )
        val aiContext = buildContextFromConfig(currentAiConfig.referencedDiaryId)
        
        return buildString {
            append("你是${currentAiConfig.name}，回复时不要添加名字或时间前缀。\n")
            if (userContext.isNotEmpty()) append("【用户】$userContext\n")
            if (aiContext.isNotEmpty()) append("【背景】$aiContext\n")

            append("【来源判别规则】\n")
            append("- 标记为【记忆库】或【记忆库RAG检索结果】的内容，来源是本地笔记/向量检索，不是互联网。\n")
            append("- 标记为【网络搜索】的内容，来源是互联网搜索结果，不是本地笔记。\n")
            append("- 涉及实时或外部知识时，优先通过 web_search_baidu 获取最新网络知识，并标注来源。\n")
            
            // 工具说明
            val toolsList = mutableListOf<String>()
            if ("read_notes" in enabledTools) toolsList.add("read_notes")
            if ("write_note" in enabledTools) toolsList.add("write_note")
            if ("edit_note" in enabledTools) toolsList.add("edit_note/batch_edit_note")
            if ("create_plan" in enabledTools) toolsList.add("create_plan")
            if ("cancel_plan" in enabledTools) toolsList.add("cancel_plan")
            if ("web_search_baidu" in enabledTools) toolsList.add("web_search_baidu")
            
            if (toolsList.isNotEmpty()) {
                append("【工具】可用：${toolsList.joinToString("/")}，通过 tool_calls 调用。")
                if (currentAiConfig.boundFolder.isNotBlank()) {
                    append("限定文件夹：${currentAiConfig.boundFolder}")
                }
                append("\n")
            }
        }.trim()
    }

    /**
     * 构建动态 system 消息：完整笔记列表、RAG 检索、提醒列表、时间信息等
     */
    private suspend fun buildDynamicContextMessage(
        currentAiConfig: AiChatConfig,
        enabledTools: Set<String>,
        history: List<ChatMessage>
    ): String {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(Date())
        return buildString {
            append("【动态上下文】以下信息会变化，请优先参考本段。\n")

            append("【时间】$currentTime")
            if (history.size >= 2) {
                append("（上次对话：${history[history.size - 2].date}）")
            }
            append("\n")

            // 注入记忆库概览（绑定文件夹的完整笔记列表）
            if (currentAiConfig.boundFolder.isNotBlank()) {
                val folderNotes = diaryDao.getDiariesByFolder(currentAiConfig.boundFolder)
                if (folderNotes.isNotEmpty()) {
                    append("【记忆库】${currentAiConfig.boundFolder}（${folderNotes.size}篇）：\n")
                    append(folderNotes.joinToString("\n") { note ->
                        "- [${note.id}] ${note.title}（${note.date.take(20)}）"
                    })
                    append("\n")
                }
            }
            
            // RAG 检索相关笔记（限制在 AI 绑定的文件夹内）
            if (currentAiConfig.enableReadNotes && history.isNotEmpty()) {
                val lastUserMessage = history.lastOrNull { it.role == userRole }?.content ?: ""
                if (lastUserMessage.isNotBlank()) {
                    val ragContext = com.xinkong.diary.rag.RAG.prepareContext(
                        getApplication(),
                        lastUserMessage,
                        boundFolder = currentAiConfig.boundFolder.ifBlank { null },
                        maxResults = 3
                    )
                    if (ragContext.isNotBlank()) {
                        append("【记忆库RAG检索结果】\n")
                        append(ragContext)
                        append("\n")
                    }
                }
            }

            // 网络搜索动态知识说明（当前轮可用性）
            if ("web_search_baidu" in enabledTools) {
                append("【网络搜索知识】本轮可通过 web_search_baidu 获取最新互联网知识；如有网络搜索结果，优先使用并标注来源。\n")
            }
            
            // 注入AI的提醒列表
            if ("create_plan" in enabledTools || "cancel_plan" in enabledTools) {
                val myAlarms = alarmDao.getActiveAlarmsByAiConfigIdSync(currentAiConfig.id)
                if (myAlarms.isNotEmpty()) {
                    append("【我的提醒】(${myAlarms.size}项)\n")
                    myAlarms.forEach { alarm ->
                        val timeStr = String.format("%02d:%02d", alarm.hour, alarm.minute)
                        val repeatStr = if (alarm.repeatDays.isEmpty()) "仅一次" else {
                            val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
                            alarm.repeatDays.mapNotNull { dayNames.getOrNull(it) }.joinToString(",")
                        }
                        append("- [ID:${alarm.id}] ${alarm.name} $timeStr $repeatStr")
                        if (alarm.remark.isNotBlank()) append(" 任务:${alarm.remark.take(20)}")
                        append("\n")
                    }
                }
            }
        }.trim()
    }

    private suspend fun buildContextFromConfig(referencedDiaryId: String?, maxLength: Int = 800): String {
        if (referencedDiaryId.isNullOrEmpty()) return ""
        val ids = try {
            Json.decodeFromString<List<Long>>(referencedDiaryId)
        } catch (e: Exception) {
            emptyList()
        }
        if (ids.isEmpty()) return ""
        val diaries = diaryDao.getDiariesByIds(ids)
        val fullText = diaries.joinToString("\n") { "${it.title}: ${it.text}" }
        return if (fullText.length > maxLength) {
            fullText.take(maxLength) + "...(已截断)"
        } else fullText
    }

    /**
     * 清理AI回复中的名字和时间前缀
     */
    private fun cleanAiResponsePrefix(content: String, aiName: String?): String {
        var result = content
        val nameToCheck = aiName ?: "AI"
        
        // 移除名字前缀的各种变体：AI助手：、Ai助手:、AI：等
        // 使用循环处理重复前缀，如：AI助手：AI助手：
        val namePrefixes = listOf(
            Regex("^(?i)${Regex.escape(nameToCheck)}[:：]\\s*"),
            Regex("^(?i)ai助手[:：]\\s*"),
            Regex("^(?i)ai[:：]\\s*"),
            Regex("^${Regex.escape(nameToCheck)}[:：]\\s*")
        )
        
        var changed = true
        while (changed) {
            changed = false
            for (regex in namePrefixes) {
                if (regex.containsMatchIn(result)) {
                    result = result.replaceFirst(regex, "").trimStart()
                    changed = true
                }
            }
        }
        
        // 移除时间前缀 [时间: yyyy-MM-dd HH:mm] 或 [时间:yyyy-MM-dd HH:mm]
        val timePrefixRegex = Regex("^\\[时间[:：]\\s*\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}\\]\\s*")
        while (timePrefixRegex.containsMatchIn(result)) {
            result = result.replaceFirst(timePrefixRegex, "").trimStart()
        }
        
        return result
    }

    // ========== 私有：响应处理 ==========

    /**
     * 处理 AI 响应（未来可扩展技能调用解析）
     */
    private suspend fun handleAiResponse(
        chatId: Long,
        result: Result<AiResponse>,
        messages: List<Map<String, Any>>,
        enabledTools: Set<String>,
        config: AiChatConfig? = null,
        executedTools: List<String> = emptyList()
    ) {
        result.fold(
            onSuccess = { response ->
                when (response) {
                    is AiResponse.Message -> {
                        val tasks = parseToolTasks(response.toolCalls, enabledTools, config?.boundFolder ?: "")
                        if (tasks.isNotEmpty()) {
                            val validToolCalls = tasks.map { it.toolCall }
                            val assistantToolCallMessage = buildAssistantToolCallMessage(
                                content = response.content,
                                toolCalls = validToolCalls
                            )
    
                            if (response.content.isNotBlank()) {
                                var tempContent = response.content
                                tempContent = cleanAiResponsePrefix(tempContent, config?.name)
                                
                                val aiMsg = ChatMessage(
                                    chatId = chatId,
                                    role = "assistant",
                                    content = tempContent,
                                    date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                                    toolExecutions = "[]", // 中间回复不展示工具执行记录，留到最终汇总
                                    aiId = config?.id,
                                    reasoningContent = response.reasoningContent
                                )
                                chatDao.insertMessage(aiMsg)
                            }
    
                            val aiConfig = chatDao.getAiConfigOnce(chatId).firstOrNull() ?: AiChatConfig(chatId = chatId)
                            currentBatch = ToolBatchContext(
                                chatId = chatId,
                                aiConfig = aiConfig,
                                enabledTools = enabledTools,
                                baseMessages = messages,
                                assistantMessage = assistantToolCallMessage,
                                allTasks = tasks,
                                completedResults = emptyList(),
                                executedTools = executedTools
                            )
                            processNextToolInBatch()
                            return@fold
                        }

                        var displayContent = response.content.ifBlank { "(空回复)" }
                        displayContent = cleanAiResponsePrefix(displayContent, config?.name)
                        
                        val toolExecutionsStr = Json.encodeToString(executedTools)
                        val aiMsg = ChatMessage(
                            chatId = chatId,
                            role = "assistant",
                            content = displayContent,
                            date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                            toolExecutions = toolExecutionsStr,
                            aiId = config?.id,
                            reasoningContent = response.reasoningContent
                        )
                        chatDao.insertMessage(aiMsg)
                        _aiState.value = AiState.Success(result = displayContent)
                    }
                }
            },
            onFailure = { error ->
                val toolExecutionsStr = Json.encodeToString(executedTools)
                val errMsg = ChatMessage(
                    chatId = chatId,
                    role = "assistant",
                    content = error.message ?: "AI回复失败",
                    date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                    toolExecutions = toolExecutionsStr,
                    aiId = config?.id
                )
                chatDao.insertMessage(errMsg)
                _aiState.value = AiState.Error(message = error.message ?: "AI回复失败")
            }
        )
    }

    private fun parseToolTasks(toolCalls: List<AiToolCall>, enabledTools: Set<String>, boundFolder: String = ""): List<ToolTask> {
        val tasks = mutableListOf<ToolTask>()
        for (call in toolCalls) {
            when {
                call.type == "function" && call.functionName == "read_notes" && "read_notes" in enabledTools -> {
                    try {
                        val json = JSONObject(call.arguments)
                        val keyword = json.optString("keyword").trim()
                        if (keyword.isNotEmpty() && keyword.matches(Regex("^[\\w\\u4e00-\\u9fa5]+$"))) {
                            val limit = json.optInt("limit", 3).coerceIn(1, 5)
                            tasks.add(ToolTask.ReadNotes(call, keyword, limit))
                        }
                    } catch (e: Exception) {}
                }
                call.type == "function" && call.functionName == "write_note" && "write_note" in enabledTools -> {
                    try {
                        val json = JSONObject(call.arguments)
                        val title = json.optString("title").trim()
                        val content = json.optString("content").trim()
                        val tag = json.optString("tag", "未分类").trim()
                        if (title.isNotEmpty() && content.isNotEmpty()) {
                            tasks.add(ToolTask.WriteNote(call, title, content, tag))
                        }
                    } catch (e: Exception) {}
                }
                call.type == "function" && call.functionName == "edit_note" && "edit_note" in enabledTools -> {
                    try {
                        val json = JSONObject(call.arguments)
                        if (json.has("id")) {
                            val id = json.getLong("id")
                            val title = if (json.has("title")) json.getString("title") else null
                            val content = if (json.has("content")) json.getString("content") else null
                            val tag = if (json.has("tag")) json.getString("tag") else null
                            tasks.add(ToolTask.EditNote(call, id, title, content, tag))
                        }
                    } catch (e: Exception) {}
                }
                call.type == "function" && call.functionName == "batch_edit_note" && "edit_note" in enabledTools -> {
                    try {
                        val json = JSONObject(call.arguments)
                        val id = json.getLong("id")
                        val opsArray = json.getJSONArray("operations")
                        val operations = mutableListOf<ToolTask.NoteOperation>()
                        for (i in 0 until opsArray.length()) {
                            val opJson = opsArray.getJSONObject(i)
                            operations.add(ToolTask.NoteOperation(
                                op = opJson.getString("op"),
                                value = opJson.optString("value").takeIf { it.isNotEmpty() },
                                old = opJson.optString("old").takeIf { it.isNotEmpty() },
                                new = opJson.optString("new")
                            ))
                        }
                        if (operations.isNotEmpty()) {
                            tasks.add(ToolTask.BatchEditNote(call, id, operations))
                        }
                    } catch (e: Exception) {}
                }
                call.type == "function" && call.functionName == "query_chat_history" && "query_chat_history" in enabledTools -> {
                    try {
                        val json = JSONObject(call.arguments)
                        val keyword = json.optString("keyword").trim()
                        val limit = json.optInt("limit", 20).coerceIn(1, 50)
                        val startDate = json.optString("startDate").takeIf { it.isNotBlank() }
                        val endDate = json.optString("endDate").takeIf { it.isNotBlank() }
                        tasks.add(ToolTask.QueryChatHistory(call, keyword, limit, startDate, endDate))
                    } catch (e: Exception) {}
                }
                call.type == "function" && call.functionName == "web_search_baidu" && "web_search_baidu" in enabledTools -> {
                    try {
                        val json = JSONObject(call.arguments)
                        val keyword = json.optString("keyword").trim()
                        if (keyword.isNotEmpty()) {
                            tasks.add(ToolTask.WebSearchBaidu(call, keyword))
                        }
                    } catch (e: Exception) {}
                }
                call.type == "function" && call.functionName == "create_plan" && "create_plan" in enabledTools -> {
                    try {
                        val json = JSONObject(call.arguments)
                        val name = json.optString("name").trim()
                        val dateTime = json.optString("dateTime").trim()
                        val taskPrompt = json.optString("taskPrompt", "").trim()
                        val repeatDays = mutableListOf<Int>()
                        if (json.has("repeatDays")) {
                            val arr = json.optJSONArray("repeatDays")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val day = arr.optInt(i, 0)
                                    if (day in 1..7) repeatDays.add(day)
                                }
                            }
                        }
                        if (name.isNotEmpty() && dateTime.isNotEmpty() && taskPrompt.isNotEmpty()) {
                            tasks.add(ToolTask.SetAlarm(call, name, dateTime, taskPrompt, repeatDays.sorted()))
                        }
                    } catch (e: Exception) {}
                }
                call.type == "function" && call.functionName == "cancel_plan" && "cancel_plan" in enabledTools -> {
                    try {
                        val json = JSONObject(call.arguments)
                        val alarmId = json.optInt("alarmId", 0)
                        if (alarmId > 0) {
                            tasks.add(ToolTask.CancelAlarm(call, alarmId))
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        return tasks
    }

    private fun buildAssistantToolCallMessage(content: String, toolCalls: List<AiToolCall>): Map<String, Any> {
        val callsList = toolCalls.map { call ->
            mapOf(
                "id" to call.id,
                "type" to call.type,
                "function" to mapOf(
                    "name" to call.functionName,
                    "arguments" to call.arguments
                )
            )
        }
        return mapOf(
            "role" to "assistant",
            "content" to content,
            "tool_calls" to callsList
        )
    }

    private fun formatDiaryToolResult(diaries: List<com.xinkong.diary.repository.Diary>): String {
        if (diaries.isEmpty()) {
            return "未找到匹配笔记。"
        }
        return diaries.joinToString("\n\n") { diary ->
            "[id=${diary.id}] 标题：${diary.title}\n时间：${diary.date}\n标签：${diary.tag}\n正文：\n${diary.text.ifEmpty { diary.content }}"
        }
    }

    private fun buildToolResultMessage(toolName: String, toolCallId: String, keyword: String, toolResult: String): Map<String, Any> {
        val sourceType = when (toolName) {
            "read_notes", "write_note", "edit_note", "batch_edit_note" -> "记忆库"
            "web_search_baidu" -> "网络搜索"
            "query_chat_history" -> "对话历史"
            "create_plan", "cancel_plan" -> "计划工具"
            else -> "工具"
        }
        val displayStr = buildString {
            append("【来源类型】$sourceType\n")
            if (keyword.isNotEmpty()) append("关键词：$keyword\n")
            append(toolResult)
        }

        // read_notes 结果作为上文背景，以 user 消息传递
        if (toolName == "read_notes") {
            return mapOf(
                "role" to userRole,
                "content" to "【记忆库检索背景】\n$displayStr"
            )
        }

        return mapOf(
            "role" to "tool",
            "tool_call_id" to toolCallId,
            "name" to toolName,
            "content" to displayStr
        )
    }

    private suspend fun executeBackgroundToolTask(task: ToolTask, chatId: Long, aiConfigId: Long, defaultFolder: String): Map<String, Any> {
        return when (task) {
            is ToolTask.ReadNotes -> {
                val diaries = diaryDao.searchByKeyword(task.keyword, task.limit)
                    .filter { it.tagFolder == defaultFolder }
                buildToolResultMessage(
                    toolName = task.toolCall.functionName,
                    toolCallId = task.toolCall.id,
                    keyword = task.keyword,
                    toolResult = formatDiaryToolResult(diaries)
                )
            }
            is ToolTask.WriteNote -> {
                val newDiary = com.xinkong.diary.repository.Diary(
                    title = task.noteTitle,
                    text = task.content,
                    content = "",
                    date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                    tag = task.tag,
                    tagFolder = defaultFolder
                )
                diaryDao.insert(newDiary)
                buildToolResultMessage(
                    toolName = task.toolCall.functionName,
                    toolCallId = task.toolCall.id,
                    keyword = "",
                    toolResult = "笔记新增成功: ${task.noteTitle}"
                )
            }
            is ToolTask.EditNote -> {
                val existing = diaryDao.getDiaryById(task.id)
                val resultText = if (existing != null) {
                    val newText = task.content ?: existing.text
                    val updated = existing.copy(
                        title = task.noteTitle ?: existing.title,
                        text = newText,
                        content = "",
                        tag = task.tag ?: existing.tag
                    )
                    diaryDao.update(updated)
                    "笔记修改成功: ID=${task.id}"
                } else {
                    "修改失败：找不到 ID=${task.id} 的笔记"
                }
                buildToolResultMessage(
                    toolName = task.toolCall.functionName,
                    toolCallId = task.toolCall.id,
                    keyword = "",
                    toolResult = resultText
                )
            }
            is ToolTask.BatchEditNote -> {
                val existing = diaryDao.getDiaryById(task.id)
                val resultText = if (existing != null) {
                    var currentText = existing.text
                    var currentTitle = existing.title
                    val results = mutableListOf<String>()
                    for (op in task.operations) {
                        when (op.op) {
                            "set_title" -> {
                                op.value?.let {
                                    currentTitle = it
                                    results.add("标题已改为「$it」")
                                }
                            }
                            "append" -> {
                                op.value?.let {
                                    currentText = if (currentText.isEmpty()) it else "$currentText\n$it"
                                    results.add("已追加内容")
                                }
                            }
                            "replace" -> {
                                val oldText = op.old
                                val newText = op.new ?: ""
                                if (oldText != null && currentText.contains(oldText)) {
                                    currentText = currentText.replaceFirst(oldText, newText)
                                    results.add("已替换「${oldText.take(15)}...」")
                                } else {
                                    results.add("未找到「${oldText?.take(15)}...」")
                                }
                            }
                        }
                    }
                    val updated = existing.copy(title = currentTitle, text = currentText, content = "")
                    diaryDao.update(updated)
                    "批量编辑完成: ${results.joinToString("; ")}"
                } else {
                    "编辑失败：找不到 ID=${task.id} 的笔记"
                }
                buildToolResultMessage(
                    toolName = task.toolCall.functionName,
                    toolCallId = task.toolCall.id,
                    keyword = "",
                    toolResult = resultText
                )
            }
            is ToolTask.QueryChatHistory -> {
                val messages = chatDao.getMessagesOnce(chatId)
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                val startTimeDate = task.startDate?.let { try { format.parse(it) } catch (e: Exception) { null } }
                val endTimeDate = task.endDate?.let { try { format.parse(it) } catch (e: Exception) { null } }
                val timeFiltered = messages.filter { msg ->
                    try {
                        val msgDate = format.parse(msg.date)
                        val passStart = startTimeDate == null || msgDate == null || !msgDate.before(startTimeDate)
                        val passEnd = endTimeDate == null || msgDate == null || !msgDate.after(endTimeDate)
                        passStart && passEnd
                    } catch (e: Exception) {
                        true
                    }
                }
                val matched = if (task.keyword.isNotEmpty()) {
                    timeFiltered.filter { it.content.contains(task.keyword, ignoreCase = true) }
                } else timeFiltered
                val results = matched.takeLast(task.limit)
                val resultText = if (results.isEmpty()) {
                    val k = task.keyword.ifEmpty { "任意" }
                    "未找到符合条件(时间和关键词 '${k}') 的历史对话记录。"
                } else {
                    results.joinToString("\n\n") { "(${it.date}) ${it.role}: ${it.content}" }
                }
                buildToolResultMessage(
                    toolName = task.toolCall.functionName,
                    toolCallId = task.toolCall.id,
                    keyword = task.keyword,
                    toolResult = resultText
                )
            }
            is ToolTask.WebSearchBaidu -> {
                val prefs = getApplication<Application>().getSharedPreferences("api_keys", android.content.Context.MODE_PRIVATE)
                val apiKey = prefs.getString("baidu_api_key", "") ?: ""
                val res = if (apiKey.isEmpty()) {
                    Result.success("说明：由于用户未配置百度千帆大模型API Key，联网搜索请求失败。")
                } else {
                    aiHttp.performBaiduWebSearch(task.keyword, apiKey)
                }
                val resultStr = res.getOrElse { "搜索出错: ${it.message}" }
                buildToolResultMessage(
                    toolName = task.toolCall.functionName,
                    toolCallId = task.toolCall.id,
                    keyword = task.keyword,
                    toolResult = "网络搜索结果：\n$resultStr"
                )
            }
            is ToolTask.SetAlarm -> {
                val resultText = try {
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val targetDate = format.parse(task.dateTime)
                    if (targetDate != null) {
                        val calendar = Calendar.getInstance().apply { time = targetDate }
                        val hour = calendar.get(Calendar.HOUR_OF_DAY)
                        val minute = calendar.get(Calendar.MINUTE)
                        
                        // 获取AI配置以获取avatarUri
                        val aiConfig = chatDao.getAiConfigById(aiConfigId)
                        
                        // 构建taskPayload JSON（始终为AI任务）
                        val taskPayload = JSONObject().apply {
                            put("aiId", aiConfigId)
                            if (aiConfig != null) put("avatarUri", aiConfig.avatarUri)
                        }.toString()
                        
                        val alarm = AlarmEntity(
                            name = task.name,
                            hour = hour,
                            minute = minute,
                            isActive = true,
                            repeatDays = task.repeatDays,
                            remark = task.taskPrompt,
                            aiConfigId = aiConfigId,
                            chatId = chatId,
                            actionType = "PROCESS_NOTE",
                            taskPayload = taskPayload
                        )
                        val newId = alarmDao.insertAlarm(alarm).toInt()
                        val savedAlarm = alarm.copy(id = newId)
                        ChainAlarmHelper.scheduleNextAlarm(getApplication(), savedAlarm)
                        
                        val repeatInfo = if (task.repeatDays.isNotEmpty()) {
                            val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
                            "，重复：${task.repeatDays.mapNotNull { dayNames.getOrNull(it) }.joinToString(",")}"
                        } else ""
                        "AI任务设置成功：「${task.name}」将于 ${task.dateTime} 执行任务$repeatInfo"
                    } else {
                        "提醒设置失败：无法解析时间格式 ${task.dateTime}"
                    }
                } catch (e: Exception) {
                    "提醒设置失败：${e.message}"
                }
                buildToolResultMessage(
                    toolName = task.toolCall.functionName,
                    toolCallId = task.toolCall.id,
                    keyword = "",
                    toolResult = resultText
                )
            }
            is ToolTask.CancelAlarm -> {
                val resultText = try {
                    val alarm = alarmDao.getAlarmByIdSync(task.alarmId)
                    if (alarm != null && alarm.aiConfigId == aiConfigId) {
                        ChainAlarmHelper.cancelAlarm(getApplication(), alarm.id)
                        alarmDao.deleteAlarmById(task.alarmId)
                        "已取消任务提醒：「${alarm.name}」(ID: ${task.alarmId})"
                    } else if (alarm == null) {
                        "取消失败：未找到ID为 ${task.alarmId} 的提醒"
                    } else {
                        "取消失败：无权取消其他AI的提醒"
                    }
                } catch (e: Exception) {
                    "取消失败：${e.message}"
                }
                buildToolResultMessage(
                    toolName = task.toolCall.functionName,
                    toolCallId = task.toolCall.id,
                    keyword = "",
                    toolResult = resultText
                )
            }
        }
    }

    private suspend fun buildAlarmContextMessages(
        chatId: Long,
        currentAiConfig: AiChatConfig,
        enabledTools: Set<String>,
        taskPrompt: String,
        referencedDiaryIdOverride: String?
    ): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()
        val systemContent = buildAlarmSystemMessage(chatId, currentAiConfig, enabledTools, referencedDiaryIdOverride)
        if (systemContent.isNotEmpty()) {
            messages.add(mapOf("role" to "system", "content" to systemContent))
        }

        val userConfig = chatDao.getUserConfigOnce(chatId)
        val aiConfigs = chatDao.getAiConfigOnce(chatId)
        
        // 获取当前对话的历史轮数设置
        val chat = chatDao.getChatByIdSuspend(chatId)
        val historyRounds = chat?.historyRounds ?: DEFAULT_HISTORY_ROUNDS
        val history = chatDao.getMessagesOnce(chatId).takeLast(historyRounds * 2 + 1)
        history.forEach { msg ->
            val isUserMessage = msg.role == userRole
            val isCurrentAiMessage = msg.role == assistantRole && msg.aiId == currentAiConfig.id
            val senderName = if (isUserMessage) userConfig?.name ?: "我" else aiConfigs.find { it.id == msg.aiId }?.name ?: "AI"
            val actualRole = if (isCurrentAiMessage) assistantRole else userRole
            val formattedContent = "${senderName}(${msg.date}): ${msg.content}"
            messages.add(mapOf("role" to actualRole, "content" to formattedContent))
        }

        val nowText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(Date())
        messages.add(
            mapOf(
                "role" to userRole,
                "content" to "请执行计划任务（触发时间：$nowText）：\n$taskPrompt"
            )
        )
        return messages
    }

    private suspend fun buildAlarmSystemMessage(
        chatId: Long,
        currentAiConfig: AiChatConfig,
        enabledTools: Set<String>,
        referencedDiaryIdOverride: String?
    ): String {
        val userContext = buildContextFromConfig(chatDao.getUserConfigOnce(chatId)?.referencedDiaryId)
        val aiContext = buildContextFromConfig(referencedDiaryIdOverride ?: currentAiConfig.referencedDiaryId)
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(Date())
        return buildString {
            append("【时间】$currentTime\n")
            append("你是${currentAiConfig.name}，正在执行定时任务，严格按指令完成。\n")
            if (userContext.isNotEmpty()) append("【用户】$userContext\n")
            if (aiContext.isNotEmpty()) append("【背景】$aiContext\n")
            if ("read_notes" in enabledTools || "edit_note" in enabledTools) {
                append("【工具】可用：read_notes/write_note/edit_note/batch_edit_note，通过 tool_calls 调用。\n")
            }
        }.trim()
    }

    // 保存多 AI 回复的模式与启用顺序
    fun saveReplySelection(chatId: Long, selectedAIs: List<AiChatConfig>) {
        viewModelScope.launch {
            // 这里我们更新所有在这个 chatId 下的 AiChatConfig 的 isEnabled 和 replyOrder
            val allConfigs = chatDao.getAiConfigOnce(chatId)
            allConfigs.forEach { config ->
                val index = selectedAIs.indexOfFirst { it.id == config.id }
                val updatedConfig = config.copy(
                    isEnabled = index != -1,
                    replyOrder = if (index != -1) index + 1 else 0
                )
                chatDao.updateAiConfig(updatedConfig)
            }
        }
    }

    // 直接回复：让选中的 AI 直接根据当前对话历史生成回复，而不需要用户发送新消息
    fun directReply(chatId: Long, selectedAIs: List<AiChatConfig>) {
        if (selectedAIs.isEmpty()) return
        viewModelScope.launch {
            saveReplySelection(chatId, selectedAIs)

            // 轮流回复：等待上一个 AI 回复完成后再请求下一个
            for (config in selectedAIs) {
                    _aiState.value = AiState.Loading
                    _currentTypingAi.value = config

                    val enabledTools = mutableSetOf<String>().apply {
                        add("query_chat_history")
                        add("web_search_baidu")
                        // 每个 AI 都有笔记工具权限（已隔离文件夹）
                        if (config.enableReadNotes) add("read_notes")
                        if (config.enableWriteNote) add("write_note")
                        if (config.enableEditNote) add("edit_note")
                        if (config.enableSetAlarm) {
                            add("create_plan")
                            add("cancel_plan")
                        }
                    }
                    val messages = buildContextMessages(chatId, config, enabledTools)
                    val result = requestAiResponse(config, messages, enabledTools)
                    // TODO: 若需要等待工具调用完全结束，此处可能要调整为观察状态
                    handleAiResponse(chatId, result, messages, enabledTools, config = config)
                    _currentTypingAi.value = null
            }
            _aiState.value = AiState.Idle
        }
    }

    // （已移除 buildMessagesForDirectReply 和 buildMessages）
}

