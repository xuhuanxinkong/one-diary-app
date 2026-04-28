package com.xinkong.diary.data


sealed class AiState{
    object Idle: AiState()
    object Loading: AiState()
    data class Streaming(val partialContent: String, val partialReasoning: String? = null): AiState()
    data class Success(val result:String): AiState()
    data class Error(val message: String): AiState()
}

sealed class AiResponse {
    data class Message(
        val content: String,
        val toolCalls: List<AiToolCall>,
        val reasoningContent: String? = null
    ) : AiResponse()

    sealed class StreamChunk {
        data class Content(val text: String) : StreamChunk()
        data class ReasoningContent(val text: String) : StreamChunk()
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

    data class SetTag(
        override val toolCall: AiToolCall,
        val id: Long,
        val tag: String
    ) : ToolTask {
        override val title: String = "设置笔记标签 ID:$id -> $tag"
        override val description: String = "AI 请求设置笔记标签 (ID: $id, 新标签: $tag)"
    }

    data class GetNotesList(
        override val toolCall: AiToolCall,
        val keyword: String,
        val limit: Int
    ) : ToolTask {
        override val title: String = "获取笔记列表"
        override val description: String = "AI 请求获取笔记列表（关键词：$keyword，最多 $limit 条）。"
    }

    data class RagSearch(
        override val toolCall: AiToolCall,
        val keyword: String,
        val limit: Int
    ) : ToolTask {
        override val title: String = "RAG检索：$keyword"
        override val description: String = "AI 请求执行 RAG 检索（关键词：$keyword，最多 $limit 条）。"
    }

    data class PauseAndDecide(
        override val toolCall: AiToolCall,
        val reason: String?
    ) : ToolTask {
        override val title: String = "暂停并决策"
        override val description: String = "AI 请求暂停当前批量输出并读取最新运行环境数据后继续决策。"
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
        val taskPrompt: String,  // AI任务提示词
        val repeatDays: List<Int> = emptyList()  // 重复日期 1-7 对应周一到周日
    ) : ToolTask {
        override val title: String = "制定计划：$name"
        override val description: String = buildString {
            append("AI 请求制定计划「$name」，时间：$dateTime")
            if (repeatDays.isNotEmpty()) {
                val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
                append("，重复：${repeatDays.mapNotNull { dayNames.getOrNull(it) }.joinToString(",")}")
            }
            append("，计划内容：$taskPrompt")
        }
    }

    data class CancelAlarm(
        override val toolCall: AiToolCall,
        val alarmId: Int
    ) : ToolTask {
        override val title: String = "取消计划：ID $alarmId"
        override val description: String = "AI 请求取消计划 (ID: $alarmId)"
    }
}