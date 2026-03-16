package com.xinkong.diary.ViewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.serialization.Serializable

class NavigationViewModel : ViewModel() {
    // 统一的返回栈
    val backStack = mutableStateListOf<Route>(Route.HomeRoot)

    // 当前选中的 Tab（持久化，不随导航丢失）
    var selectedTab by mutableStateOf(Tab.HOME)
        private set

    // 当前显示的页面（栈顶）
    val currentRoute: Route get() = backStack.last()

    fun navigateTo(route: Route) {
        backStack.add(route)
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun selectTab(tab: Tab) {
        selectedTab = tab
    }
}

enum class Tab { HOME, AI }

@Serializable
sealed class Route {
    // 底部导航的根页面
    @Serializable
    data object HomeRoot : Route()

    // 从首页打开的详情页
    @Serializable
    data class DiaryDetail(val id: Long) : Route()

    // 从 AI 页面打开的对话详情
    @Serializable
    data class ChatDetail(val sessionId: Long) : Route()

    // 角色详情页（从对话中点击头像进入）
    @Serializable
    data class RoleDetail(val chatId: Long, val role: String) : Route()

    // 对话设置页
    @Serializable
    data class ChatSetting(val chatId: Long) : Route()

    // 其他页面
}