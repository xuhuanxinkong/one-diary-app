package com.xinkong.diary.ui.screen.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.ui.animation.pressScaleEffect
import com.xinkong.diary.ui.animation.toggleRotateEffect
import com.xinkong.diary.ui.screen.chat.ChatTagSetting
import com.xinkong.diary.ui.screen.home.SwipeBackground
import com.xinkong.diary.ui.theme.diaryColors
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
import com.xinkong.diary.ui.screen.home.SelectionModeTopBar


//----------------对话页面总入口--------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    isSelectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onEnterSelection: (Long) -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onExitSelection: () -> Unit = {},
    onClick: (Chat) -> Unit
) {
    val viewModel: ChatViewModel = viewModel()
    val chatList by viewModel.chatListState.collectAsStateWithLifecycle()

    var selectedTag by rememberSaveable { mutableStateOf("未分类") }
    val filteredList = remember(chatList, selectedTag) {
        if (selectedTag.isEmpty()) chatList
        else chatList.filter { it.tag == selectedTag }
    }

    Scaffold(
        floatingActionButton = { if (!isSelectionMode) AddChatButton(selectedTag) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.diaryColors.background2)
                .padding(innerPadding)
        ) {
            if (isSelectionMode) {
                SelectionModeTopBar(
                    selectedCount = selectedIds.size,
                    onClose = onExitSelection
                )
            } else {
                ChatHeaderColumn(
                    selectedTag = selectedTag,
                    chatList = chatList,
                    onTagSelect = { tag -> selectedTag = tag },
                    onTagsDelete = { deletedTags ->
                        chatList.filter { it.tag in deletedTags }.forEach { chat ->
                            viewModel.updateChat(chat.copy(tag = "未分类"))
                        }
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = rememberLazyListState()
            ) {
                items(filteredList, key = { it.id }) { chat ->
                    if (isSelectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp)
                                .animateItem()
                                .clickable { onToggleSelection(chat.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChatCard(chat = chat, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = chat.id in selectedIds,
                                onCheckedChange = { onToggleSelection(chat.id) }
                            )
                        }
                    } else {
                        SwipeableChatCard(
                            chat = chat,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp)
                                .animateItem()
                                .combinedClickable(
                                    onClick = { onClick(chat) },
                                    onLongClick = { onEnterSelection(chat.id) }
                                ),
                            onDelete = { viewModel.deleteChat(chat) }
                        )
                    }
                }
            }
        }
    }
}


//----------------对话页头部--------------------
@Composable
fun ChatHeaderColumn(
    selectedTag: String,
    chatList: List<Chat>,
    onTagSelect: (String) -> Unit,
    onTagsDelete: (List<String>) -> Unit
) {
    var isRolled by remember { mutableStateOf(false) }
    var showAiSettings by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.diaryColors.background2)
                .padding(20.dp, 60.dp, 20.dp, 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(selectedTag, fontSize = 35.sp, modifier = Modifier.clickable { isRolled = !isRolled })
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "展开",
                modifier = Modifier
                    .padding(top = 5.dp)
                    .toggleRotateEffect(isRotated = isRolled)
            )
            Spacer(modifier = Modifier.weight(1f))
//            Text("☰", fontSize = 30.sp, modifier = Modifier
//                .padding(8.dp)
//                .clickable { showAiSettings = true })
        }

        AnimatedVisibility(
            visible = isRolled,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
        ) {
            ChatTagSetting(
                chatList = chatList,
                onTagSelect = { tag ->
                    onTagSelect(tag)
                    isRolled = false
                },
                onTagsDelete = onTagsDelete
            )
        }
    }

//    if (showAiSettings) {
//        SettingScreen(onDismiss = { showAiSettings = false })
//    }
}





//----------------滑动删除Chat卡片（复用SwipeBackground）--------------------
@Composable
fun SwipeableChatCard(
    chat: Chat,
    modifier: Modifier,
    onDelete: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                showDialog = true
                false
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = false,
        backgroundContent = { SwipeBackground(dismissState = dismissState) },
        content = { ChatCard(chat = chat, modifier = modifier) }
    )

    if (showDialog) {
        DeleteChatDialog(
            title = chat.title,
            onConfirm = { onDelete(); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}


//----------------Chat卡片--------------------
@Composable
fun ChatCard(chat: Chat, modifier: Modifier) {
    val formattedDate = remember(chat.date) {
        try {
            val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(chat.date)
            SimpleDateFormat("M月d日", Locale.CHINA).format(dateTime!!)
        } catch (e: Exception) {
            chat.date
        }
    }
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, MaterialTheme.diaryColors.border2),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFEFE)),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                "对话:${chat.title}",
                fontSize = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(15.dp, 20.dp, 0.dp, 0.dp)
            )
            Text(
                formattedDate,
                fontSize = 15.sp,
                color = Color.Gray,
                modifier = Modifier.padding(20.dp, 0.dp)
            )
        }
    }
}


//----------------添加Chat按钮--------------------
@Composable
fun AddChatButton(tag: String?) {
    val viewModel: ChatViewModel = viewModel()
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(MaterialTheme.diaryColors.primary),
        contentPadding = PaddingValues(0.dp),
        onClick = { viewModel.addChat("新对话", tag) },
        interactionSource = interactionSource,
        modifier = Modifier
            .padding(bottom = 20.dp, end = 12.dp)
            .size(64.dp)
            .pressScaleEffect(interactionSource = interactionSource)
    ) {
        Text(text = "+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}


//----------------删除确认对话框--------------------
@Composable
fun DeleteChatDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除「${title}」吗？") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

