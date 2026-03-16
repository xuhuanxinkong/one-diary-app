package com.xinkong.diary.ui.screen.chat

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.xinkong.diary.ui.screen.home.AiSection
import com.xinkong.diary.ui.screen.home.SettingSectionHeader
import com.xinkong.diary.ui.theme.diaryColors


@Composable
fun SettingScreen(
    chat: Chat,
    aiConfig: AiChatConfig,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit
) {
    var aiExpanded by remember { mutableStateOf(false) }
    val chatViewModel: ChatViewModel = viewModel()
    val config by chatViewModel.findAiConfig(chat.id)
        .collectAsStateWithLifecycle(AiChatConfig(chatId = chat.id))




    Scaffold(
        topBar = { SettingTopBar(onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF5F5F5)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // AI 头像 + 添加按钮
            AvatarRow(avatarUri = aiConfig.avatarUri)

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = Color.LightGray, thickness = 0.5.dp)
            //      AI设置
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
                    Divider(color = MaterialTheme.diaryColors.tertiary.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))


                SettingSectionHeader(
                    title = "AI 设置",
                    isExpanded = aiExpanded,
                    onClick = { aiExpanded = !aiExpanded }
                )
                if (aiExpanded) {
                    AiSection(
                        config = config,
                        onSave = { newConfig -> chatViewModel.updateAiConfig(newConfig) }
                    )
                }

                Divider(color = MaterialTheme.diaryColors.tertiary.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
            }
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


@Composable
fun AvatarRow(avatarUri: String) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        // AI 头像
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5B9BD5)),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(avatarUri).crossfade(true).build(),
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

        // 添加按钮（功能暂不实现）
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E0E0))
                    .clickable { /* 暂不实现 */ },
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