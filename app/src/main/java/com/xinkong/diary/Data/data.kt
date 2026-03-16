package com.xinkong.diary.Data


sealed class AiState{
    object Idle: AiState()
    object Loading: AiState()
    data class Success(val result:String): AiState()
    data class Error(val message: String): AiState()
}

// AI 响应：未来可扩展技能调用等类型
sealed class AiResponse {
    data class Text(val content: String) : AiResponse()
    // 未来扩展：AI 返回可执行技能
    // data class SkillAction(val skill: String, val params: Map<String, Any>) : AiResponse()
}