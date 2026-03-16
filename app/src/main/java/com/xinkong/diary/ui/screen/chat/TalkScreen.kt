package com.xinkong.diary.ui.screen.chat

import android.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xinkong.diary.Data.AiState
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.repository.UserChatConfig
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.ChatMessage
import com.xinkong.diary.ui.theme.diaryColors


@Composable
fun TalkScreen(
    chat: Chat,
    onBack: () -> Unit,
    onAvatarClick: (String) -> Unit = {},
    onSetting: () -> Unit = {}
) {
    val viewModel: ChatViewModel = viewModel()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val pendingDiaryRead by viewModel.pendingDiaryRead.collectAsStateWithLifecycle()
    val messages by viewModel.getMessages(chat.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val aiConfig by viewModel.findAiConfig(chat.id).collectAsStateWithLifecycle(AiChatConfig(chatId = chat.id))
    val userConfig by viewModel.findUserConfig(chat.id).collectAsStateWithLifecycle(UserChatConfig(chatId = chat.id))
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }

    // 自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TalkTopBar(
                title = chat.title,
                onBack = onBack,
                onSetting = onSetting
            )
        },
        bottomBar = {
            TalkBottomBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        val msg = inputText.trim()
                        inputText = ""
                        viewModel.sendMessage(chat.id, msg)
                    }
                },
                onPhoto = { /* 暂不实现 */ }
            )
        }
    ) { innerPadding ->
        ChatMessageShow(
            messages = messages,
            listState = listState,
            isLoading = aiState is AiState.Loading,
            aiAvatarUri = aiConfig.avatarUri,
            userAvatarUri = userConfig.avatarUri,
            onAvatarClick = onAvatarClick,
            onMessageLongPress = { messageToDelete = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFEDEDED))
        )
    }

    // 删除确认对话框
    messageToDelete?.let { msg ->
        DeleteMessageDialog(
            onConfirm = {
                viewModel.deleteMessage(msg)
                messageToDelete = null
            },
            onDismiss = { messageToDelete = null }
        )
    }

    pendingDiaryRead?.let { action ->
        DiaryReadConfirmDialog(
            keyword = action.keyword,
            limit = action.limit,
            onConfirm = { viewModel.confirmPendingDiaryRead() },
            onDismiss = { viewModel.cancelPendingDiaryRead() }
        )
    }
}


@Composable
fun TalkTopBar(
    title: String,
    onBack: () -> Unit,
    onSetting: () -> Unit = {}
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.diaryColors.background0)
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
                text = title,
                fontSize = 18.sp,
                maxLines = 1
            )
            IconButton(onClick = onSetting) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "设置"
                )
            }
        }
        Divider(color = Color.LightGray, thickness = 0.5.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageShow(
    messages: List<ChatMessage>,
    listState: LazyListState,
    isLoading: Boolean,
    aiAvatarUri: String = "",
    userAvatarUri: String = "",
    onAvatarClick: (String) -> Unit = {},
    onMessageLongPress: (ChatMessage) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.padding(12.dp,0.dp,12.dp,8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            var showDelete by remember { mutableStateOf(false) }
            ChatBubble(
                content = message.content,
                isUser = message.role == "user",
                avatarUri = if (message.role == "user") userAvatarUri else aiAvatarUri,
                onAvatarClick = { onAvatarClick(message.role) },
                onLongPress = { showDelete = !showDelete },
                showDelete = showDelete,
                onDelete = { onMessageLongPress(message) }
            )
        }
        if (isLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF5B9BD5)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (aiAvatarUri.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(aiAvatarUri).crossfade(true).build(),
                                contentDescription = "AI头像",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text("AI", color = Color.White, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.White, shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text("正在输入...", fontSize = 15.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    content: String,
    isUser: Boolean,
    avatarUri: String = "",
    onAvatarClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    showDelete: Boolean = false,
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    if (isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top
        ) {
            if (showDelete) {
                DeleteIcon(onClick = onDelete, modifier = Modifier.align(Alignment.CenterVertically))
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .combinedClickable(onLongClick = onLongPress, onClick = {})
                    .background(Color(0xFF95EC69), shape = RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(text = content, fontSize = 15.sp, color = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6CB4EE))
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(avatarUri).crossfade(true).build(),
                        contentDescription = "用户头像",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("我", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5B9BD5))
                    .clickable { onAvatarClick() },
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
                    Text("AI", color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .combinedClickable(onLongClick = onLongPress, onClick = {})
                    .background(Color.White, shape = RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(text = content, fontSize = 15.sp, color = Color.Black)
            }
            if (showDelete) {
                DeleteIcon(onClick = onDelete, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
fun DeleteIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(32.dp)) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "删除",
            tint = Color.Red,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun TalkBottomBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onPhoto: () -> Unit
) {
    Column {
        Divider(color = Color.LightGray, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF7F7F7))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White, shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) {
                            Text("输入消息...", color = Color.Gray, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onPhoto, modifier = Modifier.size(40.dp)) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_menu_camera),
                    contentDescription = "照片",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (inputText.isNotBlank()) Color(0xFF07C160) else Color(0xFFCCCCCC),
                        shape = RoundedCornerShape(6.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DeleteMessageDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除这条消息吗？") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun DiaryReadConfirmDialog(
    keyword: String,
    limit: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("允许读取笔记？") },
        text = {
            Text("AI 请求读取本地笔记（关键词：$keyword，最多 $limit 条）。是否允许本次读取？")
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("允许") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
