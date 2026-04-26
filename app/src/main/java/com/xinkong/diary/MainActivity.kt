package com.xinkong.diary

import android.content.Intent
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.app.Activity
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
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
import com.xinkong.diary.ui.screen.chat.GroupSettingScreen
import com.xinkong.diary.ui.screen.chat.voice.VoiceCallScreen
import com.xinkong.diary.ui.screen.chat.voice.VoiceCallSelectAiScreen
import com.xinkong.diary.ui.screen.chat.SettingScreen
import com.xinkong.diary.ui.screen.chat.TalkScreen
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.ui.screen.alarm.AlarmEditScreen
import com.xinkong.diary.ui.screen.home.DiaryDetail
import com.xinkong.diary.ui.screen.home.HomeScreen
import com.xinkong.diary.ui.screen.tag.TagManageRoute
import com.xinkong.diary.ui.theme.DiarydTheme

class MainActivity : ComponentActivity() {
    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private var pendingScreenshotRequest = false
    private var isRequestingScreenCapture = false
    private val currentIntentState = mutableStateOf<Intent?>(null)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            isRequestingScreenCapture = false
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val serviceIntent = Intent(this, com.xinkong.diary.ui.screen.chat.voice.FloatingCallService::class.java).apply {
                        action = "com.xinkong.diary.ACTION_START_SCREEN_CAPTURE"
                        putExtra("RESULT_CODE", result.resultCode)
                        putExtra("DATA", data)
                    }
                    startForegroundService(serviceIntent)
                }
            } else {
                pendingScreenshotRequest = false
            }
        }
        
        enableEdgeToEdge()
        setContent {
            DiarydTheme {
                DiaryApp(currentIntentState.value)
            }
        }
        
        intent?.let { handleIntent(it) }
    }

    override fun onResume() {
        super.onResume()
        if (pendingScreenshotRequest) {
            requestScreenCapturePermission()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        if (action == "RESUME_VOICE_CALL_AND_SCREENSHOT" || action == "com.xinkong.diary.RESUME_VOICE_CALL_AND_SCREENSHOT") {
            pendingScreenshotRequest = true
            currentIntentState.value = intent
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                requestScreenCapturePermission()
            }
            // Clear the action so the framework doesn't keep resending exactly the same intent on recreate
            intent.action = null
        } else if (action == "RESUME_VOICE_CALL") {
            currentIntentState.value = intent
            intent.action = null
        }
    }

    private fun requestScreenCapturePermission() {
        if (isRequestingScreenCapture) return
        pendingScreenshotRequest = false
        isRequestingScreenCapture = true
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        window.decorView.post {
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }
}

