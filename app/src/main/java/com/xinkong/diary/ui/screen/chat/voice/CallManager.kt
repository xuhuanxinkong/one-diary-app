package com.xinkong.diary.ui.screen.chat.voice

import android.app.Application
import android.util.Log
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.data.AiState
import com.xinkong.diary.repository.AiChatConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

object CallManager {
    private const val TAG = "CallManager"

    private var application: Application? = null
    var voiceManager: VoiceInteractionManager? = null
    private var chatViewModel: ChatViewModel? = null

    var chatId: Long = -1L
    var aiConfig: AiChatConfig? = null
    var isGroup: Boolean = false

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _rmsValue = MutableStateFlow(0f)
    val rmsValue: StateFlow<Float> = _rmsValue.asStateFlow()

    private val _userText = MutableStateFlow("")
    val userText: StateFlow<String> = _userText.asStateFlow()

    private val _aiText = MutableStateFlow("")
    val aiText: StateFlow<String> = _aiText.asStateFlow()

    private val _currentAiConfig = MutableStateFlow<AiChatConfig?>(null)
    val currentAiConfig: StateFlow<AiChatConfig?> = _currentAiConfig.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isAutoRead = MutableStateFlow(true)
    val isAutoRead: StateFlow<Boolean> = _isAutoRead.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var thinkingJob: Job? = null
    private var aiStateJob: Job? = null

    fun initData(app: Application, chatId: Long, aiConfig: AiChatConfig, chatViewModel: ChatViewModel, isGroup: Boolean = false) {
        if (this.application == null) {
            this.application = app
        }
        this.chatId = chatId
        this.aiConfig = aiConfig
        this._currentAiConfig.value = aiConfig
        this.chatViewModel = chatViewModel
        this.isGroup = isGroup

        if (voiceManager == null) {
            voiceManager = VoiceInteractionManager(app)
            setupVoiceManager()
        }
    }

    private fun setupVoiceManager() {
        voiceManager?.onPartialResult = { partial ->
            if (_callState.value == CallState.Listening && !_isPaused.value) {
                _userText.value = partial
            }
        }

        voiceManager?.onFinalResult = { text ->
            if (_callState.value == CallState.Listening && !_isPaused.value) {
                _userText.value = text
                startThinking()
            }
        }

        voiceManager?.onRmsChanged = { rms ->
            _rmsValue.value = rms
        }

        voiceManager?.onSpeechEnd = {
            if (_callState.value == CallState.Listening && !_isPaused.value && _userText.value.isNotEmpty()) {
                startThinking()
            }
        }

        voiceManager?.onError = { error ->
            Log.e(TAG, "ASR Error: \$error")
            _callState.value = CallState.Idle
            _aiText.value = "识别出错，请重试..."
            scope.launch {
                delay(2000)
                _aiText.value = ""
                _userText.value = ""
                if (!_isPaused.value) {
                    startListening()
                }
            }
        }

        voiceManager?.onTtsDone = {
            Log.d(TAG, "TTS done, back to listening")
            scope.launch {
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
        if (_isAutoRead.value) {
            voiceManager?.reInitTts()
        } else {
            voiceManager?.stopSpeaking()
        }
    }

    fun togglePause() {
        if (_isPaused.value) {
            _isPaused.value = false
            if (_callState.value == CallState.Idle || _callState.value == CallState.Listening) {
                startListening()
            }
        } else {
            _isPaused.value = true
            voiceManager?.stopListening()
            voiceManager?.stopSpeaking()
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
        voiceManager?.startListening()
    }

    fun sendManualText(text: String) {
        if (_isPaused.value) return
        _userText.value = text
        startThinking()
    }

    private fun startThinking() {
        if (_isPaused.value) return
        _callState.value = CallState.Thinking
        voiceManager?.stopListening()

        thinkingJob?.cancel()
        aiStateJob?.cancel()

        val userSpoken = _userText.value
        val aiList = aiConfig?.let { listOf(it) } ?: emptyList()

        if (aiList.isEmpty() || userSpoken.isBlank()) {
            startListening()
            return
        }

        _aiText.value = "AI 思考中..."

        thinkingJob = scope.launch {
            chatViewModel?.sendMessage(chatId, userSpoken, aiList, autoConfirmToolsOverride = true)
        }

        var lastPartial = ""
        var hasStarted = false
        aiStateJob = scope.launch {
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
                        displayContent = displayContent.replace(Regex("<think>[\\\\s\\\\S]*?</think>"), "").trim()
                        displayContent = displayContent.replace(Regex("<think>[\\\\s\\\\S]*"), "").trim()

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
                        if (!hasStarted) return@collect

                        scope.launch {
                            var finalSpeakText = lastPartial

                            if (finalSpeakText.isEmpty()) {
                                delay(800)
                                val messages = chatViewModel?.getMessages(chatId)?.firstOrNull()
                                val lastMsg = messages?.lastOrNull()

                                if (lastMsg != null && lastMsg.role == "assistant") {
                                    var text = lastMsg.content
                                    val aiName = aiConfig?.name ?: ""
                                    val prefixRegex = Regex("^(?:(?:(?i)\${Regex.escape(aiName)})|(?i)ai)[:：]\\\\s*")
                                    while (prefixRegex.containsMatchIn(text)) {
                                        text = text.replaceFirst(prefixRegex, "")
                                    }
                                    val timePrefixRegex = Regex("^\\\\[时间:\\\\s*\\\\d{4}-\\\\d{2}-\\\\d{2}\\\\s+\\\\d{2}:\\\\d{2}\\\\]\\\\s*")
                                    while (timePrefixRegex.containsMatchIn(text)) {
                                        text = text.replaceFirst(timePrefixRegex, "").trimStart()
                                    }
                                    text = text.replace(Regex("<think>[\\\\s\\\\S]*?</think>"), "").trim()
                                    finalSpeakText = text
                                }
                            }

                            finalSpeakText = finalSpeakText.replace(Regex("<think>[\\\\s\\\\S]*?</think>"), "")
                            finalSpeakText = finalSpeakText.replace(Regex("<think>[\\\\s\\\\S]*"), "")
                            finalSpeakText = finalSpeakText.replace(Regex("[*#`]"), "").trim()

                            if (finalSpeakText.isNotEmpty()) {
                                if (_isAutoRead.value) {
                                    startSpeaking(finalSpeakText)
                                } else {
                                    _aiText.value = finalSpeakText
                                    _callState.value = CallState.Speaking
                                    val readDelay = (finalSpeakText.length * 200L).coerceIn(3000L, 15000L)
                                    delay(readDelay)
                                    if (!_isPaused.value) {
                                        startListening()
                                    }
                                }
                            } else {
                                if (!_isPaused.value) {
                                    startListening()
                                }
                            }

                            lastPartial = ""
                            hasStarted = false
                            aiStateJob?.cancel()
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
        _aiText.value = text
        voiceManager?.speak(text)
    }

    fun interruptAndListen() {
        if (_callState.value == CallState.Speaking || _callState.value == CallState.Thinking) {
            voiceManager?.stopSpeaking()
            aiStateJob?.cancel()
            thinkingJob?.cancel()
            startListening()
        }
    }

    fun endCall() {
        _callState.value = CallState.Idle
        _userText.value = ""
        _aiText.value = ""
        voiceManager?.destroy()
        voiceManager = null
        aiStateJob?.cancel()
        thinkingJob?.cancel()
    }
}
