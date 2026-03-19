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

sealed interface ToolTask {
    val toolCall: AiToolCall
    val title: String
    val description: String

    data class ReadNotes(
        override val toolCall: AiToolCall,
        val keyword: String,
        val limit: Int
    ) : ToolTask {
        override val title = "允许读取笔记？"
        override val description = "AI 请求读取本地笔记（关键词：$keyword，最多 $limit 条）。是否允许本次读取？"
    }
}