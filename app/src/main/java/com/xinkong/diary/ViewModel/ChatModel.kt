package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.Http.AiHttp
import kotlinx.coroutines.flow.collect
import com.xinkong.diary.Data.AiResponse
import com.xinkong.diary.Data.AiState
import com.xinkong.diary.Data.AiToolCall
import com.xinkong.diary.Data.ToolTask
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
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatDao()
    private val diaryDao = db.diaryDao()
    private val aiHttp = AiHttp()

    // ---- 对话列表状态 ----
    private val _chatListState = MutableStateFlow(listOf<Chat>())
    val chatListState: StateFlow<List<Chat>> = _chatListState.asStateFlow()

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

    init {
        viewModelScope.launch {
            chatDao.getAllChat().collect { chats ->
                _chatListState.update { chats }
            }
        }
    }

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

            // 获取当前对话的首个 AI，只有他拥有操作笔记工具的权限
            val firstAiId = chatDao.getAiConfigOnce(chatId).firstOrNull()?.id

            // 2. 轮流调用 AI
            for (config in selectedAIs) {
                _aiState.value = AiState.Loading
                _currentTypingAi.value = config

                val enabledTools = mutableSetOf<String>().apply {
                    if (!config.enableStream) {
                        add("query_chat_history")
                        if (config.enableWebSearch) add("web_search_baidu")
                        if (config.enableImageSupport) add("image_recognition")
                        // 只有第一个 AI 才有笔记工具权限
                        if (config.id == firstAiId) {
                            if (config.enableReadNotes) add("read_notes")
                            if (config.enableWriteNote) add("write_note")
                            if (config.enableEditNote) add("edit_note")
                        }
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

    // ========== 配置 ==========

    fun findAiConfig(chatId: Long): Flow<List<AiChatConfig>> = chatDao.findAiConfig(chatId)

    fun addAiConfig(chatId: Long) {
        viewModelScope.launch { chatDao.insertAiConfig(AiChatConfig(chatId = chatId)) }
    }

    fun updateAiConfig(config: AiChatConfig) {
        viewModelScope.launch { chatDao.updateAiConfig(config) }
    }

    fun deleteAiConfig(config: AiChatConfig) {
        viewModelScope.launch { chatDao.deleteAiConfig(config) }
    }

    fun findUserConfig(chatId: Long): Flow<UserChatConfig> = chatDao.findUserConfig(chatId)

    fun updateUserConfig(config: UserChatConfig) {
        viewModelScope.launch { chatDao.updateUserConfig(config) }
    }
    
    fun searchChat(keyword: String): Flow<List<Chat>> = chatDao.searchAllByKeyword(keyword)

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
        var capturedToolCalls = listOf<AiToolCall>()
        var chunkCount = 0

        return try {
            Log.d(TAG, "requestAiResponse: start stream request, enabledTools=$enabledTools")
            aiHttp.chatWithAiStream(config, messages, enabledTools).collect { chunk ->
                chunkCount++
                when (chunk) {
                    is AiResponse.StreamChunk.Content -> {
                        if (chunk.text.isNotEmpty()) {
                            fullContent += chunk.text
                            _aiState.value = AiState.Streaming(buildStreamingDisplayContent(fullContent, config))
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
                "requestAiResponse: stream finished, chunks=$chunkCount, contentLen=${fullContent.length}, toolCalls=${capturedToolCalls.size}"
            )
            if (fullContent.isBlank() && capturedToolCalls.isEmpty()) {
                // Some providers ignore stream mode and return non-SSE payload; fallback in that case.
                Log.w(TAG, "requestAiResponse: stream yielded no content/toolCalls, fallback to non-stream")
                aiHttp.chatWithAi(config, messages, enabledTools)
            } else {
                Log.d(TAG, "requestAiResponse: use stream result directly")
                Result.success(AiResponse.Message(content = fullContent, toolCalls = capturedToolCalls))
            }
        } catch (e: Exception) {
            // If we already got stream chunks, prefer partial stream result and avoid duplicate requests.
            if (fullContent.isNotBlank() || capturedToolCalls.isNotEmpty()) {
                Log.w(
                    TAG,
                    "stream ended with exception but has partial result, use partial stream output: ${e.message}",
                    e
                )
                Result.success(AiResponse.Message(content = fullContent, toolCalls = capturedToolCalls))
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
            val resultMessage = when (currentTask) {
                is ToolTask.ReadNotes -> {
                    val diaries = diaryDao.searchByKeyword(currentTask.keyword, currentTask.limit)
                        .filter { it.tagFolder == aiCurrentFolder }
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
                        text = "",
                        content = currentTask.content,
                        date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                        tag = currentTask.tag,
                        tagFolder = aiCurrentFolder
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
                        val updated = existing.copy(
                            title = currentTask.noteTitle ?: existing.title,
                            content = currentTask.content ?: existing.content,
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
                is ToolTask.GetTagsAndFolders -> {
                    val foldersAndTags = diaryDao.getAllTagsAndFolders()
                    val grouped = foldersAndTags.groupBy({ it.tagFolder }, { it.tag })
                    val formatted = "当前所有的分类结构：\n" + grouped.entries.joinToString("\n") { (folder, tags) ->
                        "- 文件夹：[$folder]，包含标签：${tags.joinToString(", ")}"
                    }
                    buildToolResultMessage(
                        toolName = currentTask.toolCall.functionName,
                        toolCallId = currentTask.toolCall.id,
                        keyword = "",
                        toolResult = formatted
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
        if (autoConfirmTools || nextTask is ToolTask.GetTagsAndFolders || nextTask is ToolTask.QueryChatHistory || nextTask is ToolTask.ReadNotes) {
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
     * [system(上下文)] + [带名称格式的全部历史消息]
     */
    private suspend fun buildContextMessages(chatId: Long, currentAiConfig: AiChatConfig, enabledTools: Set<String>): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()

        // system 消息：上下文资料和身份设定
        val systemContent = buildSystemMessage(chatId, currentAiConfig, enabledTools)
        if (systemContent.isNotEmpty()) {
            messages.add(mapOf("role" to "system", "content" to systemContent))
        }

        // 获取全部配置用于映射名字
        val userConfig = chatDao.getUserConfigOnce(chatId)
        val aiConfigs = chatDao.getAiConfigOnce(chatId)

        // 历史消息（此时已经包含了最新的用户消息或者其他 AI 的回复）
        val history = chatDao.getMessagesOnce(chatId).takeLast(13)
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
            
            // 将每条消息格式化为 [时间] $name: message 的形式
            val formattedContent = "[时间: ${msg.date}] ${senderName}: ${msg.content}"
            messages.add(mapOf("role" to actualRole, "content" to formattedContent))
        }

        val toolInstruction = buildToolInstruction(enabledTools)
        if (toolInstruction.isNotEmpty()) {
            // 将强制性指令附加在最后作为临时 system，不写入数据库历史
            messages.add(mapOf("role" to "system", "content" to "【系统提醒】\n$toolInstruction"))
        }

        return messages
    }

    private fun buildToolInstruction(enabledTools: Set<String>): String {
        return buildString {
            if ("read_notes" in enabledTools) {
                append(
                    """
    你可使用函数工具 read_notes。当需要查询笔记时，请直接返回标准 tool_calls（由系统注入）。
    如果不需要查询笔记，直接回复。
                    """.trimIndent()
                )
                append("\n")
            }
            if (enabledTools.isNotEmpty()) {
                append("重要严厉警告：绝对不要在回复的主体文本中向用户解释你的心路历程，也不要提到'read_notes'、'返回tool_calls'等字眼！仅隐式使用工具或直接回复。")
            }
        }.trim()
    }

    /**
     * 构建 system 消息：用户资料 + AI 资料
     */
    private suspend fun buildSystemMessage(chatId: Long, currentAiConfig: AiChatConfig, enabledTools: Set<String>): String {
        val userContext = buildContextFromConfig(
            chatDao.getUserConfigOnce(chatId)?.referencedDiaryId
        )
        val aiContext = buildContextFromConfig(currentAiConfig.referencedDiaryId)
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(Date())
        
        return buildString {
            append("当前系统时间：$currentTime\n")
            append("你现在的身份是：${currentAiConfig.name}。在接下来的对话记录中，所有发送的消息会以“发送者名称: 消息内容”的格式提供，如果消息带有[时间:xxx]标识，代表该消息发送的时间。请根据此多角色群聊记录进行回复。请严格用你的身份做出对应的反应，不要扮演其他角色。\n\n")
            if (userContext.isNotEmpty()) append("【用户资料】\n$userContext\n\n")
            if (aiContext.isNotEmpty()) append("【你的背景资料】\n$aiContext\n\n")
            if ("read_notes" in enabledTools) {
                append(
                    """
    【工具协议】
    你可使用函数工具（例如 read_notes）。如果你觉得需要使用工具，必须通过标准的 tool_calls 格式结构返回，不要在对话中用文本或者 markdown 告诉用户你在使用工具。
    参数格式：
    - keyword: string（中英文关键词，不含标点）
    - limit: integer（1 到 5）
    """.trimIndent()
                )
            }
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
        messages: List<Map<String, Any>>,
        enabledTools: Set<String>,
        config: AiChatConfig? = null,
        executedTools: List<String> = emptyList()
    ) {
        result.fold(
            onSuccess = { response ->
                when (response) {
                    is AiResponse.Message -> {
                        val tasks = parseToolTasks(response.toolCalls, enabledTools)
                        if (tasks.isNotEmpty()) {
                            val validToolCalls = tasks.map { it.toolCall }
                            val assistantToolCallMessage = buildAssistantToolCallMessage(
                                content = response.content,
                                toolCalls = validToolCalls
                            )
    
                            if (response.content.isNotBlank()) {
                                var tempContent = response.content
                                val aiNameTemp = config?.name ?: "AI"
                                val prefixRegex = Regex("^(?:(?:(?i)${Regex.escape(aiNameTemp)})|(?i)ai)[:：]\\s*")
                                while (prefixRegex.containsMatchIn(tempContent)) {
                                    tempContent = tempContent.replaceFirst(prefixRegex, "").trimStart()
                                }
                                
                                val aiMsg = ChatMessage(
                                    chatId = chatId,
                                    role = "assistant",
                                    content = tempContent,
                                    date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                                    toolExecutions = "[]", // 中间回复不展示工具执行记录，留到最终汇总
                                    aiId = config?.id
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
                        val aiNameTemp = config?.name ?: "AI"
                        val prefixRegex = Regex("^(?:(?:(?i)${Regex.escape(aiNameTemp)})|(?i)ai)[:：]\\s*")
                        while (prefixRegex.containsMatchIn(displayContent)) {
                            displayContent = displayContent.replaceFirst(prefixRegex, "").trimStart()
                        }
                        
                        val toolExecutionsStr = Json.encodeToString(executedTools)
                        val aiMsg = ChatMessage(
                            chatId = chatId,
                            role = "assistant",
                            content = displayContent,
                            date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                            toolExecutions = toolExecutionsStr,
                            aiId = config?.id
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

    private fun parseToolTasks(toolCalls: List<AiToolCall>, enabledTools: Set<String>): List<ToolTask> {
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
                call.type == "function" && call.functionName == "get_tags_and_folders" && "read_notes" in enabledTools -> {
                    tasks.add(ToolTask.GetTagsAndFolders(call))
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
        val displayStr = if (keyword.isNotEmpty()) "关键词：$keyword\n$toolResult" else toolResult
        return mapOf(
            "role" to "tool",
            "tool_call_id" to toolCallId,
            "name" to toolName,
            "content" to displayStr
        )
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

            // 获取当前对话的首个 AI
            val firstAiId = chatDao.getAiConfigOnce(chatId).firstOrNull()?.id
            
            // 轮流回复：等待上一个 AI 回复完成后再请求下一个
            for (config in selectedAIs) {
                    _aiState.value = AiState.Loading
                    _currentTypingAi.value = config

                    val enabledTools = mutableSetOf<String>().apply {
                        if (!config.enableStream) {
                            add("query_chat_history")
                            add("web_search_baidu")
                            if (config.id == firstAiId) {
                                if (config.enableReadNotes) add("read_notes")
                                if (config.enableWriteNote) add("write_note")
                                if (config.enableEditNote) add("edit_note")
                            }
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

