package com.xinkong.diary.data


sealed class AiState{
    object Idle: AiState()
    object Loading: AiState()
    data class Streaming(val partialContent: String): AiState()
    data class Success(val result:String): AiState()
    data class Error(val message: String): AiState()
}

sealed class AiResponse {
    data class Message(
        val content: String,
        val toolCalls: List<AiToolCall>
    ) : AiResponse()

    sealed class StreamChunk {
        data class Content(val text: String) : StreamChunk()
        data class ToolCalls(val toolCalls: List<AiToolCall>) : StreamChunk()
        object End : StreamChunk()
    }
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
        val tag: String?
    ) : ToolTask {
        override val title: String = "修改笔记 ID:$id;标题：$noteTitle"
        override val description = "AI 请求修改你的笔记 (ID: $id;标题：$noteTitle)"
    }

    data class NoteOperation(
        val op: String,  // "set_title", "append", "replace"
        val value: String? = null,
        val old: String? = null,
        val new: String? = null
    )

    data class BatchEditNote(
        override val toolCall: AiToolCall,
        val id: Long,
        val operations: List<NoteOperation>
    ) : ToolTask {
        override val title: String = "批量编辑笔记 ID:$id"
        override val description: String = buildString {
            append("AI 请求批量编辑笔记：")
            operations.forEach { op ->
                when (op.op) {
                    "set_title" -> append("改标题「${op.value}」; ")
                    "append" -> append("追加内容; ")
                    "replace" -> append("替换「${op.old?.take(15)}...」; ")
                }
            }
        }
    }

    data class ListFolderNotes(
        override val toolCall: AiToolCall,
        val folder: String
    ) : ToolTask {
        override val title: String = "查看记忆库笔记列表"
        override val description = "AI 请求查看记忆库「$folder」中的所有笔记"
    }

    data class QueryChatHistory(
        override val toolCall: AiToolCall,
        val keyword: String,
        val limit: Int,
        val startDate: String? = null,
        val endDate: String? = null
    ) : ToolTask {
        override val title: String = "查询对话记录"
        override val description: String = "AI 正在自动查询历史对话：$keyword"
    }

    data class WebSearchBaidu(
        override val toolCall: AiToolCall,
        val keyword: String
    ) : ToolTask {
        override val title: String = "网络搜索：$keyword"
        override val description: String = "AI 请求通过百度智能云进行网络搜索：$keyword"
    }

    data class SetAlarm(
        override val toolCall: AiToolCall,
        val name: String,
        val dateTime: String,  // 格式: yyyy-MM-dd HH:mm
        val taskPrompt: String  // AI任务提示词
    ) : ToolTask {
        override val title: String = "设置AI任务：$name"
        override val description: String = "AI 请求设置AI任务「$name」，时间：$dateTime，任务：$taskPrompt"
    }
}