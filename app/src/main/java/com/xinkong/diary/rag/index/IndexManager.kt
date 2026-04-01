package com.xinkong.diary.rag.index

import android.content.Context
import com.xinkong.diary.rag.embedding.EmbeddingManager
import com.xinkong.diary.rag.embedding.TextChunker
import com.xinkong.diary.rag.vector.ObjectBoxVectorStore
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.ChatMessage
import com.xinkong.diary.repository.Diary
import com.xinkong.diary.repository.EmbeddingRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 索引管理器
 * 负责为 Diary 和 ChatMessage 创建、更新、删除向量索引
 */
class IndexManager private constructor(
    private val context: Context
) {
    private val embeddingManager = EmbeddingManager.getInstance(context)
    private val vectorStore = ObjectBoxVectorStore()
    private val embeddingDao = AppDatabase.getDatabase(context).embeddingDao()
    
    companion object {
        const val SOURCE_TYPE_DIARY = "diary"
        const val SOURCE_TYPE_CHAT_MESSAGE = "chat_message"
        
        @Volatile
        private var INSTANCE: IndexManager? = null
        
        fun getInstance(context: Context): IndexManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IndexManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    /**
     * 为日记创建索引
     */
    suspend fun indexDiary(diary: Diary) = withContext(Dispatchers.IO) {
        // 先删除旧索引
        deleteIndex(SOURCE_TYPE_DIARY, diary.id)
        
        // 合并标题和内容
        val fullText = buildString {
            if (diary.title.isNotBlank()) {
                append(diary.title)
                append("\n")
            }
            if (diary.text.isNotBlank()) {
                append(diary.text)
            }
            if (diary.content.isNotBlank()) {
                append("\n")
                append(diary.content)
            }
        }.trim()
        
        if (fullText.isEmpty()) return@withContext
        
        // 分块
        val chunks = TextChunker.chunkSmart(fullText)
        
        // 为每个块生成 embedding 并存储
        for (chunk in chunks) {
            val embedding = embeddingManager.embed(chunk.text)
            val vectorId = vectorStore.insert(embedding)
            
            embeddingDao.insert(
                EmbeddingRecord(
                    sourceType = SOURCE_TYPE_DIARY,
                    sourceId = diary.id,
                    chunkIndex = chunk.chunkIndex,
                    textChunk = chunk.text,
                    vectorId = vectorId,
                    folder = diary.tagFolder  // 记录笔记所属文件夹
                )
            )
        }
    }
    
    /**
     * 为聊天消息创建索引
     * @param chatFolder 对话所属文件夹（可选）
     */
    suspend fun indexChatMessage(message: ChatMessage, chatFolder: String = "") = withContext(Dispatchers.IO) {
        // 先删除旧索引
        deleteIndex(SOURCE_TYPE_CHAT_MESSAGE, message.id)
        
        val text = message.content.trim()
        if (text.isEmpty()) return@withContext
        
        // 消息通常较短，不需要分块
        val embedding = embeddingManager.embed(text)
        val vectorId = vectorStore.insert(embedding)
        
        embeddingDao.insert(
            EmbeddingRecord(
                sourceType = SOURCE_TYPE_CHAT_MESSAGE,
                sourceId = message.id,
                chunkIndex = 0,
                textChunk = text,
                vectorId = vectorId,
                folder = chatFolder
            )
        )
    }
    
    /**
     * 批量为消息创建索引
     */
    suspend fun indexChatMessages(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        for (message in messages) {
            indexChatMessage(message)
        }
    }
    
    /**
     * 删除索引
     */
    suspend fun deleteIndex(sourceType: String, sourceId: Long) = withContext(Dispatchers.IO) {
        val records = embeddingDao.getBySource(sourceType, sourceId)
        if (records.isNotEmpty()) {
            vectorStore.deleteBatch(records.map { it.vectorId })
            embeddingDao.deleteBySource(sourceType, sourceId)
        }
    }
    
    /**
     * 检查是否已索引
     */
    suspend fun isIndexed(sourceType: String, sourceId: Long): Boolean = withContext(Dispatchers.IO) {
        embeddingDao.getBySource(sourceType, sourceId).isNotEmpty()
    }
    
    /**
     * 获取索引统计
     */
    suspend fun getStats(): IndexStats = withContext(Dispatchers.IO) {
        IndexStats(
            totalVectors = vectorStore.count(),
            diaryCount = embeddingDao.countByType(SOURCE_TYPE_DIARY),
            chatMessageCount = embeddingDao.countByType(SOURCE_TYPE_CHAT_MESSAGE)
        )
    }
    
    /**
     * 清空所有索引
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        vectorStore.clear()
        embeddingDao.deleteAll()
    }
}

data class IndexStats(
    val totalVectors: Long,
    val diaryCount: Int,
    val chatMessageCount: Int
)
