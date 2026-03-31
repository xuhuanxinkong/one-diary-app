package com.xinkong.diary.ui.screen.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xinkong.diary.data.AiResponse

import com.xinkong.diary.Http.AiHttp
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.UserChatConfig
import com.xinkong.diary.ui.screen.home.SettingSectionHeader
import com.xinkong.diary.ui.theme.diaryColors


@Composable
fun SettingScreen(
    chat: Chat,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBackgroundChange: (String) -> Unit,
    onGroupAvatarChange: (String) -> Unit = {},
    onHistoryRoundsChange: (Int) -> Unit = {},
    onAvatarClick: (String, Long?) -> Unit = {_, _ ->},
    isGroupChat: Boolean = false  // 是否为群聊，群聊才显示添加AI按钮
) {
    val chatViewModel: ChatViewModel = viewModel()
    val aiConfigs by chatViewModel.findAiConfig(chat.id)
        .collectAsStateWithLifecycle(emptyList())
    val userConfig by chatViewModel.findUserConfig(chat.id)
        .collectAsStateWithLifecycle(UserChatConfig(chatId = chat.id))
    
    // 获取所有可用的AI（从AI列表，即非群聊的Chat）
    val allChats by chatViewModel.chatListState.collectAsStateWithLifecycle(emptyList())
    val availableAis = remember(allChats) {
        allChats.filter { !it.isGroupChat }
    }
    
    var showAddAiDialog by remember { mutableStateOf(false) }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.toString()?.let(onBackgroundChange)
    }
    
    val groupAvatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.toString()?.let(onGroupAvatarChange)
    }

    Scaffold(
        topBar = { SettingTopBar(onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .background(Color(0xFFF5F5F5)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // AI 头像（群聊时显示添加按钮，点击弹出选择对话框）
            AvatarRow(
                aiConfigs = aiConfigs,
                userConfig = userConfig,
                onAvatarClick = onAvatarClick,
                onAddAiClick = if (isGroupChat) {{ showAddAiDialog = true }} else null
            )

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = Color.LightGray, thickness = 0.5.dp)
            // 对话名称设置
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                // 对话名称
                EditableTitleRow(
                    title = chat.title,
                    onTitleChange = onTitleChange
                )
                Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                BackgroundSettingRow(
                    hasBackground = chat.backgroundUri.isNotEmpty(),
                    onClick = { backgroundPicker.launch("image/*") }
                )
                
                // 群聊头像设置（仅群聊显示）
                if (isGroupChat) {
                    Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { groupAvatarPicker.launch("image/*") }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "群聊头像",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                        Text(
                            if (chat.groupAvatarUri.isNotEmpty()) "已设置" else "未设置",
                            fontSize = 14.sp,
                            color = if (chat.groupAvatarUri.isNotEmpty()) MaterialTheme.diaryColors.primary else Color.Gray
                        )
                    }
                }

                // 记忆对话轮数设置
                Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                var historyExpanded by remember { mutableStateOf(true) }
                SettingSectionHeader(
                    title = "记忆设置",
                    isExpanded = historyExpanded,
                    onClick = { historyExpanded = !historyExpanded }
                )
                if (historyExpanded) {
                    var historyRounds by remember { mutableIntStateOf(chat.historyRounds) }
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "历史对话轮数",
                                fontSize = 14.sp,
                                color = Color.DarkGray
                            )
                            Text(
                                "${historyRounds}轮",
                                fontSize = 14.sp,
                                color = MaterialTheme.diaryColors.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = historyRounds.toFloat(),
                            onValueChange = { 
                                historyRounds = it.toInt()
                            },
                            onValueChangeFinished = {
                                onHistoryRoundsChange(historyRounds)
                            },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "AI将记忆最近${historyRounds}轮对话内容（建议6-15轮）",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
    
    // 添加AI选择对话框（群聊专用）
    if (showAddAiDialog) {
        AddAiToGroupDialog(
            availableChats = availableAis,
            existingAiConfigs = aiConfigs,
            chatViewModel = chatViewModel,
            groupChatId = chat.id,
            onDismiss = { showAddAiDialog = false }
        )
    }
}

/**
 * 添加AI到群聊的对话框
 */
@Composable
fun AddAiToGroupDialog(
    availableChats: List<Chat>,
    existingAiConfigs: List<AiChatConfig>,
    chatViewModel: ChatViewModel,
    groupChatId: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 获取每个单聊的AI配置
    var aiList by remember { mutableStateOf<List<AiChatConfig>>(emptyList()) }
    
    androidx.compose.runtime.LaunchedEffect(availableChats) {
        val ais = mutableListOf<AiChatConfig>()
        for (chat in availableChats) {
            val configs = chatViewModel.findAiConfigOnce(chat.id)
            configs.firstOrNull()?.let { ais.add(it) }
        }
        aiList = ais
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加AI到群聊") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                if (aiList.isEmpty()) {
                    Text("暂无可添加的AI", color = Color.Gray)
                } else {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(aiList) { aiConfig ->
                            // 检查是否已在群聊中
                            val isAlreadyAdded = existingAiConfigs.any { it.name == aiConfig.name }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isAlreadyAdded) {
                                        chatViewModel.addAiToGroupChat(groupChatId, aiConfig)
                                        onDismiss()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // AI头像
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE8F5E9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (aiConfig.avatarUri.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(aiConfig.avatarUri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "AI头像",
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            aiConfig.name.take(1),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.diaryColors.primary
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        aiConfig.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isAlreadyAdded) Color.Gray else Color.Black
                                    )
                                    if (aiConfig.model.isNotEmpty()) {
                                        Text(
                                            aiConfig.model,
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                
                                if (isAlreadyAdded) {
                                    Text(
                                        "已添加",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Divider(color = Color(0xFFF0F0F0))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}


@Composable
fun SettingTopBar(onBack: () -> Unit) {
    Column {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.White)
                .fillMaxWidth()
                .padding(0.dp, 36.dp, 0.dp, 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                text = "对话设置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Divider(color = Color.LightGray, thickness = 0.5.dp)
    }
}


@Composable
fun AvatarRow(
    aiConfigs: List<AiChatConfig>,
    userConfig: UserChatConfig,
    onAvatarClick: (String, Long?) -> Unit = {_, _ ->},
    onAddAiClick: (() -> Unit)? = null  // null表示不显示添加按钮
) {
    val context = LocalContext.current
    LazyRow(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        item {
            // 用户 头像
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5B9BD5))
                        .clickable{onAvatarClick("user", null)},
                    contentAlignment = Alignment.Center
                ) {
                    if (userConfig.avatarUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(userConfig.avatarUri).crossfade(true).build(),
                            contentDescription = "用户头像",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("我", color = Color.White, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        items(aiConfigs) { aiConfig ->
            // AI 头像
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5B9BD5))
                        .clickable{onAvatarClick("assistant", aiConfig.id)},
                    contentAlignment = Alignment.Center
                ) {
                    if (aiConfig.avatarUri.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(aiConfig.avatarUri).crossfade(true).build(),
                            contentDescription = "AI头像",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("AI", color = Color.White, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        // 添加按钮（仅在群聊设置时显示）
        if (onAddAiClick != null) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0E0E0))
                            .clickable { onAddAiClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加",
                            tint = Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun EditableTitleRow(
    title: String,
    onTitleChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("对话名称", fontSize = 16.sp)
        Text(title, fontSize = 16.sp, color = Color.Gray)
    }

    if (showDialog) {
        EditTitleDialog(
            currentTitle = title,
            onConfirm = { newTitle ->
                onTitleChange(newTitle)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}


@Composable
fun EditTitleDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改对话名称") },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun BackgroundSettingRow(
    hasBackground: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("自定义背景", fontSize = 16.sp)
        Text(
            text = if (hasBackground) "已设置 >" else ">",
            fontSize = 16.sp,
            color = Color.Gray
        )
    }
}