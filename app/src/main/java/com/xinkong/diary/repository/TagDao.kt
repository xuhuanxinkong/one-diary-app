package com.xinkong.diary.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    // TagFolder
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTagFolder(folder: TagFolder)

    @Delete
    suspend fun deleteTagFolder(folder: TagFolder)

    @Query("SELECT * FROM tag_folders")
    fun getAllTagFolders(): Flow<List<TagFolder>>

    @Query("SELECT * FROM tag_folders WHERE type = :type")
    fun getTagFoldersByType(type: String): Flow<List<TagFolder>>

    // Diary Tag
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiaryTag(tag: DiaryTag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDiaryTagIgnore(tag: DiaryTag)

    @Delete
    suspend fun deleteDiaryTag(tag: DiaryTag)

    @Query("SELECT * FROM diary_tags")
    fun getAllDiaryTags(): Flow<List<DiaryTag>>

    @Query("SELECT * FROM diary_tags WHERE folder = :folder")
    suspend fun getDiaryTagsByFolder(folder: String): List<DiaryTag>

    // Chat Tag
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatTag(tag: ChatTag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChatTagIgnore(tag: ChatTag)

    @Delete
    suspend fun deleteChatTag(tag: ChatTag)

    @Query("SELECT * FROM chat_tags")
    fun getAllChatTags(): Flow<List<ChatTag>>

    @Query("SELECT * FROM chat_tags WHERE folder = :folder")
    suspend fun getChatTagsByFolder(folder: String): List<ChatTag>
}