@Composable
fun DiaryApp(incomingIntent: Intent? = null) {
    val navViewModel: NavigationViewModel = viewModel()
    val diaryViewModel: DiaryViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()
    val alarmViewModel: AlarmViewModel = viewModel()
    val backStack = navViewModel.backStack

    LaunchedEffect(incomingIntent) {
        incomingIntent?.let { intent ->
            // Resume voice call screen if data is present
            val chatId = intent.getLongExtra("chatId", -1L)
            val aiId = intent.getLongExtra("aiId", -1L)
            val isGroup = intent.getBooleanExtra("isGroup", false)
            
            if (chatId != -1L) {
                val targetRoute = Route.VoiceCall(chatId, aiId, isGroup)
                if (navViewModel.currentRoute != targetRoute) {
                    navViewModel.navigateTo(targetRoute)
                }
            }
        }
    }

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
                        isGroupChat = false,
                        onStartVoiceCall = {
                            navViewModel.navigateTo(Route.VoiceCallSelectAi(it.id, isGroupChat = false))
                        }
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
                            // 群聊中点击AI头像，导航到源AI的设置界面
                            if (role == "assistant" && aiId != null) {
                                navViewModel.navigateTo(Route.SourceAiDetail(aiId))
                            }
                            // 用户头像暂不处理
                        },
                        onSetting = {
                            navViewModel.navigateTo(Route.GroupChatSetting(it.id))
                        },
                        isGroupChat = true,
                        onStartVoiceCall = {
                            navViewModel.navigateTo(Route.VoiceCallSelectAi(it.id, isGroupChat = true))
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
                        onHistoryRoundsChange = { rounds ->
                            chatViewModel.updateChat(it.copy(historyRounds = rounds))
                        },
                        onAvatarClick = { role, aiId ->
                            navViewModel.navigateTo(Route.RoleDetail(it.id, role, aiId, isGroupChat = false))
                        }
                    )
                }
            }
            entry<Route.VoiceCallSelectAi> { route ->
                VoiceCallSelectAiScreen(
                    chatId = route.chatId,
                    isGroupChat = route.isGroupChat,
                    onBack = { navViewModel.navigateBack() },
                    onAiSelected = { aiId ->
                        navViewModel.navigateTo(Route.VoiceCall(route.chatId, aiId, route.isGroupChat))
                    }
                )
            }
            entry<Route.VoiceCall> { route ->
                val callViewModel: com.xinkong.diary.ui.screen.chat.voice.CallViewModel = viewModel()
                VoiceCallScreen(
                    chatId = route.chatId,
                    aiId = route.aiId,
                    isGroupChat = route.isGroupChat,
                    viewModel = callViewModel,
                    onMinimizeClick = {
                        // VoiceCall -> VoiceCallSelectAi -> TalkScreen/GroupTalkScreen
                        navViewModel.navigateBack()
                        navViewModel.navigateBack()
                        val target = if (route.isGroupChat) {
                            Route.GroupChatDetail(route.chatId)
                        } else {
                            Route.ChatDetail(route.chatId)
                        }
                        if (navViewModel.currentRoute != target) {
                            navViewModel.navigateTo(target)
                        }
                    },
                    onHangUp = {
                        navViewModel.navigateBack()
                    }
                )
            }
            entry<Route.GroupChatSetting> { route ->
                val chat by chatViewModel.findChat(route.chatId)
                    .collectAsStateWithLifecycle(initialValue = null)
                chat?.let {
                    GroupSettingScreen(
                        chat = it,
                        onBack = { navViewModel.navigateBack() },
                        onDeleteGroupChat = {
                            chatViewModel.deleteChat(it)
                            navViewModel.navigateBack()
                            navViewModel.navigateBack()
                        },
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
                        onAiClick = { sourceAiId ->
                            // 导航到源AI的设置界面（通过源AI所在的Chat）
                            navViewModel.navigateTo(Route.SourceAiDetail(sourceAiId))
                        },
                        onUserClick = {
                            // 导航到用户设置界面（群聊中的用户身份）
                            navViewModel.navigateTo(Route.RoleDetail(it.id, "user", null, isGroupChat = true))
                        }
                    )
                }
            }
            entry<Route.SourceAiDetail> { route ->
                // 根据 sourceAiId 查找对应的 Chat 和 AiConfig，然后显示详情页
                val sourceAiId = route.sourceAiId
                var sourceChat by remember { mutableStateOf<com.xinkong.diary.repository.Chat?>(null) }
                var sourceAiConfig by remember { mutableStateOf<AiChatConfig?>(null) }
                
                LaunchedEffect(sourceAiId) {
                    // 获取源AI配置和对应的Chat
                    sourceChat = chatViewModel.getSourceChatForAi(sourceAiId)
                    sourceAiConfig = chatViewModel.getAiConfigById(sourceAiId)
                }
                
                sourceChat?.let { chat ->
                    DetailScreen(
                        chat = chat,
                        role = "assistant",
                        aiId = sourceAiId,
                        onBack = { navViewModel.navigateBack() },
                        isGroupChat = false  // 编辑的是源AI（单聊），不是群聊
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
                    isAiReminder = route.isAiReminder,
                    selectedAiId = route.selectedAiId,
                    alarmViewModel = alarmViewModel,
                    chatViewModel = chatViewModel,
                    onBack = { navViewModel.navigateBack() }
                )
            }
        }
    )
}
