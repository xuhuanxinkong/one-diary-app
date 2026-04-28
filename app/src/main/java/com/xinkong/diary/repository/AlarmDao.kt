package com.xinkong.diary.repository

import androidx.room.*
import com.xinkong.diary.data.AlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    fun getAlarmById(id: Int): Flow<AlarmEntity?>

    @Query("SELECT * FROM alarms WHERE aiConfigId = :aiConfigId ORDER BY hour ASC, minute ASC")
    fun getAlarmsByAiConfigId(aiConfigId: Long): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE chatId = :chatId ORDER BY hour ASC, minute ASC")
    fun getAlarmsByChatId(chatId: Long): Flow<List<AlarmEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmEntity)

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    suspend fun getAlarmByIdSync(id: Int): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE aiConfigId = :aiConfigId AND isActive = 1 ORDER BY hour ASC, minute ASC")
    suspend fun getActiveAlarmsByAiConfigIdSync(aiConfigId: Long): List<AlarmEntity>

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: Int)
}
