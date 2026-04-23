package com.xinkong.diary.rag

import android.content.Context
import com.xinkong.diary.rag.embedding.EmbeddingManager
import com.xinkong.diary.rag.index.IndexManager
import com.xinkong.diary.rag.search.RAGSearchService
import com.xinkong.diary.rag.search.SearchOptions
import com.xinkong.diary.repository.ChatMessage
import com.xinkong.diary.repository.Diary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RAG 模块统一入口
 * 提供简单的 API 供 ViewModel 调用
 */
object RAG {
    
    private var initialized = false
    
    /**
     * 初始化 RAG 模块
     * 建议在 Application 或首次使用时调用
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        
        // 预加载 embedding 模型
        EmbeddingManager.getInstance(context).initialize()
        initialized = true
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = initialized
    
    /**
     * 为日记创建索引
     */
    suspend fun indexDiary(context: Context, diary: Diary) {
        IndexManager.getInstance(context).indexDiary(diary)
    }
    
    /**
     * 为聊天消息创建索引
     */
    suspend fun indexMessage(context: Context, message: ChatMessage, chatFolder: String = "") {
        IndexManager.getInstance(context).indexChatMessage(message, chatFolder)
    }
    
    /**
     * 删除日记索引
     */
    suspend fun deleteIndex(context: Context, sourceType: String, sourceId: Long) {
        IndexManager.getInstance(context).deleteIndex(sourceType, sourceId)
    }
    
    /**
     * 搜索相关内容
     */
    suspend fun search(
        context: Context,
        query: String,
        topK: Int = 5,
        minScore: Float = 0.5f
    ) = RAGSearchService.getInstance(context).search(
        query,
        SearchOptions(topK = topK, minScore = minScore)
    )
    
    /**
     * 为 AI 对话准备上下文
     * @param context Android Context
     * @param query 用户问题
     * @param boundFolder AI 绑定的文件夹（限制搜索范围，为空表示不限制）
     * @param maxResults 最大结果数
     * @return 格式化的上下文文本
     */
    suspend fun prepareContext(
        context: Context,
        query: String,
        boundFolder: String? = null,
        maxResults: Int = 5,
        maxTotalLength: Int = 1000
    ): String {
        if (!initialized) {
            // 未初始化时返回空，不阻塞对话
            return ""
        }
        
        return try {
            RAGSearchService.getInstance(context).prepareContext(
                query = query,
                boundFolder = boundFolder,
                maxResults = maxResults,
                maxTotalLength = maxTotalLength
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    /**
     * 获取索引统计
     */
    suspend fun getStats(context: Context) = 
        IndexManager.getInstance(context).getStats()
    
    /**
     * 清空所有索引
     */
    suspend fun clearAll(context: Context) {
        IndexManager.getInstance(context).clearAll()
    }
}
