package com.xinkong.diary.repository

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

/**
 * ObjectBox 向量实体
 * 使用 HNSW 索引进行高效的近似最近邻搜索
 */
@Entity
data class VectorEntity(
    @Id
    var id: Long = 0,
    
    /**
     * 512维向量 (bge-small-zh-v1.5 输出维度)
     * HNSW 参数说明:
     * - dimensions: 向量维度
     * - neighborsPerNode: 每个节点的邻居数，影响精度和内存
     * - indexingSearchCount: 索引时搜索的候选数
     */
    @HnswIndex(dimensions = 512, neighborsPerNode = 30, indexingSearchCount = 100)
    var embedding: FloatArray = FloatArray(512)
) {
    // ObjectBox 需要无参构造函数
    constructor() : this(0, FloatArray(512))
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VectorEntity
        if (id != other.id) return false
        if (!embedding.contentEquals(other.embedding)) return false
        return true
    }
    
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
