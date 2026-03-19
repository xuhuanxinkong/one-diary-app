package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.TagFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TagFolderModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val tagDao = db.tagDao()

    private val _folders = MutableStateFlow(listOf<TagFolder>())
    val folders: StateFlow<List<TagFolder>> = _folders.asStateFlow()

    init {
        viewModelScope.launch {
            tagDao.getAllTagFolders().collect { folders ->
                _folders.update { folders }
            }
        }
    }

    fun addOrUpdateFolder(folder: TagFolder) {
        viewModelScope.launch {
            tagDao.insertTagFolder(folder)
        }
    }

    fun deleteFolder(folder: TagFolder) {
        viewModelScope.launch {
            tagDao.deleteTagFolder(folder)
        }
    }
}