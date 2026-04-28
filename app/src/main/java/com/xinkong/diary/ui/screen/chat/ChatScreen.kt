//// This file is deprecated and should be deleted.
//// Use AiListScreen.kt instead.
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.foundation.lazy.rememberLazyListState
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.AlertDialog
//import androidx.compose.material3.Button
//import androidx.compose.material3.ButtonDefaults
//import androidx.compose.material3.Card
//import androidx.compose.material3.CardDefaults
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.SwipeToDismissBox
//import androidx.compose.material3.SwipeToDismissBoxValue
//import androidx.compose.material3.Text
//import androidx.compose.material3.TextButton
//import androidx.compose.material3.rememberSwipeToDismissBoxState
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.lifecycle.compose.collectAsStateWithLifecycle
//import androidx.lifecycle.viewmodel.compose.viewModel
//import coil.compose.AsyncImage
//import com.xinkong.diary.ViewModel.ChatViewModel
//import com.xinkong.diary.repository.AiChatConfig
//import com.xinkong.diary.repository.Chat
//import com.xinkong.diary.ui.animation.pressScaleEffect
//import com.xinkong.diary.ui.screen.home.SearchBarItem
//import com.xinkong.diary.ui.screen.home.SelectionModeTopBar
//import com.xinkong.diary.ui.screen.home.SwipeBackground
//import com.xinkong.diary.ui.screen.tag.UNCLASSIFIED_TAG_NAME
//import com.xinkong.diary.ui.theme.diaryColors
//import java.text.SimpleDateFormat
//import java.util.Locale
//
////----------------对话页面总入口--------------------
//@OptIn(ExperimentalFoundationApi::class)
//@Composable
//fun ChatScreen(
//    onClick: (Chat) -> Unit
//) {
//    val viewModel: ChatViewModel = viewModel()
//    val chatList by viewModel.chatListState.collectAsStateWithLifecycle()
//    val aiList by viewModel.AiListState.collectAsStateWithLifecycle()
//
//    // ----------- 搜索状态 -------------
//    var searchQuery by remember { mutableStateOf("") }
//    var isSearchMode by remember { mutableStateOf(false) }
//
//    val searchFlow = remember(searchQuery) {
//        if (searchQuery.isNotBlank()) viewModel.searchChat(searchQuery)
//        else kotlinx.coroutines.flow.flowOf(emptyList())
//    }
//    val searchResults by searchFlow.collectAsStateWithLifecycle(initialValue = emptyList())
//
//    val filteredList = remember(chatList, isSearchMode, searchResults) {
//        if (isSearchMode && searchQuery.isNotBlank()) {
//            searchResults
//        } else {
//            chatList
//        }
//    }
//    // --------------------------------
//
//    val listState = rememberLazyListState()
//    var isCollapsed by remember { mutableStateOf(false) }
//
//    LaunchedEffect(listState) {
//        androidx.compose.runtime.snapshotFlow {
//            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
//        }.collect { (index, offset) ->
//            if (index == 0 && offset < 50) {
//                isCollapsed = false
//            } else if (index > 0 || offset > 200) {
//                isCollapsed = true
//            }
//        }
//    }
//
//    Scaffold(
//        floatingActionButton = { if (!isSearchMode) AddChatButton() }
//    ) { innerPadding ->
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .background(MaterialTheme.diaryColors.background3)
//                .padding(innerPadding)
//        ) {
//            if (isSearchMode) {
//                SelectionModeTopBar(
//                    isDiary = false,
//                    selectedCount = filteredList.size,
//                    onClose = {
//                        isSearchMode = false
//                        searchQuery = ""
//                    },
//                    title = "搜索结果: ${filteredList.size} 项"
//                )
//            } else {
//                ChatHeaderColumn(isCollapsed = isCollapsed)
//            }
//
//            LazyColumn(
//                modifier = Modifier
//                    .weight(1f)
//                    .fillMaxWidth(),
//                state = listState
//            ) {
//                item {
//                    SearchBarItem(
//                        searchQuery = searchQuery,
//                        onSearchQueryChange = {
//                            searchQuery = it
//                            isSearchMode = true
//                        },
//                        onSearchExecute = { isSearchMode = true },
//                        onClear = {
//                            searchQuery = ""
//                        },
//                        isDiary = false
//                    )
//                }
//
//                items(filteredList, key = { it.id }) { chat ->
//                    val aiConfig = aiList.firstOrNull { it.chatId == chat.id }
//                    SwipeableChatCard(
//                        chat = chat,
//                        aiConfig = aiConfig,
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(5.dp)
//                            .animateItem()
//                            .clickable { onClick(chat) },
//                        onDelete = { viewModel.deleteChat(chat) }
//                    )
//                }
//            }
//        }
//    }
//}
//
//
////----------------对话页头部--------------------
//@Composable
//fun ChatHeaderColumn(
//    isCollapsed: Boolean = false
//) {
//    val topPadding by androidx.compose.animation.core.animateDpAsState(targetValue = if (isCollapsed) 10.dp else 40.dp, label = "topPadding")
//    val titleFontSize by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isCollapsed) 24f else 35f, label = "titleFontSize")
//
//    Column {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .background(MaterialTheme.diaryColors.background3)
//                .padding(20.dp, topPadding, 20.dp, 0.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Text("AI列表", fontSize = titleFontSize.sp)
//            Spacer(modifier = Modifier.weight(1f))
//        }
//
//        Spacer(modifier = Modifier.height(if(isCollapsed) 10.dp else 20.dp))
//    }
//}
//
//
//
//
//
////----------------滑动删除Chat卡片（复用SwipeBackground）--------------------
//@Composable
//fun SwipeableChatCard(
//    chat: Chat,
//    aiConfig: AiChatConfig?,
//    modifier: Modifier,
//    onDelete: () -> Unit
//) {
//    var showDialog by remember { mutableStateOf(false) }
//    val dismissState = rememberSwipeToDismissBoxState(
//        confirmValueChange = { dismissValue ->
//            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
//                showDialog = true
//                false
//            } else false
//        }
//    )
//    SwipeToDismissBox(
//        state = dismissState,
//        modifier = modifier,
//        enableDismissFromEndToStart = true,
//        enableDismissFromStartToEnd = false,
//        backgroundContent = { SwipeBackground(dismissState = dismissState) },
//        content = { ChatCard(chat = chat, aiConfig = aiConfig, modifier = modifier) }
//    )
//
//    if (showDialog) {
//        DeleteChatDialog(
//            title = chat.title,
//            onConfirm = { onDelete(); showDialog = false },
//            onDismiss = { showDialog = false }
//        )
//    }
//}
//
//
////----------------Chat卡片--------------------
//@Composable
//fun ChatCard(chat: Chat, aiConfig: AiChatConfig?, modifier: Modifier) {
//    val formattedDate = remember(chat.date) {
//        try {
//            val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(chat.date)
//            SimpleDateFormat("M月d日", Locale.CHINA).format(dateTime!!)
//        } catch (e: Exception) {
//            chat.date
//        }
//    }
//    Card(
//        elevation = CardDefaults.cardElevation(4.dp),
//        shape = RoundedCornerShape(20.dp),
//        border = BorderStroke(1.dp, MaterialTheme.diaryColors.border3),
//        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFEFE)),
//        modifier = modifier
//    ) {
//        Column(
//            modifier = Modifier.padding(15.dp, 12.dp),
//            verticalArrangement = Arrangement.spacedBy(6.dp)
//        ) {
//            // 第一行：AI头像 + AI名称 + 未读红点
//            Row(verticalAlignment = Alignment.CenterVertically) {
//                // AI头像
//                if (aiConfig != null && aiConfig.avatarUri.isNotBlank()) {
//                    AsyncImage(
//                        model = aiConfig.avatarUri,
//                        contentDescription = "AI头像",
//                        modifier = Modifier
//                            .size(32.dp)
//                            .clip(CircleShape)
//                    )
//                } else {
//                    Box(
//                        modifier = Modifier
//                            .size(32.dp)
//                            .background(Color(0xFF5B9BD5), CircleShape),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Text(
//                            (aiConfig?.name ?: "AI").take(2),
//                            color = Color.White,
//                            fontSize = 12.sp,
//                            fontWeight = FontWeight.Bold
//                        )
//                    }
//                }
//                Spacer(modifier = Modifier.width(10.dp))
//                Text(
//                    aiConfig?.name ?: "Ai助手",
//                    fontWeight = FontWeight.Bold,
//                    fontSize = 16.sp,
//                    maxLines = 1,
//                    overflow = TextOverflow.Ellipsis,
//                    modifier = Modifier.weight(1f)
//                )
//                if (chat.unreadCount > 0) {
//                    // 红色未读圆点
//                    androidx.compose.foundation.Canvas(
//                        modifier = Modifier.size(12.dp),
//                        onDraw = { drawCircle(Color.Red) }
//                    )
//                }
//            }
//            // 第二行：日期 | 对话名称
//            Text(
//                "$formattedDate | ${chat.title}",
//                maxLines = 1,
//                overflow = TextOverflow.Ellipsis,
//                fontSize = 12.sp,
//                color = Color.Gray
//            )
//        }
//    }
//}
//
//
////----------------添加Chat按钮--------------------
//@Composable
//fun AddChatButton() {
//    val viewModel: ChatViewModel = viewModel()
//    val interactionSource = remember { MutableInteractionSource() }
//
//    Button(
//        shape = CircleShape,
//        colors = ButtonDefaults.buttonColors(MaterialTheme.diaryColors.primary),
//        contentPadding = PaddingValues(0.dp),
//        onClick = { viewModel.addChat("新对话", UNCLASSIFIED_TAG_NAME) },
//        interactionSource = interactionSource,
//        modifier = Modifier
//            .padding(bottom = 20.dp, end = 12.dp)
//            .size(64.dp)
//            .pressScaleEffect(interactionSource = interactionSource)
//    ) {
//        Text(text = "+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
//    }
//}
//
//
////----------------删除确认对话框--------------------
//@Composable
//fun DeleteChatDialog(
//    title: String,
//    onConfirm: () -> Unit,
//    onDismiss: () -> Unit
//) {
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = { Text("确认删除") },
//        text = { Text("确定要删除「${title}」吗？") },
//        confirmButton = {
//            Button(onClick = onConfirm) { Text("删除") }
//        },
//        dismissButton = {
//            TextButton(onClick = onDismiss) { Text("取消") }
//        }
//    )
//}
//
