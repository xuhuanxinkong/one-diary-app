package com.xinkong.diary.rag.embedding

/**
 * 文本分块器
 * 将长文本分割成适合 embedding 的小块
 */
object TextChunker {
    
    /**
     * 默认配置
     */
    const val DEFAULT_CHUNK_SIZE = 256      // 每块最大字符数
    const val DEFAULT_OVERLAP = 32          // 块之间的重叠字符数
    
    /**
     * 分块结果
     */
    data class Chunk(
        val text: String,
        val startIndex: Int,
        val endIndex: Int,
        val chunkIndex: Int
    )
    
    /**
     * 将文本分割成块
     * @param text 原始文本
     * @param chunkSize 每块最大字符数
     * @param overlap 块之间的重叠字符数
     * @return 分块列表
     */
    fun chunk(
        text: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_OVERLAP
    ): List<Chunk> {
        if (text.isEmpty()) return emptyList()
        if (text.length <= chunkSize) {
            return listOf(Chunk(text, 0, text.length, 0))
        }
        
        val chunks = mutableListOf<Chunk>()
        var start = 0
        var chunkIndex = 0
        
        while (start < text.length) {
            var end = minOf(start + chunkSize, text.length)
            
            // 尝试在句子边界处切分
            if (end < text.length) {
                val lastSentenceEnd = findLastSentenceEnd(text, start, end)
                if (lastSentenceEnd > start) {
                    end = lastSentenceEnd
                }
            }
            
            val chunkText = text.substring(start, end).trim()
            if (chunkText.isNotEmpty()) {
                chunks.add(Chunk(chunkText, start, end, chunkIndex++))
            }
            
            // 计算下一块的起始位置（考虑重叠）
            start = end - overlap
            if (start >= text.length || start <= chunks.lastOrNull()?.startIndex ?: -1) {
                break
            }
        }
        
        return chunks
    }
    
    /**
     * 智能分块：基于语义单元（段落、句子）
     */
    fun chunkSmart(text: String, maxChunkSize: Int = DEFAULT_CHUNK_SIZE): List<Chunk> {
        if (text.isEmpty()) return emptyList()
        
        // 先按段落分割
        val paragraphs = text.split(Regex("\n\\s*\n"))
        
        val chunks = mutableListOf<Chunk>()
        var currentChunk = StringBuilder()
        var currentStart = 0
        var chunkIndex = 0
        var textPosition = 0
        
        for (paragraph in paragraphs) {
            val trimmedParagraph = paragraph.trim()
            if (trimmedParagraph.isEmpty()) {
                textPosition += paragraph.length + 2 // +2 for \n\n
                continue
            }
            
            // 如果当前段落太长，需要进一步分割
            if (trimmedParagraph.length > maxChunkSize) {
                // 先保存当前累积的内容
                if (currentChunk.isNotEmpty()) {
                    chunks.add(Chunk(currentChunk.toString().trim(), currentStart, textPosition, chunkIndex++))
                    currentChunk.clear()
                }
                
                // 分割长段落
                val subChunks = chunk(trimmedParagraph, maxChunkSize, DEFAULT_OVERLAP)
                for (subChunk in subChunks) {
                    chunks.add(Chunk(
                        subChunk.text,
                        textPosition + subChunk.startIndex,
                        textPosition + subChunk.endIndex,
                        chunkIndex++
                    ))
                }
                
                currentStart = textPosition + trimmedParagraph.length
            } else if (currentChunk.length + trimmedParagraph.length + 1 > maxChunkSize) {
                // 当前块已满，保存并开始新块
                if (currentChunk.isNotEmpty()) {
                    chunks.add(Chunk(currentChunk.toString().trim(), currentStart, textPosition, chunkIndex++))
                }
                currentChunk.clear()
                currentChunk.append(trimmedParagraph)
                currentStart = textPosition
            } else {
                // 添加到当前块
                if (currentChunk.isNotEmpty()) {
                    currentChunk.append("\n")
                }
                currentChunk.append(trimmedParagraph)
            }
            
            textPosition += paragraph.length + 2
        }
        
        // 保存最后一块
        if (currentChunk.isNotEmpty()) {
            chunks.add(Chunk(currentChunk.toString().trim(), currentStart, textPosition, chunkIndex))
        }
        
        return chunks
    }
    
    /**
     * 查找最后一个句子结束位置
     */
    private fun findLastSentenceEnd(text: String, start: Int, end: Int): Int {
        val sentenceEnds = listOf('。', '！', '？', '；', '.', '!', '?', ';', '\n')
        
        for (i in end - 1 downTo start) {
            if (text[i] in sentenceEnds) {
                return i + 1
            }
        }
        
        // 没找到句子边界，尝试在空格处切分
        for (i in end - 1 downTo start) {
            if (text[i].isWhitespace()) {
                return i + 1
            }
        }
        
        return end
    }
}
