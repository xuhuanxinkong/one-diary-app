package com.xinkong.diary.rag.search

import android.content.Context
import com.xinkong.diary.rag.embedding.EmbeddingManager
import com.xinkong.diary.rag.index.IndexManager
import com.xinkong.diary.rag.vector.ObjectBoxVectorStore
import com.xinkong.diary.repository.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RAG 检索服务
 * 提供语义搜索和混合检索功能
 */
class RAGSearchService private constructor(
    private val context: Context
) {
    private val embeddingManager = EmbeddingManager.getInstance(context)
    private val vectorStore = ObjectBoxVectorStore()
    private val db = AppDatabase.getDatabase(context)
    private val embeddingDao = db.embeddingDao()
    private val diaryDao = db.diaryDao()
    private val chatDao = db.chatDao()
    
    companion object {
        @Volatile
        private var INSTANCE: RAGSearchService? = null
        
        fun getInstance(context: Context): RAGSearchService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RAGSearchService(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    /**
     * 语义搜索
     * @param query 查询文本
     * @param options 搜索选项
     * @return 搜索结果列表，按相似度降序
     */
    suspend fun search(
        query: String,
        options: SearchOptions = SearchOptions()
    ): List<RAGSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        // 生成查询向量
        val queryEmbedding = embeddingManager.embed(query)
        
        // 向量搜索
        val vectorResults = vectorStore.search(queryEmbedding, options.topK * 2)
        
        // 获取对应的 EmbeddingRecord（支持文件夹过滤）
        val vectorIds = vectorResults.map { it.vectorId }
        val records = if (options.folders != null && options.folders.isNotEmpty()) {
            embeddingDao.getByVectorIdsAndFolders(vectorIds, options.folders)
        } else {
            embeddingDao.getByVectorIds(vectorIds)
        }
        val recordMap = records.associateBy { it.vectorId }
        
        // 构建结果
        val results = mutableListOf<RAGSearchResult>()
        
        for (vectorResult in vectorResults) {
            val record = recordMap[vectorResult.vectorId] ?: continue
            
            // 过滤最小分数
            if (vectorResult.score < options.minScore) continue
            
            when (record.sourceType) {
                IndexManager.SOURCE_TYPE_DIARY -> {
                    if (!options.searchDiaries) continue
                    if (options.diaryIds != null && record.sourceId !in options.diaryIds) continue
                    
                    val diary = diaryDao.getDiaryById(record.sourceId)
                    if (diary != null) {
                        results.add(
                            RAGSearchResult.DiaryResult(
                                diary = diary,
                                textChunk = record.textChunk,
                                score = vectorResult.score
                            )
                        )
                    }
                }
                IndexManager.SOURCE_TYPE_CHAT_MESSAGE -> {
                    if (!options.searchMessages) continue
                    // TODO: 添加 getMessageById 方法
                }
            }
        }
        
        // 去重（同一个日记可能有多个分块命中）
        val uniqueResults = results
            .groupBy { 
                when (it) {
                    is RAGSearchResult.DiaryResult -> "diary_${it.diary.id}"
                    is RAGSearchResult.ChatMessageResult -> "msg_${it.message.id}"
                }
            }
            .map { (_, group) -> group.maxByOrNull { it.score }!! }
            .sortedByDescending { it.score }
            .take(options.topK)
        
        uniqueResults
    }
    
    /**
     * 混合搜索（向量 + 关键字）
     * 先搜笔记，再搜对话
     */
    suspend fun hybridSearch(
        query: String,
        options: SearchOptions = SearchOptions()
    ): List<RAGSearchResult> = withContext(Dispatchers.IO) {
        val allResults = mutableListOf<RAGSearchResult>()
        
        // 1. 先搜索笔记
        if (options.searchDiaries) {
            val diaryOptions = options.copy(searchMessages = false)
            val diaryResults = search(query, diaryOptions)
            allResults.addAll(diaryResults)
            
            // 关键字补充搜索
            val diaries = diaryDao.searchByKeyword(query, options.topK)
            for (diary in diaries) {
                // 文件夹过滤
                if (options.folders != null && diary.tagFolder !in options.folders) continue
                
                // 检查是否已在向量结果中
                val alreadyExists = allResults.any { 
                    it is RAGSearchResult.DiaryResult && it.diary.id == diary.id 
                }
                if (!alreadyExists) {
                    allResults.add(
                        RAGSearchResult.DiaryResult(
                            diary = diary,
                            textChunk = diary.text.take(200),
                            score = 0.4f
                        )
                    )
                }
            }
        }
        
        // 2. 再搜索对话（如果笔记结果不足）
        if (options.searchMessages && allResults.size < options.topK) {
            val messageOptions = options.copy(
                searchDiaries = false,
                topK = options.topK - allResults.size
            )
            val messageResults = search(query, messageOptions)
            allResults.addAll(messageResults)
        }
        
        // 排序并返回
        allResults
            .sortedByDescending { it.score }
            .take(options.topK)
    }
    
    /**
     * 为 AI 对话准备上下文
     * @param query 用户问题
     * @param boundFolder AI 绑定的文件夹（限制搜索范围）
     * @param maxResults 最大结果数
     * @return 格式化的上下文文本
     */
    suspend fun prepareContext(
        query: String,
        boundFolder: String? = null,
        maxResults: Int = 5,
        maxTotalLength: Int = 1000
    ): String = withContext(Dispatchers.IO) {
        val folders = if (!boundFolder.isNullOrBlank()) listOf(boundFolder) else null
        
        val options = SearchOptions(
            topK = maxResults,
            minScore = 0.5f,
            searchDiaries = true,
            searchMessages = true,  // 也搜索对话历史
            folders = folders
        )
        
        val results = hybridSearch(query, options)
        
        if (results.isEmpty()) return@withContext ""
        
        val fullText = buildString {
            append("【相关参考】")
            
            for ((index, result) in results.withIndex()) {
                when (result) {
                    is RAGSearchResult.DiaryResult -> {
                        append("${index + 1}.")
                        if (result.diary.title.isNotBlank()) {
                            append("${result.diary.title}：")
                        }
                        append(result.textChunk.take(200))
                        append(" ")
                    }
                    is RAGSearchResult.ChatMessageResult -> {
                        append("${index + 1}.[对话]${result.textChunk.take(150)} ")
                    }
                }
            }
        }
        
        if (fullText.length > maxTotalLength) {
            fullText.take(maxTotalLength) + "..."
        } else fullText
    }
}
