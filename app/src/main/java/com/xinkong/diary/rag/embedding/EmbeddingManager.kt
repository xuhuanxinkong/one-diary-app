package com.xinkong.diary.rag.embedding

import android.content.Context
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
                model = OnnxEmbeddingModel.fromAssets(context)
                isInitialized = true
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
        model!!.embed(text)
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
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
        }
        // 因为向量已经 L2 归一化，所以 dot product 就是余弦相似度
        return dotProduct
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
