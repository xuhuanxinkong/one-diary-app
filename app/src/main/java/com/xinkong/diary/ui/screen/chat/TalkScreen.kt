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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xinkong.diary.data.AiState
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.ChatMessage
import com.xinkong.diary.repository.UserChatConfig
import com.xinkong.diary.ui.animation.ExpandableAnim
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import com.huawei.hms.mlsdk.asr.MLAsrConstants
import com.huawei.hms.mlsdk.asr.MLAsrListener
import com.huawei.hms.mlsdk.asr.MLAsrRecognizer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkScreen(
    chat: Chat,
    onBack: () -> Unit,
    onAvatarClick: (String, Long?) -> Unit = {_, _ -> },
    onSetting: () -> Unit = {},
    isGroupChat: Boolean = false  // 群聊显示AI回复顺序按钮
) {
    val viewModel: ChatViewModel = viewModel()
    val diaryModel: com.xinkong.diary.ViewModel.DiaryViewModel = viewModel()
    val currentFolder by diaryModel.currentFolder.collectAsStateWithLifecycle()
    
    LaunchedEffect(currentFolder) {
        viewModel.aiCurrentFolder = currentFolder
    }
    // 进入聊天时清空未读数
    LaunchedEffect(chat.id) {
        viewModel.clearUnreadCount(chat.id)
    }
    
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val pendingToolUI by viewModel.pendingToolUI.collectAsStateWithLifecycle()
    val messages by viewModel.getMessages(chat.id).collectAsStateWithLifecycle(initialValue = emptyList())
    
    // 根据是否为群聊，获取不同来源的AI配置
    // 单聊：直接从 ai_chat_configs 表获取
    // 群聊：从成员关系表获取源AI配置
    val aiConfigsFromChat by viewModel.findAiConfig(chat.id).collectAsStateWithLifecycle(emptyList())
    val groupMembers by viewModel.getGroupChatMembers(chat.id).collectAsStateWithLifecycle(emptyList())
    val sourceAiConfigs by viewModel.getGroupChatSourceAiConfigs(chat.id).collectAsStateWithLifecycle(emptyList())
    
    // 群聊时使用成员关系中的配置，单聊时使用直接配置
    val aiConfigs = if (isGroupChat) {
        // 将成员的 isEnabled 和 replyOrder 与源AI配置合并
        groupMembers.filter { it.isEnabled }.mapNotNull { member ->
            sourceAiConfigs.find { it.id == member.sourceAiId }?.copy(
                replyOrder = member.replyOrder,
                isEnabled = member.isEnabled
            )
        }.sortedBy { it.replyOrder }
    } else {
        aiConfigsFromChat
    }
    
    val currentTypingAi by viewModel.currentTypingAi.collectAsStateWithLifecycle()
    val userConfig by viewModel.findUserConfig(chat.id).collectAsStateWithLifecycle(UserChatConfig(chatId = chat.id))

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedMessages = remember { mutableStateListOf<ChatMessage>() }
    var showMultiDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var allowSelection by remember { mutableStateOf(true) }
    
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = engine?.setLanguage(Locale.CHINESE) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                ttsReady = false
            }
        }
        textToSpeech = engine

        onDispose {
            engine?.stop()
            engine?.shutdown()
            textToSpeech = null
            ttsReady = false
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val imagesDir = File(context.filesDir, "chat_images")
                    if (!imagesDir.exists()) imagesDir.mkdirs()

                    val imageFile = File(imagesDir, "img_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(imageFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap == null) {
                        selectedImageBase64 = null
                        selectedImageUri = null
                        return@launch
                    }

                    val outputStream = ByteArrayOutputStream()
                    val maxWidth = 1024
                    val maxHeight = 1024
                    var scaledBitmap = bitmap
                    if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
                        val ratio = Math.min(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
                        val width = Math.round(ratio * bitmap.width)
                        val height = Math.round(ratio * bitmap.height)
                        scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    }
                    if (!scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 78, outputStream)) {
                        selectedImageBase64 = null
                        selectedImageUri = null
                        return@launch
                    }
                    val bytes = outputStream.toByteArray()
                    if (bytes.isEmpty()) {
                        selectedImageBase64 = null
                        selectedImageUri = null
                        return@launch
                    }
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    selectedImageUri = android.net.Uri.fromFile(imageFile)
                    selectedImageBase64 = base64
                } catch (e: Exception) {
                    e.printStackTrace()
                    selectedImageBase64 = null
                    selectedImageUri = null
                }
            }
        }
    }

    fun safeBack() {
        allowSelection = false
        focusManager.clearFocus(force = true)
        scope.launch {
            delay(80)
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
        }
    }

    var initialScrollDone by remember { mutableStateOf(false) }

    var animatingMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var previousMessageCount by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(messages.size, aiState) {
        if (messages.size > previousMessageCount) {
            // 取消旧的假流式打字动画，因为现在已经有真流式了
            if (!initialScrollDone) {
                listState.scrollToItem(messages.size - 1)
                initialScrollDone = true
            } else {
                listState.animateScrollToItem(messages.size - 1)
            }
        } else if (aiState is AiState.Streaming || aiState is AiState.Loading) {
            // Scroll to the loading/streaming indicator which is at messages.size
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size)
            }
        }
        previousMessageCount = messages.size
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
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
                    onBack = { safeBack() },
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
                var showExpandPanel by remember { mutableStateOf(false) }
                var showAiReplySheet by remember { mutableStateOf(false) }

                TalkBottomBar(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    selectedImageUri = selectedImageUri,
                    onRemoveImage = {
                        selectedImageUri = null
                        selectedImageBase64 = null
                    },
                    onSend = {
                        if (inputText.isNotBlank() || selectedImageBase64 != null) {
                            val msg = inputText.trim()
                            inputText = ""
                            val selectedAIs = aiConfigs.filter { it.isEnabled }.sortedBy { it.replyOrder }
                            val targetAIs = if (selectedAIs.isNotEmpty()) selectedAIs else aiConfigs.take(1)
                            viewModel.sendMessage(chat.id, msg, targetAIs, selectedImageBase64, selectedImageUri?.toString())
                            selectedImageUri = null
                            selectedImageBase64 = null
                        }
                    },
                    onAddClick = { showExpandPanel = !showExpandPanel },
                    showExpandPanel = showExpandPanel,
                    onPickImage = {
                        imagePickerLauncher.launch("image/*")
                        showExpandPanel = false
                    },
                    showAiReplyButton = isGroupChat,
                    onShowAiReply = { showAiReplySheet = true }
                )

                // 群聊时显示AI回复顺序选择
                if (showAiReplySheet && isGroupChat) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = { showAiReplySheet = false },
                        sheetState = sheetState
                    ) {
                        AiReplyBottomSheetContent(
                            aiConfigs = aiConfigs,
                            onDismiss = { showAiReplySheet = false },
                            onDirectReply = { selectedAIs ->
                                viewModel.directReply(chat.id, selectedAIs)
                            },
                            onSaveSelection = { selectedAIs ->
                                viewModel.saveReplySelection(chat.id, selectedAIs)
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isMultiSelectMode) {
            BackHandler {
                isMultiSelectMode = false
                selectedMessages.clear()
            }
        } else {
            BackHandler {
                safeBack()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                }
        ) {
            if (chat.backgroundUri.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(chat.backgroundUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "聊天背景",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                ChatMessageShow(
                    messages = messages,
                    listState = listState,
                    aiConfigs = aiConfigs,
                    currentTypingAi = currentTypingAi,
                    userConfig = userConfig,
                    animatingMessageId = animatingMessageId,
                    onAnimationEnd = { animatingMessageId = null },
                    onAvatarClick = onAvatarClick,
                    onMessageLongPress = { messageToDelete = it },
                    onReadAloud = { content ->
                        if (content.isBlank()) return@ChatMessageShow
                        if (!ttsReady || textToSpeech == null) {
                            Toast.makeText(context, "朗读暂不可用", Toast.LENGTH_SHORT).show()
                        } else {
                            textToSpeech?.stop()
                            val speakResult = textToSpeech?.speak(
                                content,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "chat_read_${System.currentTimeMillis()}"
                            )
                            if (speakResult == TextToSpeech.ERROR) {
                                Toast.makeText(context, "朗读失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    isMultiSelectMode = isMultiSelectMode,
                    selectedMessages = selectedMessages,
                    onToggleSelect = { msg ->
                        if (selectedMessages.contains(msg)) selectedMessages.remove(msg) else selectedMessages.add(msg)
                    },
                    onEnterMultiSelect = { msg ->
                        isMultiSelectMode = true
                        if (!selectedMessages.contains(msg)) selectedMessages.add(msg)
                    },
                    allowSelection = allowSelection,
                    aiState = aiState,
                    pendingToolUI = pendingToolUI,
                    onConfirmTool = { dontAsk -> viewModel.confirmPendingToolAction(dontAsk) },
                    onCancelTool = { viewModel.cancelPendingToolAction() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(if (chat.backgroundUri.isEmpty()) Color(0xFFEDEDED) else Color.Transparent)
                )
            }
        }
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
    aiConfigs: List<AiChatConfig>,
    currentTypingAi: AiChatConfig?,
    userConfig: UserChatConfig,
    animatingMessageId: Long? = null,
    onAnimationEnd: () -> Unit = {},
    onAvatarClick: (String, Long?) -> Unit = {_, _ -> },
    onMessageLongPress: (ChatMessage) -> Unit = {},
    onReadAloud: (String) -> Unit = {},
    isMultiSelectMode: Boolean = false,
    selectedMessages: List<ChatMessage> = emptyList(),
    onToggleSelect: (ChatMessage) -> Unit = {},
    onEnterMultiSelect: (ChatMessage) -> Unit = {},
    allowSelection: Boolean = true,
    aiState: AiState? = null,
    pendingToolUI: ChatViewModel.PendingToolUIState? = null,
    onConfirmTool: (Boolean) -> Unit = {},
    onCancelTool: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val userName = userConfig.name
    val userAvatarUri = userConfig.avatarUri
    val activeAiConfig = currentTypingAi ?: aiConfigs.firstOrNull()
    val activeAiName = activeAiConfig?.name ?: "AI"
    val activeAiAvatarUri = activeAiConfig?.avatarUri ?: ""

    LazyColumn(
        state = listState,
        modifier = modifier.padding(12.dp, 0.dp, 12.dp, 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            items(messages, key = { it.id }) { message ->
            var showDelete by remember { mutableStateOf(false) }
            val isSelected = selectedMessages.contains(message)
            val isUserMessage = message.role == "user"
            val isAiMessage = message.role == "assistant"

            val tools = try {
                Json.decodeFromString<List<String>>(message.toolExecutions)
            } catch (e: Exception) {
                emptyList()
            }
            
            val currentAiConfig = if (isAiMessage) {
                aiConfigs.find { it.id == message.aiId } ?: aiConfigs.firstOrNull()
            } else null
            
            val aiName = currentAiConfig?.name ?: "AI"
            val aiAvatarUri = currentAiConfig?.avatarUri ?: ""

            val photoUris = try {
                Json.decodeFromString<List<String>>(message.photoUris)
            } catch (e: Exception) {
                emptyList()
            }

            ChatBubble(
                content = message.content,
                isUser = isUserMessage,
                avatarUri = if (isUserMessage) userAvatarUri else aiAvatarUri,
                name = if (isUserMessage) userName else aiName,
                date = message.date,
                photoUris = photoUris,
                toolExecutions = tools,
                reasoningContent = message.reasoningContent,
                onAvatarClick = {
                    if (!isMultiSelectMode) onAvatarClick(if (isUserMessage) "user" else "assistant", message.aiId)
                },
                showDelete = showDelete,
                onDelete = { onMessageLongPress(message) },
                onQuote = { },
                onMultiSelect = { onEnterMultiSelect(message) },
                onReadAloud = { onReadAloud(message.content) },
                isMultiSelectMode = isMultiSelectMode,
                isSelected = isSelected,
                onToggleSelect = { onToggleSelect(message) },
                isAnimating = (animatingMessageId == message.id),
                onAnimationEnd = onAnimationEnd,
                allowSelection = allowSelection
            )
        }
        
        item {
            if (aiState is AiState.Loading || aiState is AiState.Streaming) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                        if (activeAiAvatarUri.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(activeAiAvatarUri).crossfade(true).build(),
                                contentDescription = "AI头像",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(activeAiName.take(2), color = Color.White, fontSize = 14.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .widthIn(max = 260.dp)
                            .background(Color.White, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        val streaming = aiState as? AiState.Streaming
                        if (streaming != null) {
                            streaming.partialReasoning?.let {
                                if (it.isNotEmpty()) {
                                    ExpandableAnim(
                                        title = "思考过程",
                                        modifier = Modifier.padding(bottom = 6.dp),
                                        isExpandedAtStart = false
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .widthIn(max = 220.dp)
                                                .background(Color(0xFFF0F0F0), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            ExpandableMessageContent(
                                                content = streaming.partialReasoning,
                                                textColor = Color.DarkGray,
                                                isAnimating = false,
                                                isSelectable = false
                                            )
                                        }
                                    }
                                }
                            }
                            ExpandableMessageContent(
                                content = streaming.partialContent,
                                textColor = Color.Black,
                                isAnimating = false,
                                isSelectable = false
                            )
                        } else {
                            Text("正在思考...", fontSize = 15.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        item {
            if (pendingToolUI != null) {
                ToolRequestBubble(
                    uiState = pendingToolUI,
                    aiAvatarUri = activeAiAvatarUri,
                    aiName = activeAiName,
                    onConfirm = { dontAsk -> onConfirmTool(dontAsk) },
                    onCancel = { onCancelTool() }
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
fun ExpandableMessageContent(
    content: String,
    textColor: Color = Color.Black,
    isAnimating: Boolean = false,
    isSelectable: Boolean = true, // Added isSelectable flag to prevent SelectionContainer crashes during layout thrashing
    onAnimationEnd: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    var showExpandButton by remember { mutableStateOf(false) }
    var animationCompleted by remember(content) { mutableStateOf(!isAnimating) }

    var displayedText by remember(isAnimating, content) {
        mutableStateOf(if (isAnimating) "" else content)
    }

    LaunchedEffect(isAnimating, content) {
        if (isAnimating) {
            animationCompleted = false
            showExpandButton = false
            val length = content.length
            val step = when {
                length > 800 -> 12
                length > 300 -> 6
                length > 120 -> 3
                else -> 1
            }
            var index = 0
            while (index < length) {
                index = (index + step).coerceAtMost(length)
                displayedText = content.substring(0, index)
                delay(24)
            }
            displayedText = content
            animationCompleted = true
            onAnimationEnd()
        } else {
            displayedText = content
            animationCompleted = true
        }
    }

    val containerModifier = if (isAnimating && !animationCompleted) Modifier else Modifier.animateContentSize()

    Column(modifier = containerModifier) {
        @Composable
        fun MessageTextComponent() {
            Text(
                text = displayedText,
                fontSize = 15.sp,
                color = textColor,
                maxLines = if (isExpanded) Int.MAX_VALUE else 15,
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.hasVisualOverflow) {
                        showExpandButton = true
                    }
                }
            )
        }

        if (!animationCompleted || !isSelectable) {
            MessageTextComponent()
        } else {
            SelectionContainer {
                MessageTextComponent()
            }
        }
        
        if (showExpandButton) {
            Text(
                text = if (isExpanded) "收起" else "展开全文",
                color = Color(0xFF5B9BD5),
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { isExpanded = !isExpanded }
            )
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
    onMultiSelect: () -> Unit,
    onReadAloud: () -> Unit
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
        DropdownMenuItem(
            text = { Text("朗读") },
            onClick = {
                onDismissRequest()
                onReadAloud()
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
    date: String = "",
    photoUris: List<String> = emptyList(),
    toolExecutions: List<String> = emptyList(),
    reasoningContent: String? = null,
    onAvatarClick: () -> Unit = {},
    showDelete: Boolean = false,
    onDelete: () -> Unit = {},
    onQuote: () -> Unit = {},
    onMultiSelect: () -> Unit = {},
    onReadAloud: () -> Unit = {},
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    isAnimating: Boolean = false,
    onAnimationEnd: () -> Unit = {},
    allowSelection: Boolean = true
) {
    var showMenu by remember { mutableStateOf(false) }
    var expandedImageUri by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    if (expandedImageUri != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { expandedImageUri = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { expandedImageUri = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(expandedImageUri).build(),
                    contentDescription = "查看大图",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

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
                    Row(verticalAlignment = Alignment.Bottom) {

                        Text(
                            text = name,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 2.dp, end = 4.dp)
                        )
                    }
                    // 图片单独显示在气泡外
                    if (photoUris.isNotEmpty()) {
                        photoUris.forEach { uri ->
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                                contentDescription = "用户图片",
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .widthIn(max = 200.dp)
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { expandedImageUri = uri },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    // 文字气泡（如果有文字内容）
                    if (content.isNotBlank()) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .combinedClickable(
                                        enabled = !isMultiSelectMode,
                                        onLongClick = { showMenu = true },
                                        onClick = { }
                                    )
                                    .background(Color(0xFF95EC69), shape = RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                ExpandableMessageContent(
                                    content = content,
                                    textColor = Color.Black,
                                    isAnimating = false,
                                    isSelectable = allowSelection
                                )
                            }
                            ChatMessageMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                onDelete = onDelete,
                                onQuote = onQuote,
                                onCopy = { clipboardManager.setText(AnnotatedString(content)) },
                                onMultiSelect = onMultiSelect,
                                onReadAloud = onReadAloud
                            )
                        }
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
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                        )
                    }
                    // 图片单独显示在气泡外
                    if (photoUris.isNotEmpty()) {
                        photoUris.forEach { uri ->
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                                contentDescription = "AI图片",
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .widthIn(max = 200.dp)
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { expandedImageUri = uri },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    // 文字气泡（如果有文字内容）
                    if (content.isNotBlank()) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 260.dp)
                                    .combinedClickable(
                                        enabled = !isMultiSelectMode,
                                        onLongClick = { showMenu = true },
                                        onClick = { }
                                    )
                                    .background(Color.White, shape = RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    if (!reasoningContent.isNullOrBlank()) {
                                        ExpandableAnim(
                                            title = "深度思考",
                                            modifier = Modifier.padding(bottom = 6.dp),
                                            isExpandedAtStart = false
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(6.dp))
                                                    .padding(8.dp)
                                            ) {
                                                ExpandableMessageContent(
                                                    content = reasoningContent,
                                                    textColor = Color.DarkGray,
                                                    isAnimating = false, // 已完成思考过程不播放动画
                                                    isSelectable = allowSelection
                                                )
                                            }
                                        }
                                    }
                                    ExpandableMessageContent(
                                        content = content,
                                        textColor = Color.Black,
                                        isAnimating = isAnimating,
                                        isSelectable = allowSelection,
                                        onAnimationEnd = onAnimationEnd
                                    )
                                }
                            }
                            ChatMessageMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                onDelete = onDelete,
                                onQuote = onQuote,
                                onCopy = { clipboardManager.setText(AnnotatedString(content)) },
                                onMultiSelect = onMultiSelect,
                                onReadAloud = onReadAloud
                            )
                        }
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
    selectedImageUri: android.net.Uri? = null,
    onRemoveImage: () -> Unit = {},
    onSend: () -> Unit,
    onAddClick: () -> Unit,
    showExpandPanel: Boolean,
    onPickImage: () -> Unit = {},
    showAiReplyButton: Boolean = false,
    onShowAiReply: () -> Unit = {}
) {
    Column {
        Divider(color = Color.LightGray, thickness = 0.5.dp)
        
        if (selectedImageUri != null) {
            Box(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp).height(92.dp).width(92.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(selectedImageUri)
                        .build(),
                    contentDescription = "Selected Image",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).border(2.dp, Color(0xFF6CB4EE), RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onRemoveImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        
        TalkInputRow(
            inputText = inputText,
            onInputChange = onInputChange,
            onSend = onSend,
            onAddClick = onAddClick,
            showAiReplyButton = showAiReplyButton,
            onShowAiReply = onShowAiReply
        )
        if (showExpandPanel) {
            TalkExpandablePanel(onPickImage)
        }
    }
}

@Composable
fun TalkInputRow(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAddClick: () -> Unit,
    showAiReplyButton: Boolean = false,
    onShowAiReply: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    // 记录开始识别时的文本，用于追加
    var textBeforeListening by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    
    // 检测是否为华为/荣耀设备
    val isHuaweiDevice = remember {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        manufacturer.contains("huawei") || manufacturer.contains("honor")
    }
    
    // 华为 ML Kit 语音识别器（仅华为设备创建）
    var mlAsrRecognizer by remember {
        mutableStateOf(
            if (isHuaweiDevice) MLAsrRecognizer.createAsrRecognizer(context) else null
        )
    }
    
    // 使用 rememberUpdatedState 确保回调中使用最新的值
    val currentOnInputChange by androidx.compose.runtime.rememberUpdatedState(onInputChange)
    val currentTextBefore by androidx.compose.runtime.rememberUpdatedState(textBeforeListening)
    
    // 系统语音识别（非华为设备使用）
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                val newText = if (textBeforeListening.isEmpty()) recognizedText else "$textBeforeListening$recognizedText"
                onInputChange(newText)
            }
        }
    }
    
    // 设置华为语音识别监听器（仅华为设备）
    if (isHuaweiDevice && mlAsrRecognizer != null) {
        DisposableEffect(mlAsrRecognizer) {
            val recognizer = mlAsrRecognizer
            recognizer?.setAsrListener(object : MLAsrListener {
                override fun onStartListening() {
                    android.util.Log.d("VoiceInput", "华为 ML Kit: 开始聆听")
                }
                
                override fun onStartingOfSpeech() {
                    android.util.Log.d("VoiceInput", "华为 ML Kit: 检测到说话")
                }
                
                override fun onVoiceDataReceived(data: ByteArray?, energy: Float, bundle: Bundle?) {}
                
                override fun onRecognizingResults(partialResults: Bundle?) {
                    val partial = partialResults?.getString(MLAsrRecognizer.RESULTS_RECOGNIZING)
                    android.util.Log.d("VoiceInput", "华为 ML Kit 实时结果: $partial")
                    if (!partial.isNullOrEmpty()) {
                        val newText = if (currentTextBefore.isEmpty()) partial else "$currentTextBefore$partial"
                        currentOnInputChange(newText)
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val finalResult = results?.getString(MLAsrRecognizer.RESULTS_RECOGNIZED)
                    android.util.Log.d("VoiceInput", "华为 ML Kit 最终结果: $finalResult")
                    if (!finalResult.isNullOrEmpty()) {
                        val newText = if (currentTextBefore.isEmpty()) finalResult else "$currentTextBefore$finalResult"
                        currentOnInputChange(newText)
                    }
                }
                
                override fun onError(error: Int, errorMessage: String?) {
                    isListening = false
                    android.util.Log.e("VoiceInput", "华为 ML Kit 错误: $error - $errorMessage")
                    val msg = when (error) {
                        MLAsrConstants.ERR_NO_NETWORK -> "网络不可用"
                        MLAsrConstants.ERR_SERVICE_UNAVAILABLE -> "服务不可用"
                        MLAsrConstants.ERR_NO_UNDERSTAND -> "未识别到语音"
                        else -> errorMessage ?: "识别错误"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                
                override fun onState(state: Int, params: Bundle?) {
                    android.util.Log.d("VoiceInput", "华为 ML Kit 状态: $state")
                }
            })
            
            onDispose {
                recognizer?.destroy()
            }
        }
    }

    fun startSystemSpeechRecognition() {
        // 系统界面会接管录音流程
        isListening = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话...")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "您的设备不支持语音识别", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 启动语音识别
    fun startVoiceRecognition() {
        textBeforeListening = inputText
        isListening = true
        
        if (isHuaweiDevice) {
            // 华为设备：使用 ML Kit
            if (mlAsrRecognizer == null) {
                mlAsrRecognizer = MLAsrRecognizer.createAsrRecognizer(context)
            }
            val intent = Intent(MLAsrConstants.ACTION_HMS_ASR_SPEECH).apply {
                putExtra(MLAsrConstants.LANGUAGE, "zh-CN")
                putExtra(MLAsrConstants.FEATURE, MLAsrConstants.FEATURE_WORDFLUX)
                putExtra(MLAsrConstants.PUNCTUATION_ENABLE, true)
            }
            try {
                mlAsrRecognizer?.startRecognizing(intent) ?: startSystemSpeechRecognition()
            } catch (e: Exception) {
                // 华为识别器异常时回退到系统识别
                startSystemSpeechRecognition()
            }
        } else {
            // 其他设备：使用系统语音识别
            startSystemSpeechRecognition()
        }
    }
    
    // 停止语音识别
    fun stopVoiceRecognition() {
        if (isHuaweiDevice && mlAsrRecognizer != null) {
            try {
                mlAsrRecognizer?.destroy()
                mlAsrRecognizer = MLAsrRecognizer.createAsrRecognizer(context)
            } catch (_: Exception) {
                // 已销毁或未启动时忽略
            }
        }
        isListening = false
    }
    
    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecognition()
        } else {
            Toast.makeText(context, "需要麦克风权限才能使用语音输入", Toast.LENGTH_SHORT).show()
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F7F7))
            .padding(start = 8.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = inputText,
            onValueChange = onInputChange,
            textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            keyboardActions = KeyboardActions(
                onSend = {
                    onInputChange("$inputText\n")
                },
                onDone = {
                    onInputChange("$inputText\n")
                }
            ),
            singleLine = false,
            maxLines = 6,
            modifier = Modifier
                .weight(1f)
                .background(Color.White, shape = RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (inputText.isEmpty()) {
                        Text(
                            if (isListening) "正在聆听..." else "输入消息...",
                            color = if (isListening) Color(0xFF07C160) else Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(modifier = Modifier.width(4.dp))
        // 语音输入按钮（输入框右侧）
        IconButton(
            onClick = {
                if (isListening) {
                    stopVoiceRecognition()
                } else {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) {
                        startVoiceRecognition()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "停止语音" else "语音输入",
                tint = if (isListening) Color(0xFFE53935) else Color.DarkGray,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))
        // 群聊时显示AI回复顺序按钮
        if (showAiReplyButton) {
            IconButton(onClick = onShowAiReply, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.StarBorder,
                    contentDescription = "AI回复顺序",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        IconButton(onClick = onAddClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加",
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

@Composable
fun TalkExpandablePanel(onPickImage: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F7F7))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .padding(5.dp)
            .clickable { onPickImage() }) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.White, shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Photo, contentDescription = "相册", tint = Color.Gray, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("相册", fontSize = 12.sp, color = Color.DarkGray)
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .padding(5.dp)
            .clickable { Toast.makeText(context, "分享功能开发中", Toast.LENGTH_SHORT).show() }) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.White, shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "分享", tint = Color.Gray, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("分享", fontSize = 12.sp, color = Color.DarkGray)
        }

        // 可以继续添加更多功能按钮
        Spacer(modifier = Modifier.weight(1f))
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

@Composable
fun AiReplyBottomSheetContent(
    aiConfigs: List<AiChatConfig>,
    onDismiss: () -> Unit,
    onDirectReply: (selectedOrder: List<AiChatConfig>) -> Unit,
    onSaveSelection: (selectedOrder: List<AiChatConfig>) -> Unit
) {
    val selectedAIs = remember { mutableStateListOf<AiChatConfig>() }

    LaunchedEffect(aiConfigs) {
        if (selectedAIs.isEmpty()) {
            val enabled = aiConfigs.filter { it.isEnabled }.sortedBy { it.replyOrder }
            selectedAIs.addAll(enabled)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        // AI 列表
        Text("选择要回复的 AI 及顺序：", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
        Divider(modifier = Modifier.padding(bottom = 12.dp))

        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(aiConfigs) { config ->
                val index = selectedAIs.indexOf(config)
                val isSelected = index != -1
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (isSelected) {
                            selectedAIs.remove(config)
                        } else {
                            selectedAIs.add(config)
                        }
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF5B9BD5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(config.name.take(2), color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = config.name, modifier = Modifier.weight(1f))
                    
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.dp, if (isSelected) Color(0xFF07C160) else Color.Gray, CircleShape)
                            .background(if (isSelected) Color(0xFF07C160) else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text((index + 1).toString(), color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        
        // 底部按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { onDirectReply(selectedAIs); onDismiss() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B9BD5))
            ) {
                Text("直接回复")
            }
            Button(
                onClick = { onSaveSelection(selectedAIs); onDismiss() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF07C160))
            ) {
                Text("保存选择")
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}
