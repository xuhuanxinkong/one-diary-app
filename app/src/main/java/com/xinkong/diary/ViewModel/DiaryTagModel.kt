package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.DiaryTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiaryTagModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val diaryDao = db.diaryDao()

    private val _tags = MutableStateFlow(listOf<DiaryTag>())
    val tags: StateFlow<List<DiaryTag>> = _tags.asStateFlow()

    init {
        viewModelScope.launch {
            diaryDao.getAllDiaryTags().collect { tags ->
                _tags.update { tags }
            }
        }
    }

    fun addTag(tag: DiaryTag) {
        viewModelScope.launch {
            diaryDao.insertDiaryTag(tag)
        }
    }

    fun deleteTag(tag: DiaryTag) {
        viewModelScope.launch {
            diaryDao.deleteDiaryTag(tag)
        }
    }
}

