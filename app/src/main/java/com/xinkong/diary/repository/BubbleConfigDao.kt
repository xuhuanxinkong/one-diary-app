package com.xinkong.diary.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xinkong.diary.data.BubbleConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface BubbleConfigDao {
    @Query("SELECT * FROM bubble_config WHERE id = 1")
    fun getConfigFlow(): Flow<BubbleConfig?>

    @Query("SELECT * FROM bubble_config WHERE id = 1")
    suspend fun getConfig(): BubbleConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: BubbleConfig)
}