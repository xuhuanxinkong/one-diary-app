package com.xinkong.diary.rag.search

import com.xinkong.diary.repository.ChatMessage
import com.xinkong.diary.repository.Diary

/**
 * RAG 搜索结果
 */
sealed class RAGSearchResult {
    abstract val score: Float
    abstract val textChunk: String
    
    data class DiaryResult(
        val diary: Diary,
        override val textChunk: String,
        override val score: Float
    ) : RAGSearchResult()
    
    data class ChatMessageResult(
        val message: ChatMessage,
        override val textChunk: String,
        override val score: Float
    ) : RAGSearchResult()
}

/**
 * 搜索选项
 */
data class SearchOptions(
    val topK: Int = 10,
    val minScore: Float = 0.5f,          // 最小相似度阈值
    val searchDiaries: Boolean = true,
    val searchMessages: Boolean = true,
    val diaryIds: List<Long>? = null,    // 限定搜索的日记ID
    val chatIds: List<Long>? = null,     // 限定搜索的对话ID
    val folders: List<String>? = null    // 限定搜索的文件夹（为空表示不限制）
)
