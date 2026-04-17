package com.xinkong.diary.ui.screen.chat.voice

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.data.AiState
import com.xinkong.diary.repository.AiChatConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed class CallState {
    object Idle : CallState()
    object Listening : CallState()
    object Thinking : CallState()
    object Speaking : CallState()
}

class CallViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "CallViewModel"

    private val voiceManager = VoiceInteractionManager(application)
    private var chatViewModel: ChatViewModel? = null

    private var chatId: Long = -1L
    private var aiConfig: AiChatConfig? = null

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _rmsValue = MutableStateFlow(0f)
    val rmsValue: StateFlow<Float> = _rmsValue.asStateFlow()

    private val _userText = MutableStateFlow("")
    val userText: StateFlow<String> = _userText.asStateFlow()

    private val _aiText = MutableStateFlow("")
    val aiText: StateFlow<String> = _aiText.asStateFlow()

    // 暴露 AI 配置给 UI 获取头像和名字
    private val _currentAiConfig = MutableStateFlow<AiChatConfig?>(null)
    val currentAiConfig: StateFlow<AiChatConfig?> = _currentAiConfig.asStateFlow()

    // 是否处于暂停状态
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    // 是否自动朗读AI的回复
    private val _isAutoRead = MutableStateFlow(true)
    val isAutoRead: StateFlow<Boolean> = _isAutoRead.asStateFlow()

    private var thinkingJob: Job? = null
    private var aiStateJob: Job? = null

    init {
        setupVoiceManager()
    }

    fun initData(chatId: Long, aiConfig: AiChatConfig, chatViewModel: ChatViewModel) {
        this.chatId = chatId
        this.aiConfig = aiConfig
        this._currentAiConfig.value = aiConfig
        this.chatViewModel = chatViewModel
    }

    private fun setupVoiceManager() {
        voiceManager.onPartialResult = { partial ->
            if (_callState.value == CallState.Listening && !_isPaused.value) {
                _userText.value = partial
            }
        }

        voiceManager.onFinalResult = { text ->
            if (_callState.value == CallState.Listening && !_isPaused.value) {
                _userText.value = text
                startThinking()
            }
        }

        voiceManager.onRmsChanged = { rms ->
            _rmsValue.value = rms
        }

        voiceManager.onSpeechEnd = {
            if (_callState.value == CallState.Listening && !_isPaused.value && _userText.value.isNotEmpty()) {
                startThinking()
            }
        }

        voiceManager.onError = { error ->
            Log.e(TAG, "ASR Error: \$error")
            _callState.value = CallState.Idle
            _aiText.value = "识别出错，请重试..."
            viewModelScope.launch {
                delay(2000)
                 _aiText.value = ""
                 _userText.value = ""
                 if (!_isPaused.value) {
                     startListening()
                 }
            }
        }

        voiceManager.onTtsDone = {
            Log.d(TAG, "TTS done, back to listening")
            viewModelScope.launch {
                delay(500)
                if (_callState.value == CallState.Speaking && !_isPaused.value) {
                    startListening()
                }
            }
        }
    }

    fun startCall() {
        if (_isPaused.value) return 
        if (_callState.value == CallState.Idle) {
            startListening()
        }
    }

    fun toggleCall() {
        if (_isPaused.value) return 
        when (_callState.value) {
            CallState.Idle -> startListening()
            else -> endCall()
        }
    }

    fun toggleAutoRead() {
        _isAutoRead.value = !_isAutoRead.value
    }

    fun togglePause() {
        if (_isPaused.value) {
            _isPaused.value = false
            if (_callState.value == CallState.Idle || _callState.value == CallState.Listening) {
                startListening()
            }
        } else {
            _isPaused.value = true
            voiceManager.stopListening()
            voiceManager.stopSpeaking()
            thinkingJob?.cancel()
            aiStateJob?.cancel()
            _callState.value = CallState.Idle
        }
    }

    private fun startListening() {
        if (_isPaused.value) return
        _callState.value = CallState.Listening
        _userText.value = ""
        _aiText.value = ""
        voiceManager.startListening()
    }

    private fun startThinking() {
        if (_isPaused.value) return
        _callState.value = CallState.Thinking
        voiceManager.stopListening()
        
        thinkingJob?.cancel()
        aiStateJob?.cancel()
        
        val userSpoken = _userText.value
        val aiList = aiConfig?.let { listOf(it) } ?: emptyList()

        if (aiList.isEmpty() || userSpoken.isBlank()) {
            startListening()
            return
        }

        _aiText.value = "AI 思考中..."

        thinkingJob = viewModelScope.launch {
            // 真正发至大模型，语音通话时强制静默确认工具
            chatViewModel?.sendMessage(chatId, userSpoken, aiList, autoConfirmToolsOverride = true)
        }
        
        var lastPartial = ""
        var hasStarted = false
        aiStateJob = viewModelScope.launch {
            chatViewModel?.aiState?.collect { state ->
                when (state) {
                    is AiState.Loading -> {
                        hasStarted = true
                        _callState.value = CallState.Thinking
                        _aiText.value = "AI 思考中..."
                    }
                    is AiState.Streaming -> {
                        hasStarted = true
                        _callState.value = CallState.Thinking
                        
                        var displayContent = state.partialContent
                        // 过滤未闭合或已闭合的 think 标签，使其不在屏幕上输出思维链内容
                        displayContent = displayContent.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
                        displayContent = displayContent.replace(Regex("<think>[\\s\\S]*"), "").trim()

                        if (displayContent.isNotEmpty()) {
                            _aiText.value = displayContent
                        } else if (!state.partialReasoning.isNullOrEmpty() || state.partialContent.contains("<think>")) {
                            _aiText.value = "深度思考中..."
                        } else {
                            _aiText.value = "AI 思考中..."
                        }
                        
                        lastPartial = displayContent
                    }
                    is AiState.Idle -> {
                        if (!hasStarted) return@collect // 忽略掉初始化的空闲状态

                        viewModelScope.launch {
                            var finalSpeakText = lastPartial

                            if (finalSpeakText.isEmpty()) {
                                // 等待一小段时间，确保数据库最新插入的消息已经通过 Flow 刷新过来
                                kotlinx.coroutines.delay(800)
                                val messages = chatViewModel?.getMessages(chatId)?.firstOrNull()
                                val lastMsg = messages?.lastOrNull()
                                
                                if (lastMsg != null && lastMsg.role == "assistant") {
                                    var text = lastMsg.content
                                    val aiName = aiConfig?.name ?: ""
                                    val prefixRegex = Regex("^(?:(?:(?i)${Regex.escape(aiName)})|(?i)ai)[:：]\\s*")
                                    while (prefixRegex.containsMatchIn(text)) {
                                        text = text.replaceFirst(prefixRegex, "")
                                    }
                                    val timePrefixRegex = Regex("^\\[时间:\\s*\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}\\]\\s*")
                                    while (timePrefixRegex.containsMatchIn(text)) {
                                        text = text.replaceFirst(timePrefixRegex, "").trimStart()
                                    }
                                    text = text.replace(Regex("<think>[\\\\s\\\\S]*?</think>"), "").trim()
                                    finalSpeakText = text
                                }
                            }

                            // 无论来自 lastPartial 还是 DB，朗读前都清洗掉可能包含的思想标签和Markdown导致发音异常
                            finalSpeakText = finalSpeakText.replace(Regex("<think>[\\\\s\\\\S]*?</think>"), "")
                            finalSpeakText = finalSpeakText.replace(Regex("<think>[\\\\s\\\\S]*"), "") // 过滤可能未闭合的标签
                            finalSpeakText = finalSpeakText.replace(Regex("[*#`]"), "").trim() // 过滤会让TTS磕巴的Markdown

                            if (finalSpeakText.isNotEmpty()) {
                                if (_isAutoRead.value) {
                                    startSpeaking(finalSpeakText)
                                } else {
                                    _aiText.value = finalSpeakText
                                    _callState.value = CallState.Speaking
                                    // 根据回复长度动态延迟，确保用户有时间阅读（每字200ms，最少3秒，最多15秒）
                                    val readDelay = (finalSpeakText.length * 200L).coerceIn(3000L, 15000L)
                                    delay(readDelay)
                                    // 模拟说话结束，继续监听
                                    if (!_isPaused.value) {
                                        startListening()
                                    }
                                }
                            } else {
                                // 如果无法获取文本，直接继续听
                                if (!_isPaused.value) {
                                    startListening()
                                }
                            }
                            
                            lastPartial = "" // 防止重入
                            hasStarted = false
                            aiStateJob?.cancel() // 当次回复已完成，停止监听直至下次重新触发
                        }
                    }
                    is AiState.Error -> {
                        if (!hasStarted) return@collect
                        
                        _aiText.value = state.message
                        delay(3000)
                        if (!_isPaused.value) {
                            startListening()
                        }
                        aiStateJob?.cancel()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startSpeaking(text: String) {
        if (_isPaused.value) return
        _callState.value = CallState.Speaking
        _aiText.value = text // 显示完整的 AI 回复
        voiceManager.speak(text)
    }

    fun interruptAndListen() {
        if (_callState.value == CallState.Speaking || _callState.value == CallState.Thinking) {
            voiceManager.stopSpeaking()
            aiStateJob?.cancel()
            thinkingJob?.cancel()
            startListening()
        }
    }

    fun endCall() {
        _callState.value = CallState.Idle
        _userText.value = ""
        _aiText.value = ""
        voiceManager.destroy()
        aiStateJob?.cancel()
        thinkingJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
    }
}
