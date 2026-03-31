package com.xinkong.diary.ui.screen.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.ViewModel.TagModel
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.ui.animation.pressScaleEffect
import com.xinkong.diary.ui.screen.home.SearchBarItem
import com.xinkong.diary.ui.screen.home.SwipeBackground
import com.xinkong.diary.ui.theme.diaryColors
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * AI列表主界面 - 包含"AI助手列表"和"群聊"两个Tab
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiListScreen(
    onAiChatClick: (Chat) -> Unit,
    onGroupChatClick: (Chat) -> Unit
) {
    val viewModel: ChatViewModel = viewModel()
    val tagModel: TagModel = viewModel()
    val chatList by viewModel.chatListState.collectAsStateWithLifecycle()
    val aiList by viewModel.AiListState.collectAsStateWithLifecycle()
    val tagFolders by tagModel.tagFolders.collectAsStateWithLifecycle()
    
    // Tab状态: 0 = AI助手列表, 1 = 群聊
    var selectedTab by remember { mutableStateOf(0) }
    
    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }
    
    // 创建群聊对话框
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    
    // 创建AI对话框
    var showCreateAiDialog by remember { mutableStateOf(false) }

    val searchFlow = remember(searchQuery) {
        if (searchQuery.isNotBlank()) viewModel.searchChat(searchQuery)
        else flowOf(emptyList())
    }
    val searchResults by searchFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // 区分单聊和群聊（使用isGroupChat字段）
    val singleAiChats = remember(chatList) {
        chatList.filter { !it.isGroupChat }
    }
    
    val groupChats = remember(chatList) {
        chatList.filter { it.isGroupChat }
    }

    val filteredList = remember(selectedTab, isSearchMode, searchResults, singleAiChats, groupChats, searchQuery) {
        if (isSearchMode && searchQuery.isNotBlank()) {
            if (selectedTab == 0) {
                searchResults.filter { !it.isGroupChat }
            } else {
                searchResults.filter { it.isGroupChat }
            }
        } else {
            if (selectedTab == 0) singleAiChats else groupChats
        }
    }

    val listState = rememberLazyListState()
    var isCollapsed by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (index == 0 && offset < 50) {
                isCollapsed = false
            } else if (index > 0 || offset > 200) {
                isCollapsed = true
            }
        }
    }

    // 渐变背景色
    val gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFF5E6), // 浅橙色
            Color(0xFFFCE4D6), // 浅粉橙色
            Color(0xFFF5F5F5)  // 底部灰白色
        )
    )

    Scaffold(
        floatingActionButton = {
            if (!isSearchMode) {
                AddAiChatButton(
                    isGroupTab = selectedTab == 1,
                    onAddAiChat = {
                        // 单聊：显示创建AI对话框
                        showCreateAiDialog = true
                    },
                    onAddGroupChat = {
                        // 群聊：显示选择AI对话框
                        showCreateGroupDialog = true
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(innerPadding)
        ) {
            // 顶部标题栏
            AiListHeader(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                isCollapsed = isCollapsed
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState
            ) {
                item {
                    SearchBarItem(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { 
                            searchQuery = it 
                            isSearchMode = true
                        },
                        onSearchExecute = { isSearchMode = true },
                        onClear = { 
                            searchQuery = ""
                            isSearchMode = false
                        },
                        isDiary = false
                    )
                }
                
                items(filteredList, key = { it.id }) { chat ->
                    val chatAiConfigs = aiList.filter { it.chatId == chat.id }
                    val primaryAiConfig = chatAiConfigs.firstOrNull()
                    
                    SwipeableAiCard(
                        chat = chat,
                        aiConfig = primaryAiConfig,
                        aiCount = chatAiConfigs.size,
                        isGroupChat = selectedTab == 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .animateItem()
                            .clickable { 
                                if (selectedTab == 0) onAiChatClick(chat) 
                                else onGroupChatClick(chat) 
                            },
                        onDelete = { viewModel.deleteChat(chat) }
                    )
                }
            }
        }
    }
    
    // 创建AI对话框
    if (showCreateAiDialog) {
        // 获取已存在的文件夹名列表
        val existingFolderNames = remember(tagFolders) {
            tagFolders.map { it.name }
        }
        
        CreateAiDialog(
            existingFolderNames = existingFolderNames,
            onDismiss = { showCreateAiDialog = false },
            onConfirm = { aiName, folderName ->
                viewModel.createAiChatWithNewAi(aiName, folderName)
                showCreateAiDialog = false
            }
        )
    }
    
    // 创建群聊对话框
    if (showCreateGroupDialog) {
        // 获取所有不重复的AI（从单聊中获取）
        val availableAis = remember(singleAiChats, aiList) {
            singleAiChats.mapNotNull { chat ->
                aiList.firstOrNull { it.chatId == chat.id }
            }.distinctBy { it.name } // 按名称去重，避免同名AI
        }
        
        CreateGroupChatDialog(
            availableAis = availableAis,
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { selectedAis ->
                viewModel.createGroupChat(selectedAis)
                showCreateGroupDialog = false
            }
        )
    }
}


/**
 * 顶部标题栏 - 包含Tab切换
 */
@Composable
fun AiListHeader(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    isCollapsed: Boolean = false
) {
    val topPadding by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isCollapsed) 10.dp else 40.dp, 
        label = "topPadding"
    )
    val titleFontSize by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isCollapsed) 20f else 24f, 
        label = "titleFontSize"
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp, topPadding, 20.dp, 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AI助手列表 Tab
            Text(
                text = "AI助手列表",
                fontSize = titleFontSize.sp,
                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedTab == 0) Color.Black else Color.Gray,
                modifier = Modifier.clickable { onTabSelected(0) }
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 群聊 Tab
            Text(
                text = "群聊",
                fontSize = titleFontSize.sp,
                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                color = if (selectedTab == 1) Color.Black else Color.Gray,
                modifier = Modifier.clickable { onTabSelected(1) }
            )
            
            Spacer(modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(if(isCollapsed) 10.dp else 20.dp))
    }
}


