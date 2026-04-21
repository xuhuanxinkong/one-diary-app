package com.xinkong.diary.rag.embedding

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Embedding 管理器
 * 提供懒加载、线程安全的 embedding 生成服务
 */
class EmbeddingManager private constructor(
    private val context: Context
) {
    private val tag = "EmbeddingManager"
    private var model: OnnxEmbeddingModel? = null
    private val mutex = Mutex()
    private var isInitialized = false
    
    companion object {
        @Volatile
        private var INSTANCE: EmbeddingManager? = null
        
        fun getInstance(context: Context): EmbeddingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EmbeddingManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    /**
     * 初始化模型（懒加载）
     * 建议在后台线程调用
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isInitialized) {
                Log.i(tag, "开始初始化 embedding 模型")
                model = OnnxEmbeddingModel.fromAssets(context)
                isInitialized = true
                Log.i(tag, "embedding 模型初始化完成")
            }
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * 生成文本的 embedding
     * 如果模型未初始化，会自动初始化
     */
    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            initialize()
        }
        Log.d(tag, "生成文档向量: len=${text.length}")
        model!!.embed(text)
    }

    /**
     * 生成查询向量
     */
    suspend fun embedQuery(text: String): FloatArray = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            initialize()
        }
        Log.d(tag, "生成查询向量: len=${text.length}")
        model!!.embedQuery(text)
    }
    
    /**
     * 批量生成 embedding
     */
    suspend fun embedBatch(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            initialize()
        }
        model!!.embedBatch(texts)
    }
    
    /**
     * 计算两个文本的相似度
     * @return 余弦相似度 [-1, 1]，越接近 1 越相似
     */
    suspend fun similarity(text1: String, text2: String): Float {
        val emb1 = embed(text1)
        val emb2 = embed(text2)
        return cosineSimilarity(emb1, emb2)
    }
    
    /**
     * 计算余弦相似度
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "向量维度必须相同" }
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }
    
    /**
     * 释放模型资源
     */
    fun release() {
        model?.close()
        model = null
        isInitialized = false
    }
}
