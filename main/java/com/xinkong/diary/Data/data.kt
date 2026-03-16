package com.xinkong.diary.Data


sealed class AiState{
    object Idle: AiState()
    object Loading: AiState()
    data class Success(val result:String): AiState()
    data class Error(val message: String): AiState()
}