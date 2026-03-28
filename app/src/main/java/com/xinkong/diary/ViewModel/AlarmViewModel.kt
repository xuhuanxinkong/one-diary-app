package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.data.AlarmEntity
import com.xinkong.diary.repository.AlarmRepository
import com.xinkong.diary.repository.AppDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AlarmRepository

    val alarms: StateFlow<List<AlarmEntity>>

    init {
        val alarmDao = AppDatabase.getDatabase(application).alarmDao()
        repository = AlarmRepository(application, alarmDao)

        alarms = repository.getAllAlarms().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
    }

    fun getAlarmFlow(id: Int): StateFlow<AlarmEntity?> {
        return repository.getAlarmById(id).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )
    }

    suspend fun getAlarmById(id: Int) = repository.getAlarmByIdSync(id)

    fun toggleAlarm(alarm: AlarmEntity, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleAlarm(alarm, isActive)
        }
    }

    fun saveAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            repository.saveAlarm(alarm)
        }
    }

    fun deleteAlarm(id: Int) {
        viewModelScope.launch {
            repository.deleteAlarm(id)
        }
    }
}