/**
 * 滑动删除AI卡片
 */
@Composable
fun SwipeableAiCard(
    chat: Chat,
    aiConfig: AiChatConfig?,
    aiCount: Int = 1,
    isGroupChat: Boolean = false,
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
        content = { 
            AiChatCard(
                chat = chat, 
                aiConfig = aiConfig, 
                aiCount = aiCount,
                isGroupChat = isGroupChat
            ) 
        }
    )

    if (showDialog) {
        DeleteAiChatDialog(
            title = aiConfig?.name ?: chat.title,
            onConfirm = { onDelete(); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}


/**
 * AI聊天卡片 - 新样式（参考图片设计）
 */
@Composable
fun AiChatCard(
    chat: Chat, 
    aiConfig: AiChatConfig?, 
    aiCount: Int = 1,
    isGroupChat: Boolean = false
) {
    val formattedDate = remember(chat.date) {
        try {
            val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(chat.date)
            SimpleDateFormat("M月d日", Locale.CHINA).format(dateTime!!)
        } catch (e: Exception) {
            chat.date
        }
    }
    
    // AI头像背景色 - 根据名称生成不同颜色
    val avatarColors = listOf(
        Color(0xFF5B9BD5), // 蓝色
        Color(0xFF7BC67E), // 绿色
        Color(0xFFE57373), // 粉红色
        Color(0xFFFFB74D), // 橙色
        Color(0xFF9575CD), // 紫色
        Color(0xFF4DD0E1), // 青色
    )
    val avatarColor = remember(aiConfig?.name) {
        val index = abs(aiConfig?.name?.hashCode() ?: 0) % avatarColors.size
        avatarColors[index]
    }
    
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp, 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AI头像
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                if (aiConfig != null && aiConfig.avatarUri.isNotBlank()) {
                    AsyncImage(
                        model = aiConfig.avatarUri,
                        contentDescription = "AI头像",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = (aiConfig?.name ?: "AI").take(2),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // AI名称
                Text(
                    text = if (isGroupChat) {
                        "${aiConfig?.name ?: "群聊"} (${aiCount}人)"
                    } else {
                        aiConfig?.name ?: "AI助手"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 日期 | 消息数
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$formattedDate | ${chat.unreadCount}",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }
            
            // 未读红点
            if (chat.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        }
    }
}


/**
 * 添加AI/群聊按钮
 */
@Composable
fun AddAiChatButton(
    isGroupTab: Boolean,
    onAddAiChat: () -> Unit,
    onAddGroupChat: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(MaterialTheme.diaryColors.primary),
        contentPadding = PaddingValues(0.dp),
        onClick = { if (isGroupTab) onAddGroupChat() else onAddAiChat() },
        interactionSource = interactionSource,
        modifier = Modifier
            .padding(bottom = 20.dp, end = 12.dp)
            .size(64.dp)
            .pressScaleEffect(interactionSource = interactionSource)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "添加",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}


/**
 * 删除确认对话框
 */
@Composable
fun DeleteAiChatDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除与「$title」的对话吗？") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}


/**
 * 创建群聊对话框 - 选择多个AI
 */
@Composable
fun CreateGroupChatDialog(
    availableAis: List<AiChatConfig>,
    onDismiss: () -> Unit,
    onConfirm: (List<AiChatConfig>) -> Unit
) {
    val selectedAis = remember { mutableStateOf(setOf<Long>()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建群聊", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("选择要加入群聊的AI：", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                
                if (availableAis.isEmpty()) {
                    Text("暂无可用的AI，请先创建AI助手", color = Color.Gray)
                } else {
                    availableAis.forEach { ai ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAis.value = if (selectedAis.value.contains(ai.id)) {
                                        selectedAis.value - ai.id
                                    } else {
                                        selectedAis.value + ai.id
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedAis.value.contains(ai.id),
                                onCheckedChange = { checked ->
                                    selectedAis.value = if (checked) {
                                        selectedAis.value + ai.id
                                    } else {
                                        selectedAis.value - ai.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(ai.name, fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selected = availableAis.filter { selectedAis.value.contains(it.id) }
                    if (selected.size >= 2) {
                        onConfirm(selected)
                    }
                },
                enabled = selectedAis.value.size >= 2
            ) { 
                Text("创建 (${selectedAis.value.size})") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}


/**
 * 创建AI对话框 - 输入AI名称和记忆库文件夹名
 */
@Composable
fun CreateAiDialog(
    existingFolderNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (aiName: String, folderName: String) -> Unit
) {
    var aiName by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("") }
    var folderError by remember { mutableStateOf<String?>(null) }
    
    // 检查文件夹名是否重复
    fun validateFolderName(name: String): String? {
        return when {
            name.isBlank() -> "文件夹名不能为空"
            existingFolderNames.contains(name) -> "文件夹名已存在，请输入其他名称"
            else -> null
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建AI助手", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("AI名称", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = aiName,
                    onValueChange = { aiName = it },
                    placeholder = { Text("输入AI名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("记忆库文件夹", color = Color.Gray, fontSize = 14.sp)
                Text("AI的专属记忆库，用于存储笔记和资料", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { 
                        folderName = it
                        folderError = validateFolderName(it)
                    },
                    placeholder = { Text("输入文件夹名称") },
                    singleLine = true,
                    isError = folderError != null,
                    supportingText = {
                        if (folderError != null) {
                            Text(folderError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val error = validateFolderName(folderName)
                    if (error == null && aiName.isNotBlank()) {
                        onConfirm(aiName.ifBlank { "AI助手" }, folderName)
                    } else {
                        folderError = error
                    }
                },
                enabled = aiName.isNotBlank() && folderName.isNotBlank() && folderError == null
            ) { 
                Text("创建") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
