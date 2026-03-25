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
        override val title = "读取笔记：$keyword"
        override val description = "AI 请求读取本地笔记（关键词：$keyword，最多 $limit 条）。是否允许本次读取？"
    }

    data class WriteNote(
        override val toolCall: AiToolCall,
        val noteTitle: String,
        val content: String,
        val folder: String,
        val tag: String
    ) : ToolTask {
        override val title: String = "新增笔记：$noteTitle"
        override val description = "AI 请求新增笔记《$noteTitle》。"
    }

    data class EditNote(
        override val toolCall: AiToolCall,
        val id: Long,
        val noteTitle: String?,
        val content: String?,
        val folder: String?,
        val tag: String?
    ) : ToolTask {
        override val title: String = "修改笔记 ID:$id"
        override val description = "AI 请求修改你的笔记 (ID: $id)"
    }

    data class GetTagsAndFolders(
        override val toolCall: AiToolCall
    ) : ToolTask {
        override val title: String = "读取分类与标签"
        override val description = "AI 请求读取所有日记的分类与标签"
    }

    data class QueryChatHistory(
        override val toolCall: AiToolCall,
        val keyword: String,
        val limit: Int
    ) : ToolTask {
        override val title: String = "查询对话记录"
        override val description: String = "AI 正在自动查询历史对话：$keyword"
    }
}