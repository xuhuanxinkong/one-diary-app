package com.xinkong.diary.rag.vector

import android.util.Log
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
    
    private val tag = "ObjectBoxVectorStore"
    private val box get() = ObjectBox.vectorBox()
    
    override suspend fun insert(embedding: FloatArray): Long = withContext(Dispatchers.IO) {
        val entity = VectorEntity(embedding = embedding)
        box.put(entity)
        Log.d(tag, "insert vector: id=${entity.id} dim=${embedding.size}")
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
        Log.d(tag, "search vector: dim=${query.size} topK=$topK")
        val results = box.query(
            VectorEntity_.embedding.nearestNeighbors(query, topK)
        ).build().findWithScores()
        Log.d(tag, "search result count=${results.size}")
        
        results.map { scoredResult ->
            val distance = scoredResult.score.toFloat()
            // ObjectBox 返回的是距离（越小越近），这里转成越大越相似的分数。
            val similarity = 1f / (1f + distance)
            VectorSearchResult(
                vectorId = scoredResult.get().id,
                score = similarity
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
