package com.xinkong.diary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.ViewModel.NavigationViewModel
import com.xinkong.diary.ViewModel.Route
import com.xinkong.diary.ui.screen.chat.DetailScreen
import com.xinkong.diary.ui.screen.chat.SettingScreen
import com.xinkong.diary.ui.screen.chat.TalkScreen
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.ui.screen.home.DiaryDetail
import com.xinkong.diary.ui.screen.home.HomeScreen
import com.xinkong.diary.ui.theme.DiarydTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiarydTheme {
                DiaryApp()
            }
        }
    }
}

@Composable
fun DiaryApp() {
    val navViewModel: NavigationViewModel = viewModel()
    val diaryViewModel: DiaryViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()
    val backStack = navViewModel.backStack


    NavDisplay(
        backStack = backStack,
        onBack = { navViewModel.navigateBack() },
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith
                    slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        },
        entryProvider = entryProvider {
            entry<Route.HomeRoot> {
                HomeScreen()
            }
            entry<Route.DiaryDetail> { route ->
                val diary by diaryViewModel.findDiary(route.id)
                    .collectAsStateWithLifecycle(initialValue = null)
                diary?.let {
                    DiaryDetail(
                        diary = it,
                        onClose = { navViewModel.navigateBack() },
                        onSave = { updated ->
                            diaryViewModel.updateDiary(updated)
                            navViewModel.navigateBack()
                        }
                    )
                }
            }
            entry<Route.ChatDetail> { route ->
                val chat by chatViewModel.findChat(route.sessionId)
                    .collectAsStateWithLifecycle(initialValue = null)
                chat?.let {
                    TalkScreen(
                        chat = it,
                        onBack = { navViewModel.navigateBack() },
                        onAvatarClick = { role ->
                            navViewModel.navigateTo(Route.RoleDetail(it.id, role))
                        },
                        onSetting = {
                            navViewModel.navigateTo(Route.ChatSetting(it.id))
                        }
                    )
                }
            }
            entry<Route.RoleDetail> { route ->
                val chat by chatViewModel.findChat(route.chatId)
                    .collectAsStateWithLifecycle(initialValue = null)
                chat?.let {
                    DetailScreen(
                        chat = it,
                        role = route.role,
                        onBack = { navViewModel.navigateBack() }
                    )
                }
            }
            entry<Route.ChatSetting> { route ->
                val chat by chatViewModel.findChat(route.chatId)
                    .collectAsStateWithLifecycle(initialValue = null)
                val aiConfig by chatViewModel.findAiConfig(route.chatId)
                    .collectAsStateWithLifecycle(AiChatConfig(chatId = route.chatId))
                chat?.let {
                    SettingScreen(
                        chat = it,
                        aiConfig = aiConfig,
                        onBack = { navViewModel.navigateBack() },
                        onTitleChange = { newTitle ->
                            chatViewModel.updateChat(it.copy(title = newTitle))
                        }
                    )
                }
            }
        }
    )
}
