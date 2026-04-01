package com.xinkong.diary.rag.vector

import com.xinkong.diary.repository.ObjectBox
import com.xinkong.diary.repository.VectorEntity
import com.xinkong.diary.repository.VectorEntity_
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 ObjectBox 的向量存储实现
 * 使用 HNSW 索引进行高效的近似最近邻搜索
 */
class ObjectBoxVectorStore : VectorStore {
    
    private val box get() = ObjectBox.vectorBox()
    
    override suspend fun insert(embedding: FloatArray): Long = withContext(Dispatchers.IO) {
        val entity = VectorEntity(embedding = embedding)
        box.put(entity)
        entity.id
    }
    
    override suspend fun insertBatch(embeddings: List<FloatArray>): List<Long> = withContext(Dispatchers.IO) {
        val entities = embeddings.map { VectorEntity(embedding = it) }
        box.put(entities)
        entities.map { it.id }
    }
    
    override suspend fun delete(vectorId: Long) = withContext(Dispatchers.IO) {
        box.remove(vectorId)
        Unit
    }
    
    override suspend fun deleteBatch(vectorIds: List<Long>) = withContext(Dispatchers.IO) {
        box.removeByIds(vectorIds)
        Unit
    }
    
    override suspend fun search(query: FloatArray, topK: Int): List<VectorSearchResult> = withContext(Dispatchers.IO) {
        // 使用 ObjectBox 的向量最近邻查询
        val results = box.query(
            VectorEntity_.embedding.nearestNeighbors(query, topK)
        ).build().findWithScores()
        
        results.map { scoredResult ->
            VectorSearchResult(
                vectorId = scoredResult.get().id,
                score = scoredResult.score.toFloat()
            )
        }
    }
    
    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        box.count()
    }
    
    override suspend fun clear() = withContext(Dispatchers.IO) {
        box.removeAll()
        Unit
    }
    
    /**
     * 根据ID获取向量
     */
    suspend fun getById(vectorId: Long): FloatArray? = withContext(Dispatchers.IO) {
        box.get(vectorId)?.embedding
    }
    
    /**
     * 批量获取向量
     */
    suspend fun getByIds(vectorIds: List<Long>): Map<Long, FloatArray> = withContext(Dispatchers.IO) {
        box.get(vectorIds)
            .filterNotNull()
            .associate { it.id to it.embedding }
    }
}
