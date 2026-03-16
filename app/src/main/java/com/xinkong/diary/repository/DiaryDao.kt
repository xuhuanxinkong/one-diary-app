package com.xinkong.diary.repository

import android.os.Message
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    //日记相关
    @Insert
    suspend fun insert(diary: Diary)

    @Update
    suspend fun update(diary:Diary)

    @Delete
    suspend fun delete(diary: Diary)

    @Query("SELECT * FROM diaries")
    fun getAll(): Flow<List<Diary>>

    @Query("SELECT*FROM diaries WHERE id =:diaryId")
    fun getByID(diaryId: Long): Flow<Diary>

    @Query("SELECT*FROM diaries WHERE tag =:tag")
    fun getByTag(tag: String):Flow<List<Diary>>

    @Query("SELECT*FROM diaries WHERE type = :diaryType")
    fun getByType(diaryType:String): Flow<List<Diary>>

    @Query("SELECT * FROM diaries WHERE id IN (:ids)")
    suspend fun getDiariesByIds(ids: List<Long>): List<Diary>

    @Query(
        """
        SELECT * FROM diaries
        WHERE title LIKE '%' || :keyword || '%'
           OR text LIKE '%' || :keyword || '%'
           OR content LIKE '%' || :keyword || '%'
        ORDER BY id DESC
        LIMIT :limit
        """
    )
    suspend fun searchByKeyword(keyword: String, limit: Int): List<Diary>



}
