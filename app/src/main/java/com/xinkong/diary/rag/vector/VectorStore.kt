package com.xinkong.diary.rag.vector

/**
 * 向量搜索结果
 */
data class VectorSearchResult(
    val vectorId: Long,
    val score: Float  // 相似度分数，越高越相似
)

/**
 * 向量存储接口
 */
interface VectorStore {
    /**
     * 插入向量
     * @return 向量ID
     */
    suspend fun insert(embedding: FloatArray): Long
    
    /**
     * 批量插入向量
     * @return 向量ID列表
     */
    suspend fun insertBatch(embeddings: List<FloatArray>): List<Long>
    
    /**
     * 删除向量
     */
    suspend fun delete(vectorId: Long)
    
    /**
     * 批量删除向量
     */
    suspend fun deleteBatch(vectorIds: List<Long>)
    
    /**
     * 相似度搜索
     * @param query 查询向量
     * @param topK 返回前K个结果
     * @return 搜索结果列表，按相似度降序
     */
    suspend fun search(query: FloatArray, topK: Int = 10): List<VectorSearchResult>
    
    /**
     * 获取向量总数
     */
    suspend fun count(): Long
    
    /**
     * 清空所有向量
     */
    suspend fun clear()
}
