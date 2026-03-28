package com.xinkong.diary.repository

import android.content.Context
import com.xinkong.diary.data.AlarmEntity
import com.xinkong.diary.receiver.ChainAlarmHelper
import kotlinx.coroutines.flow.Flow

class AlarmRepository(
    private val context: Context,
    private val alarmDao: AlarmDao
) {
    fun getAllAlarms(): Flow<List<AlarmEntity>> = alarmDao.getAllAlarms()

    fun getAlarmById(id: Int): Flow<AlarmEntity?> = alarmDao.getAlarmById(id)

    suspend fun getAlarmByIdSync(id: Int): AlarmEntity? = alarmDao.getAlarmByIdSync(id)

    suspend fun saveAlarm(alarm: AlarmEntity) {
        if (alarm.id == 0) {
            val newId = alarmDao.insertAlarm(alarm).toInt()
            val savedAlarm = alarm.copy(id = newId)
            if (savedAlarm.isActive) {
                ChainAlarmHelper.scheduleNextAlarm(context, savedAlarm)
            }
        } else {
            alarmDao.updateAlarm(alarm)
            if (alarm.isActive) {
                ChainAlarmHelper.scheduleNextAlarm(context, alarm)
            } else {
                ChainAlarmHelper.cancelAlarm(context, alarm.id)
            }
        }
    }

    suspend fun toggleAlarm(alarm: AlarmEntity, isActive: Boolean) {
        val updated = alarm.copy(isActive = isActive)
        alarmDao.updateAlarm(updated)
        if (isActive) {
            ChainAlarmHelper.scheduleNextAlarm(context, updated)
        } else {
            ChainAlarmHelper.cancelAlarm(context, updated.id)
        }
    }

    suspend fun deleteAlarm(id: Int) {
        val alarm = alarmDao.getAlarmByIdSync(id)
        if (alarm != null) {
            ChainAlarmHelper.cancelAlarm(context, id)
            alarmDao.deleteAlarm(alarm)
        }
    }
}
