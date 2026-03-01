package com.xinkong.diary.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Insert
    suspend fun insert(diary: Diary)

    @Update
    suspend fun update(diary:Diary)

    @Delete
    suspend fun delete(diary: Diary)

    @Query("SELECT * FROM diaries")
    fun getAll(): Flow<List<Diary>>

    @Query("SELECT*FROM diaries WHERE id =:diaryId")
    fun getByID(diaryId:Int): Flow<Diary>

    @Query("SELECT*FROM diaries WHERE tag =:tag")
    fun getByTag(tag: String):Flow<List<Diary>>
}
