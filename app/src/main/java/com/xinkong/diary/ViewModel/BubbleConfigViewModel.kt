package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.data.BubbleConfig
import com.xinkong.diary.repository.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BubbleConfigViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).bubbleConfigDao()

    private val _config = MutableStateFlow(BubbleConfig())
    val config: StateFlow<BubbleConfig> = _config.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            dao.getConfigFlow().collect { entity ->
                if (entity != null) {
                    _config.value = entity
                } else {
                    dao.insertConfig(BubbleConfig())
                }
            }
        }
    }

    fun updateConfig(newConfig: BubbleConfig) {
        _config.value = newConfig
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertConfig(newConfig)
        }
    }
}
