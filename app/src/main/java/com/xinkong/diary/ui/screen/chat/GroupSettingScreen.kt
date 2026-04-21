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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.xinkong.diary.repository.GroupChatMember
import com.xinkong.diary.repository.UserChatConfig
import com.xinkong.diary.ui.screen.home.SettingSectionHeader
import com.xinkong.diary.ui.theme.diaryColors

/**
 * 群聊专用设置界面
 * - 显示群聊成员（引用的源AI）
 * - 点击AI头像导航到源AI设置界面
 * - 可以添加/移除AI、调整回复顺序
 */
@Composable
fun GroupSettingScreen(
    chat: Chat,
    onBack: () -> Unit,
    onDeleteGroupChat: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBackgroundChange: (String) -> Unit,
    onGroupAvatarChange: (String) -> Unit,
    onHistoryRoundsChange: (Int) -> Unit,
    onAiClick: (sourceAiId: Long) -> Unit,  // 点击AI导航到源AI设置界面
    onUserClick: () -> Unit = {}  // 点击用户导航到用户设置
) {
    val chatViewModel: ChatViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 获取群聊成员列表
    val members by chatViewModel.getGroupChatMembers(chat.id)
        .collectAsStateWithLifecycle(emptyList())
    
    // 获取群聊中引用的源AI配置（用于显示）
    val sourceAiConfigs by chatViewModel.getGroupChatSourceAiConfigs(chat.id)
        .collectAsStateWithLifecycle(emptyList())
    
    val userConfig by chatViewModel.findUserConfig(chat.id)
        .collectAsStateWithLifecycle(UserChatConfig(chatId = chat.id))
    
    // 获取所有可用的AI（用于添加对话框）
    val allChats by chatViewModel.chatListState.collectAsStateWithLifecycle(emptyList())
    val availableAis = remember(allChats) {
        allChats.filter { !it.isGroupChat }
    }
    
    var showAddAiDialog by remember { mutableStateOf(false) }
    var showRemoveConfirmDialog by remember { mutableStateOf<GroupChatMember?>(null) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }

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
        topBar = {
            GroupSettingTopBar(
                onBack = onBack,
                onDelete = { showDeleteGroupDialog = true }
            )
        }
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
            
            // 基本设置
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
                
                // 背景设置
                BackgroundSettingRow(
                    hasBackground = chat.backgroundUri.isNotEmpty(),
                    onClick = { backgroundPicker.launch("image/*") }
                )
                
                // 群聊头像设置
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
                            onValueChange = { historyRounds = it.toInt() },
                            onValueChangeFinished = { onHistoryRoundsChange(historyRounds) },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = MaterialTheme.diaryColors.primary,
                                activeTrackColor = MaterialTheme.diaryColors.primary
                            )
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
            
            // 群聊成员管理
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    "群聊成员",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 用户项（用户在不同群聊可以有不同身份）
                UserMemberItem(
                    userConfig = userConfig,
                    onClick = onUserClick
                )
                Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                
                // AI成员列表
                members.forEach { member ->
                    val aiConfig = sourceAiConfigs.find { 
                        it.id == member.sourceAiId 
                    }
                    if (aiConfig != null) {
                        GroupMemberItem(
                            member = member,
                            aiConfig = aiConfig,
                            onAiClick = { onAiClick(member.sourceAiId) },
                            onRemove = { showRemoveConfirmDialog = member }
                        )
                        Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp)
                    }
                }
                
                // 添加AI按钮
                AddMemberButton(onClick = { showAddAiDialog = true })
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // 添加AI对话框
    if (showAddAiDialog) {
        AddAiToGroupDialogNew(
            availableChats = availableAis,
            existingMembers = members,
            chatViewModel = chatViewModel,
            groupChatId = chat.id,
            onDismiss = { showAddAiDialog = false }
        )
    }
    
    // 移除确认对话框
    showRemoveConfirmDialog?.let { member ->
        val aiConfig = sourceAiConfigs.find { it.id == member.sourceAiId }
        AlertDialog(
            onDismissRequest = { showRemoveConfirmDialog = null },
            title = { Text("移除成员") },
            text = { Text("确定要将 ${aiConfig?.name ?: "该AI"} 从群聊中移除吗？") },
            confirmButton = {
                Button(onClick = {
                    chatViewModel.removeAiFromGroupChat(chat.id, member.sourceAiId)
                    showRemoveConfirmDialog = null
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmDialog = null }) { Text("取消") }
            }
        )
    }

    if (showDeleteGroupDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除该群聊吗？") },
            containerColor = Color.White,
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteGroupDialog = false
                        onDeleteGroupChat()
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun GroupSettingTopBar(onBack: () -> Unit, onDelete: () -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .background(Color.White)
                .fillMaxWidth()
                .padding(0.dp, 36.dp, 0.dp, 4.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }

            Text(
                text = "群聊设置",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除群聊"
                )
            }
        }
        Divider(color = Color.LightGray, thickness = 0.5.dp)
    }
}

/**
 * 用户成员项（用户在不同群聊可以有不同身份）
 */
@Composable
fun UserMemberItem(
    userConfig: UserChatConfig,
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
        // 用户头像
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF5B9BD5)),
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
                Text(
                    userConfig.name.take(1).ifEmpty { "我" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 用户信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                userConfig.name.ifEmpty { "用户" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                "我",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

/**
 * 添加成员按钮
 */
@Composable
fun AddMemberButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 添加图标
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "添加",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            "添加AI",
            fontSize = 15.sp,
            color = Color.Gray
        )
    }
}

/**
 * 群聊成员列表项
 */
@Composable
fun GroupMemberItem(
    member: GroupChatMember,
    aiConfig: AiChatConfig,
    onAiClick: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAiClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // AI头像
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F5E9)),
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
                Text(
                    aiConfig.name.take(1),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.diaryColors.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // AI信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                aiConfig.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (member.isEnabled) Color.Black else Color.Gray
            )
            if (aiConfig.model.isNotEmpty()) {
                Text(
                    aiConfig.model,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        
        // 移除按钮
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "移除",
                tint = Color.Gray
            )
        }
    }
}

/**
 * 添加AI到群聊的对话框（引用模式）
 */
@Composable
fun AddAiToGroupDialogNew(
    availableChats: List<Chat>,
    existingMembers: List<GroupChatMember>,
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
                    LazyColumn {
                        items(aiList) { aiConfig ->
                            // 检查是否已在群聊中（通过sourceAiId判断）
                            val isAlreadyAdded = existingMembers.any { it.sourceAiId == aiConfig.id }
                            
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
