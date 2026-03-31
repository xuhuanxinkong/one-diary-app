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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onHistoryRoundsChange: (Int) -> Unit = {},
    onAvatarClick: (String, Long?) -> Unit = {_, _ ->}
) {
    val chatViewModel: ChatViewModel = viewModel()
    val aiConfigs by chatViewModel.findAiConfig(chat.id)
        .collectAsStateWithLifecycle(emptyList())
    val userConfig by chatViewModel.findUserConfig(chat.id)
        .collectAsStateWithLifecycle(UserChatConfig(chatId = chat.id))

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.toString()?.let(onBackgroundChange)
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
            Spacer(modifier = Modifier.height(16.dp))
            
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 对话成员
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    "对话成员",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 用户项
                ChatMemberItem(
                    name = userConfig.name.ifEmpty { "用户" },
                    subtitle = "我",
                    avatarUri = userConfig.avatarUri,
                    avatarColor = Color(0xFF5B9BD5),
                    onClick = { onAvatarClick("user", null) }
                )
                Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                
                // AI成员列表
                aiConfigs.forEach { aiConfig ->
                    ChatMemberItem(
                        name = aiConfig.name,
                        subtitle = aiConfig.model.ifEmpty { "AI助手" },
                        avatarUri = aiConfig.avatarUri,
                        avatarColor = Color(0xFFE8F5E9),
                        onClick = { onAvatarClick("assistant", aiConfig.id) }
                    )
                    if (aiConfig != aiConfigs.last()) {
                        Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
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

/**
 * 对话成员列表项（单聊用）
 */
@Composable
fun ChatMemberItem(
    name: String,
    subtitle: String,
    avatarUri: String,
    avatarColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(avatarUri).crossfade(true).build(),
                    contentDescription = "头像",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    name.take(1),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 名称和副标题
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        
        // 右箭头
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
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