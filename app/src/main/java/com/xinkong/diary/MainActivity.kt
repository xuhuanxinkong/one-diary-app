package com.xinkong.diary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.xinkong.diary.ViewModel.AlarmViewModel
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.ViewModel.NavigationViewModel
import com.xinkong.diary.ViewModel.Route
import com.xinkong.diary.ui.screen.chat.DetailScreen
import com.xinkong.diary.ui.screen.chat.SettingScreen
import com.xinkong.diary.ui.screen.chat.TalkScreen
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.ui.screen.alarm.AlarmEditScreen
import com.xinkong.diary.ui.screen.home.DiaryDetail
import com.xinkong.diary.ui.screen.home.HomeScreen
import com.xinkong.diary.ui.screen.tag.TagManageRoute
import com.xinkong.diary.ui.theme.DiarydTheme

//666
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
    val alarmViewModel: AlarmViewModel = viewModel()
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
                        onAvatarClick = { role, aiId ->
                            navViewModel.navigateTo(Route.RoleDetail(it.id, role, aiId, isGroupChat = false))
                        },
                        onSetting = {
                            navViewModel.navigateTo(Route.ChatSetting(it.id))
                        },
                        isGroupChat = false
                    )
                }
            }
            entry<Route.GroupChatDetail> { route ->
                val chat by chatViewModel.findChat(route.sessionId)
                    .collectAsStateWithLifecycle(initialValue = null)
                chat?.let {
                    TalkScreen(
                        chat = it,
                        onBack = { navViewModel.navigateBack() },
                        onAvatarClick = { role, aiId ->
                            navViewModel.navigateTo(Route.RoleDetail(it.id, role, aiId, isGroupChat = true))
                        },
                        onSetting = {
                            navViewModel.navigateTo(Route.GroupChatSetting(it.id))
                        },
                        isGroupChat = true
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
                        aiId = route.aiId,
                        onBack = { navViewModel.navigateBack() },
                        isGroupChat = route.isGroupChat
                    )
                }
            }
            entry<Route.ChatSetting> { route ->
                val chat by chatViewModel.findChat(route.chatId)
                    .collectAsStateWithLifecycle(initialValue = null)
                chat?.let {
                    SettingScreen(
                        chat = it,
                        onBack = { navViewModel.navigateBack() },
                        onTitleChange = { newTitle ->
                            chatViewModel.updateChat(it.copy(title = newTitle))
                        },
                        onBackgroundChange = { backgroundUri ->
                            chatViewModel.updateChat(it.copy(backgroundUri = backgroundUri))
                        },
                        onGroupAvatarChange = { avatarUri ->
                            chatViewModel.updateChat(it.copy(groupAvatarUri = avatarUri))
                        },
                        onHistoryRoundsChange = { rounds ->
                            chatViewModel.updateChat(it.copy(historyRounds = rounds))
                        },
                        onAvatarClick = { role, aiId ->
                            navViewModel.navigateTo(Route.RoleDetail(it.id, role, aiId, isGroupChat = false))
                        },
                        isGroupChat = false
                    )
                }
            }
            entry<Route.GroupChatSetting> { route ->
                val chat by chatViewModel.findChat(route.chatId)
                    .collectAsStateWithLifecycle(initialValue = null)
                chat?.let {
                    SettingScreen(
                        chat = it,
                        onBack = { navViewModel.navigateBack() },
                        onTitleChange = { newTitle ->
                            chatViewModel.updateChat(it.copy(title = newTitle))
                        },
                        onBackgroundChange = { backgroundUri ->
                            chatViewModel.updateChat(it.copy(backgroundUri = backgroundUri))
                        },
                        onGroupAvatarChange = { avatarUri ->
                            chatViewModel.updateChat(it.copy(groupAvatarUri = avatarUri))
                        },
                        onHistoryRoundsChange = { rounds ->
                            chatViewModel.updateChat(it.copy(historyRounds = rounds))
                        },
                        onAvatarClick = { role, aiId ->
                            navViewModel.navigateTo(Route.RoleDetail(it.id, role, aiId, isGroupChat = true))
                        },
                        isGroupChat = true
                    )
                }
            }
            entry<Route.TagManage> { route ->
                val type = route.type
                TagManageRoute(
                    type = type,
                    onBack = { navViewModel.navigateBack() }
                )
            }
            entry<Route.AlarmEdit> { route ->
                AlarmEditScreen(
                    id = route.id,
                    alarmViewModel = alarmViewModel,
                    chatViewModel = chatViewModel,
                    onBack = { navViewModel.navigateBack() }
                )
            }
        }
    )
}
