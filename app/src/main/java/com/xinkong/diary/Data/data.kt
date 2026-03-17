package com.xinkong.diary.Data


sealed class AiState{
    object Idle: AiState()
    object Loading: AiState()
    data class Success(val result:String): AiState()
    data class Error(val message: String): AiState()
}

sealed class AiResponse {
    data class Message(
        val content: String,
        val toolCalls: List<AiToolCall>
    ) : AiResponse()
}

data class AiToolCall(
    val id: String,
    val type: String,
    val functionName: String,
    val arguments: String
)