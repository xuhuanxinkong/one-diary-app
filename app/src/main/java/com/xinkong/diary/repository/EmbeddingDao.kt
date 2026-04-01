package com.xinkong.diary.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EmbeddingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: EmbeddingRecord): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<EmbeddingRecord>)
    
    @Delete
    suspend fun delete(record: EmbeddingRecord)
    
    @Query("DELETE FROM embedding_records WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deleteBySource(sourceType: String, sourceId: Long)
    
    @Query("DELETE FROM embedding_records WHERE vectorId = :vectorId")
    suspend fun deleteByVectorId(vectorId: Long)
    
    @Query("SELECT * FROM embedding_records WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun getBySource(sourceType: String, sourceId: Long): List<EmbeddingRecord>
    
    @Query("SELECT * FROM embedding_records WHERE vectorId IN (:vectorIds)")
    suspend fun getByVectorIds(vectorIds: List<Long>): List<EmbeddingRecord>
    
    @Query("SELECT * FROM embedding_records WHERE vectorId IN (:vectorIds) AND folder IN (:folders)")
    suspend fun getByVectorIdsAndFolders(vectorIds: List<Long>, folders: List<String>): List<EmbeddingRecord>
    
    @Query("SELECT * FROM embedding_records WHERE vectorId IN (:vectorIds) AND folder = :folder")
    suspend fun getByVectorIdsAndFolder(vectorIds: List<Long>, folder: String): List<EmbeddingRecord>
    
    @Query("SELECT * FROM embedding_records WHERE sourceType = :sourceType")
    suspend fun getAllByType(sourceType: String): List<EmbeddingRecord>
    
    @Query("SELECT * FROM embedding_records WHERE sourceType = :sourceType AND folder = :folder")
    suspend fun getAllByTypeAndFolder(sourceType: String, folder: String): List<EmbeddingRecord>
    
    @Query("SELECT COUNT(*) FROM embedding_records")
    suspend fun count(): Int
    
    @Query("SELECT COUNT(*) FROM embedding_records WHERE sourceType = :sourceType")
    suspend fun countByType(sourceType: String): Int
    
    @Query("SELECT DISTINCT sourceId FROM embedding_records WHERE sourceType = :sourceType")
    suspend fun getIndexedSourceIds(sourceType: String): List<Long>
    
    @Query("DELETE FROM embedding_records")
    suspend fun deleteAll()
}
