package com.xinkong.diary.ui.screen.chat.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.repository.AiChatConfig
import kotlinx.coroutines.flow.StateFlow

sealed class CallState {
    object Idle : CallState()
    object Listening : CallState()
    object Thinking : CallState()
    object Speaking : CallState()
}

class CallViewModel(application: Application) : AndroidViewModel(application) {
    val callState: StateFlow<CallState> = CallManager.callState
    val rmsValue: StateFlow<Float> = CallManager.rmsValue
    val userText: StateFlow<String> = CallManager.userText
    val aiText: StateFlow<String> = CallManager.aiText
    val currentAiConfig: StateFlow<AiChatConfig?> = CallManager.currentAiConfig
    val isPaused: StateFlow<Boolean> = CallManager.isPaused
    val isAutoRead: StateFlow<Boolean> = CallManager.isAutoRead

    fun initData(chatId: Long, aiConfig: AiChatConfig, chatViewModel: ChatViewModel) {
        CallManager.initData(getApplication(), chatId, aiConfig, chatViewModel)
    }

    fun startCall() {
        CallManager.startCall()
    }

    fun toggleCall() {
        CallManager.toggleCall()
    }

    fun toggleAutoRead() {
        CallManager.toggleAutoRead()
    }

    fun togglePause() {
        CallManager.togglePause()
    }

    fun interruptAndListen() {
        CallManager.interruptAndListen()
    }

    fun endCall() {
        CallManager.endCall()
    }

    override fun onCleared() {
        super.onCleared()
        // Intentionally keep call state in CallManager so floating call can survive UI lifecycle.
    }
}
