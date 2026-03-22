package com.xinkong.diary.ui.screen.chat

import android.widget.Toast
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xinkong.diary.Data.AiState
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.ChatMessage
import com.xinkong.diary.repository.UserChatConfig
import com.xinkong.diary.ui.animation.ExpandableAnim
import kotlinx.serialization.json.Json

@Composable
fun TalkScreen(
    chat: Chat,
    onBack: () -> Unit,
    onAvatarClick: (String) -> Unit = {},
    onSetting: () -> Unit = {}
) {
    val viewModel: ChatViewModel = viewModel()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val pendingToolUI by viewModel.pendingToolUI.collectAsStateWithLifecycle()
    val messages by viewModel.getMessages(chat.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val aiConfig by viewModel.findAiConfig(chat.id).collectAsStateWithLifecycle(AiChatConfig(chatId = chat.id))
    val userConfig by viewModel.findUserConfig(chat.id).collectAsStateWithLifecycle(UserChatConfig(chatId = chat.id))

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedMessages = remember { mutableStateListOf<ChatMessage>() }
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                MultiSelectTopBar(
                    onCancel = {
                        isMultiSelectMode = false
                        selectedMessages.clear()
                    }
                )
            } else {
                TalkTopBar(
                    title = chat.title,
                    onBack = onBack,
                    onSetting = onSetting
                )
            }
        },
        bottomBar = {
            if (isMultiSelectMode) {
                MultiSelectBottomBar(
                    onDelete = {
                        if (selectedMessages.isNotEmpty()) showMultiDeleteConfirm = true
                    },
                    onOther = {
                        Toast.makeText(context, "功能暂未开发", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
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
                    onPhoto = { }
                )
            }
        }
    ) { innerPadding ->
        ChatMessageShow(
            messages = messages,
            listState = listState,
            isLoading = aiState is AiState.Loading,
            aiConfig = aiConfig,
            userConfig = userConfig,
            onAvatarClick = onAvatarClick,
            onMessageLongPress = { messageToDelete = it },
            isMultiSelectMode = isMultiSelectMode,
            selectedMessages = selectedMessages,
            onToggleSelect = { msg ->
                if (selectedMessages.contains(msg)) selectedMessages.remove(msg) else selectedMessages.add(msg)
            },
            onEnterMultiSelect = { msg ->
                isMultiSelectMode = true
                if (!selectedMessages.contains(msg)) selectedMessages.add(msg)
            },
            pendingToolUI = pendingToolUI,
            onConfirmTool = { dontAsk -> viewModel.confirmPendingToolAction(dontAsk) },
            onCancelTool = { viewModel.cancelPendingToolAction() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFEDEDED))
        )
    }

    if (showMultiDeleteConfirm) {
        DeleteMessageDialog(
            onConfirm = {
                selectedMessages.forEach { viewModel.deleteMessage(it) }
                selectedMessages.clear()
                isMultiSelectMode = false
                showMultiDeleteConfirm = false
            },
            onDismiss = { showMultiDeleteConfirm = false }
        )
    }

    messageToDelete?.let { msg ->
        DeleteMessageDialog(
            onConfirm = {
                viewModel.deleteMessage(msg)
                messageToDelete = null
            },
            onDismiss = { messageToDelete = null }
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
                .background(Color(0xFFEDEDED))
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
    aiConfig: AiChatConfig,
    userConfig: UserChatConfig,
    onAvatarClick: (String) -> Unit = {},
    onMessageLongPress: (ChatMessage) -> Unit = {},
    isMultiSelectMode: Boolean = false,
    selectedMessages: List<ChatMessage> = emptyList(),
    onToggleSelect: (ChatMessage) -> Unit = {},
    onEnterMultiSelect: (ChatMessage) -> Unit = {},
    pendingToolUI: ChatViewModel.PendingToolUIState? = null,
    onConfirmTool: (Boolean) -> Unit = {},
    onCancelTool: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val aiName = aiConfig.name
    val userName = userConfig.name
    val aiAvatarUri = aiConfig.avatarUri
    val userAvatarUri = userConfig.avatarUri

    LazyColumn(
        state = listState,
        modifier = modifier.padding(12.dp, 0.dp, 12.dp, 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            var showDelete by remember { mutableStateOf(false) }
            val isSelected = selectedMessages.contains(message)

            val tools = try {
                Json.decodeFromString<List<String>>(message.toolExecutions)
            } catch (e: Exception) {
                emptyList()
            }

            ChatBubble(
                content = message.content,
                isUser = message.role == "user",
                avatarUri = if (message.role == "user") userAvatarUri else aiAvatarUri,
                name = if (message.role == "user") userName else aiName,
                toolExecutions = tools,
                onAvatarClick = { if (!isMultiSelectMode) onAvatarClick(message.role) },
                showDelete = showDelete,
                onDelete = { onMessageLongPress(message) },
                onQuote = { },
                onMultiSelect = { onEnterMultiSelect(message) },
                isMultiSelectMode = isMultiSelectMode,
                isSelected = isSelected,
                onToggleSelect = { onToggleSelect(message) }
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
                            Text(aiName.take(2), color = Color.White, fontSize = 14.sp)
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

        if (pendingToolUI != null) {
            item {
                ToolRequestBubble(
                    uiState = pendingToolUI,
                    aiAvatarUri = aiAvatarUri,
                    aiName = aiName,
                    onConfirm = onConfirmTool,
                    onCancel = onCancelTool
                )
            }
        }
    }
}

@Composable
fun ToolRequestBubble(
    uiState: ChatViewModel.PendingToolUIState,
    aiAvatarUri: String,
    aiName: String,
    onConfirm: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var dontAsk by remember { mutableStateOf(false) }

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
                Text(aiName.take(2), color = Color.White, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = aiName,
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
            )
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .background(Color(0xFFF0F0F0), shape = RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "AI请求使用工具：${uiState.title}",
                        fontSize = 14.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = uiState.description,
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (uiState.showDontAskAgain) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { dontAsk = !dontAsk }
                                .padding(bottom = 8.dp)
                        ) {
                            Checkbox(
                                checked = dontAsk,
                                onCheckedChange = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("本次不再提醒", fontSize = 12.sp, color = Color.Gray)
                        }
                    }

                    Divider(color = Color.LightGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = onCancel) {
                            Text("拒绝", color = Color.Red, fontSize = 15.sp)
                        }
                        TextButton(onClick = { onConfirm(dontAsk) }) {
                            Text("同意", color = Color(0xFF07C160), fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    onQuote: () -> Unit,
    onCopy: () -> Unit,
    onSelectText: () -> Unit,
    onMultiSelect: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = {
                onDismissRequest()
                onCopy()
            }
        )
        DropdownMenuItem(
            text = { Text("选择部分文本") },
            onClick = {
                onDismissRequest()
                onSelectText()
            }
        )
        DropdownMenuItem(
            text = { Text("删除") },
            onClick = {
                onDismissRequest()
                onDelete()
            }
        )
        DropdownMenuItem(
            text = { Text("引用") },
            onClick = {
                onDismissRequest()
                onQuote()
            }
        )
        DropdownMenuItem(
            text = { Text("多选") },
            onClick = {
                onDismissRequest()
                onMultiSelect()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    content: String,
    isUser: Boolean,
    avatarUri: String = "",
    name: String = if (isUser) "我" else "AI",
    toolExecutions: List<String> = emptyList(),
    onAvatarClick: () -> Unit = {},
    showDelete: Boolean = false,
    onDelete: () -> Unit = {},
    onQuote: () -> Unit = {},
    onMultiSelect: () -> Unit = {},
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isTextSelectable by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .clickable(enabled = isMultiSelectMode, onClick = { onToggleSelect() })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier
                    .padding(end = 6.dp)
                    .align(Alignment.CenterVertically)
            )
        }

        if (isUser) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.Top
            ) {
                if (showDelete) {
                    DeleteIcon(onClick = onDelete, modifier = Modifier.align(Alignment.CenterVertically))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = name,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 2.dp, end = 4.dp)
                    )
                    Box {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .combinedClickable(
                                    enabled = !isMultiSelectMode,
                                    onLongClick = { showMenu = true },
                                    onClick = { isTextSelectable = false }
                                )
                                .background(Color(0xFF95EC69), shape = RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            if (isTextSelectable) {
                                SelectionContainer {
                                    Text(text = content, fontSize = 15.sp, color = Color.Black)
                                }
                            } else {
                                Text(text = content, fontSize = 15.sp, color = Color.Black)
                            }
                        }
                        ChatMessageMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            onDelete = onDelete,
                            onQuote = onQuote,
                            onCopy = { clipboardManager.setText(AnnotatedString(content)) },
                            onSelectText = { isTextSelectable = true },
                            onMultiSelect = onMultiSelect
                        )
                    }
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
                        Text(name.take(2), color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.weight(1f),
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
                        Text(name.take(2), color = Color.White, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = name,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                    )
                    Box {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .combinedClickable(
                                    enabled = !isMultiSelectMode,
                                    onLongClick = { showMenu = true },
                                    onClick = { isTextSelectable = false }
                                )
                                .background(Color.White, shape = RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            if (isTextSelectable) {
                                SelectionContainer {
                                    Text(text = content, fontSize = 15.sp, color = Color.Black)
                                }
                            } else {
                                Text(text = content, fontSize = 15.sp, color = Color.Black)
                            }
                        }
                        ChatMessageMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            onDelete = onDelete,
                            onQuote = onQuote,
                            onCopy = { clipboardManager.setText(AnnotatedString(content)) },
                            onSelectText = { isTextSelectable = true },
                            onMultiSelect = onMultiSelect
                        )
                    }

                    if (toolExecutions.isNotEmpty()) {
                        ExpandableAnim(
                            title = "执行了 ${toolExecutions.size} 个工具",
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                toolExecutions.forEach { tool ->
                                    Text(
                                        text = "• $tool",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (showDelete) {
                    DeleteIcon(onClick = onDelete, modifier = Modifier.align(Alignment.CenterVertically))
                }
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
                .padding(start = 8.dp, top = 16.dp, end = 8.dp, bottom = 30.dp),
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
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (inputText.isEmpty()) {
                            Text("输入消息...", color = Color.Gray, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onPhoto, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = "照片",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
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
fun MultiSelectTopBar(
    onCancel: () -> Unit
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color(0xFFEDEDED))
                .fillMaxWidth()
                .padding(16.dp, 36.dp, 16.dp, 24.dp)
        ) {
            Text(
                text = "取消",
                fontSize = 16.sp,
                color = Color.Black,
                modifier = Modifier.clickable { onCancel() }
            )
        }
        Divider(color = Color.LightGray, thickness = 0.5.dp)
    }
}

@Composable
fun MultiSelectBottomBar(
    onDelete: () -> Unit,
    onOther: () -> Unit
) {
    Column {
        Divider(color = Color.LightGray, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF7F7F7))
                .padding(vertical = 12.dp, horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOther) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "转发", tint = Color.DarkGray)
            }
            IconButton(onClick = onOther) {
                Icon(imageVector = Icons.Default.StarBorder, contentDescription = "收藏", tint = Color.DarkGray)
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "删除", tint = Color.DarkGray)
            }
            IconButton(onClick = onOther) {
                Icon(imageVector = Icons.Default.MoreHoriz, contentDescription = "更多", tint = Color.DarkGray)
            }
        }
    }
}
