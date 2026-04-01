package com.xinkong.diary.repository

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 存储文本块与向量的映射关系
 * 实际向量存储在 ObjectBox 中，这里只存元数据
 */
@Entity(
    tableName = "embedding_records",
    indices = [
        Index(value = ["sourceType", "sourceId"]),
        Index(value = ["vectorId"], unique = true),
        Index(value = ["folder"])
    ]
)
data class EmbeddingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 来源类型: "diary" 或 "chat_message" */
    val sourceType: String,
    
    /** 来源ID: Diary.id 或 ChatMessage.id */
    val sourceId: Long,
    
    /** 分块索引，同一来源可能有多个分块 */
    val chunkIndex: Int = 0,
    
    /** 分块文本内容（用于展示和调试） */
    val textChunk: String,
    
    /** ObjectBox 中的向量记录ID */
    val vectorId: Long,
    
    /** 笔记所属文件夹（用于按文件夹过滤检索） */
    val folder: String = "",
    
    /** 创建时间戳 */
    val createdAt: Long = System.currentTimeMillis()
)
