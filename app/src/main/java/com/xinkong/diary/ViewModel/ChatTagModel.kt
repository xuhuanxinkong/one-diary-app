package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.ChatTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatTagModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatDao()

    private val _tags = MutableStateFlow(listOf<ChatTag>())
    val tags: StateFlow<List<ChatTag>> = _tags.asStateFlow()

    init {
        viewModelScope.launch {
            chatDao.getAllChatTags().collect { tags ->
                _tags.update { tags }
            }
        }
    }

    fun addTag(tag: ChatTag) {
        viewModelScope.launch {
            chatDao.insertChatTag(tag)
        }
    }

    fun deleteTag(tag: ChatTag) {
        viewModelScope.launch {
            chatDao.deleteChatTag(tag)
        }
    }
}

