package com.xinkong.diary.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class NoteContextUseCase {

    /**
     * Enum for Chunk size logic
     */
    enum class PreprocessStrategy {
        DIRECT,       // 短篇直接处理
        SUMMARIZED,   // 中篇先提取摘要再处理
        ITERATIVE     // 超长篇迭代生成片段
    }

    /**
     * 根据笔记的总字数给出拆分策略的样例
     */
    fun determineStrategy(notesBody: String): PreprocessStrategy {
        val wordCount = notesBody.length
        return when {
            wordCount < 1000 -> PreprocessStrategy.DIRECT
            wordCount in 1000..5000 -> PreprocessStrategy.SUMMARIZED
            else -> PreprocessStrategy.ITERATIVE // Token超过限制时的长篇
        }
    }

    /**
     * 针对分段式生成的伪代码
     */
    fun processByChunks(notesBody: String, chunkSize: Int = 2000): Flow<String> = flow {
        // 根据Chunk Size切片日记
        val chunks = notesBody.chunked(chunkSize)
        for ((index, chunk) in chunks.withIndex()) {
            val partialContext = "这是第 ${index + 1}/${chunks.size} 段笔记:\n$chunk"
            // TODO: 调用AI发送 partialContext，并将回复组合在一起。
            // 每次生成都作为一个中间状态 emit(partialResponse)
            emit("处理了分块 ${index + 1} 的摘要...")
        }
    }
}