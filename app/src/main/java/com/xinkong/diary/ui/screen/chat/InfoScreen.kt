package com.xinkong.diary.ui.screen.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.repository.UserChatConfig
import com.xinkong.diary.repository.Diary
import com.xinkong.diary.ui.screen.home.AiSection
import com.xinkong.diary.ui.screen.home.SettingSectionHeader
import com.xinkong.diary.ui.theme.diaryColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlin.text.ifEmpty


@Composable
fun DetailScreen(
    chat: Chat,
    role: String,
    onBack: () -> Unit
) {
    if (role == "user") {
        UserConfig(chat = chat, onBack = onBack)
    } else {
        AiConfig(chat = chat, onBack = onBack)
    }
}



@Composable
fun AiConfig(chat: Chat,onBack: () -> Unit){

    val chatViewModel: ChatViewModel = viewModel()
    val config by chatViewModel.findAiConfig(chat.id)
        .collectAsStateWithLifecycle(AiChatConfig(chatId = chat.id))

    var enableAllNotes by remember(config.enableAllNotes) { mutableStateOf(config.enableAllNotes) }


    var configExpanded by remember { mutableStateOf(true) }
    var functionExpanded by remember { mutableStateOf(true) }



    Scaffold(
        topBar = {
            DetailTopBar(
                title ="AI 信息",
                onBack = onBack
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
            Spacer(modifier = Modifier.height(32.dp))

            //头像设置
            AvatarIcon(role = "AI", avatarUri = config.avatarUri,
                onSave = {avatarUri ->
                    chatViewModel.updateAiConfig(
                        config.copy(avatarUri = avatarUri)
                    )
                })


            Spacer(modifier = Modifier.height(8.dp))

            // 角色信息
            Text(
                text ="AI 助手",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ---- 配置栏 & 功能栏 ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {



                //      上下文设置
                SettingSectionHeader(
                    title = "配置栏",
                    isExpanded = configExpanded,
                    onClick = { configExpanded = !configExpanded }
                )
                if (configExpanded) {
                    ContextConfig(config)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.5f))







                // ========== 功能栏 ==========
                SettingSectionHeader(
                    title = "功能栏",
                    isExpanded = functionExpanded,
                    onClick = { functionExpanded = !functionExpanded }
                )

                if (functionExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "是否让AI获取所有笔记类型",
                            fontSize = 14.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = enableAllNotes,
                            onCheckedChange = { checked ->
                                enableAllNotes = checked
                                chatViewModel.updateAiConfig(
                                    config.copy(enableAllNotes = checked)
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.diaryColors.primary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }


}


@Composable
fun UserConfig(chat: Chat, onBack: () -> Unit) {

    val chatViewModel: ChatViewModel = viewModel()
    val config by chatViewModel.findUserConfig(chat.id)
        .collectAsStateWithLifecycle(UserChatConfig(chatId = chat.id))

    var configExpanded by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            DetailTopBar(
                title = "用户信息",
                onBack = onBack
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
            Spacer(modifier = Modifier.height(32.dp))

            // 头像设置
            AvatarIcon(role = "user", avatarUri = config.avatarUri,
                onSave = { avatarUri ->
                    chatViewModel.updateUserConfig(
                        config.copy(avatarUri = avatarUri)
                    )
                })

            Spacer(modifier = Modifier.height(8.dp))

            // 角色信息
            Text(
                text = "用户",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ---- 上下文设置 ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                SettingSectionHeader(
                    title = "用户资料",
                    isExpanded = configExpanded,
                    onClick = { configExpanded = !configExpanded }
                )
                if (configExpanded) {
                    UserContextConfig(config)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


@Composable
fun UserContextConfig(config: UserChatConfig) {

    val diaryViewModel: DiaryViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()

    val referencedIds = remember(config.referencedDiaryId) {
        try {
            Json.decodeFromString<List<Long>>(config.referencedDiaryId)
        } catch (e: Exception) {
            emptyList()
        }
    }
    var currentReferencedIds by remember(referencedIds) { mutableStateOf(referencedIds) }

    val allDiaries by diaryViewModel.listState.collectAsStateWithLifecycle()
    val referencedDiaries = remember(allDiaries, currentReferencedIds) {
        allDiaries.filter { it.id in currentReferencedIds }
    }

    var showAddNoteDialog by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        "上下文笔记",
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Gray,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    if (referencedDiaries.isNotEmpty()) {
        DiaryColumn(referencedDiaries = referencedDiaries,
            onDismiss = { diary ->
                currentReferencedIds = currentReferencedIds - diary.id
                chatViewModel.updateUserConfig(
                    config.copy(
                        referencedDiaryId = Json.encodeToString(currentReferencedIds)
                    )
                )
            })
    } else {
        Text(
            "暂无上下文笔记",
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = { showAddNoteDialog = true },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.diaryColors.primary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("添加笔记作为上下文")
    }

    if (showAddNoteDialog) {
        AddNoteDialog(
            allDiaries = allDiaries,
            alreadySelected = currentReferencedIds,
            onDismiss = { showAddNoteDialog = false },
            onConfirm = { selectedIds ->
                currentReferencedIds = selectedIds
                chatViewModel.updateUserConfig(
                    config.copy(
                        referencedDiaryId = Json.encodeToString(selectedIds)
                    )
                )
                showAddNoteDialog = false
            }
        )
    }
}


@Composable
fun AvatarIcon(role: String,avatarUri: String, onSave:(String)-> Unit){

    val context = LocalContext.current
    var avatarUri by remember(avatarUri) { mutableStateOf(avatarUri) }


    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 持久化 URI 权限
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            avatarUri = it.toString()
            onSave(avatarUri)
        }
    }
    // ---- 头像区域 ----
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(if (role == "user") Color(0xFF6CB4EE) else Color(0xFF5B9BD5))
            .clickable { imagePickerLauncher.launch("image/*") }
    ) {
        if (avatarUri.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "头像",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = if (role == "user") "我" else "AI",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))

    // 选择照片按钮
    TextButton(
        onClick = { imagePickerLauncher.launch("image/*") }
    ) {
        Text("选择照片", color = MaterialTheme.diaryColors.primary)
    }
}





@Composable
fun DiaryColumn(referencedDiaries: List<Diary>,onDismiss:(Diary)-> Unit){
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        referencedDiaries.forEachIndexed { index, diary ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        diary.title.ifEmpty { "无标题" },
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        diary.text.take(30) + if (diary.text.length > 30) "..." else "",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onDismiss(diary) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "移除",
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (index < referencedDiaries.size - 1) {
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
            }
        }
    }
}







@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit
) {
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
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Divider(color = Color.LightGray, thickness = 0.5.dp)
    }
}

@Composable
fun AddNoteDialog(
    allDiaries: List<Diary>,
    alreadySelected: List<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(alreadySelected.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择笔记作为上下文", color = MaterialTheme.diaryColors.primary)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "选择的笔记内容将作为AI对话的上下文",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        if (allDiaries.isEmpty()) {
                            Text(
                                "暂无笔记",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            allDiaries.forEach { diary ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIds = if (selectedIds.contains(diary.id)) {
                                                selectedIds - diary.id
                                            } else {
                                                selectedIds + diary.id
                                            }
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedIds.contains(diary.id),
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) {
                                                selectedIds + diary.id
                                            } else {
                                                selectedIds - diary.id
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.diaryColors.primary
                                        )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            diary.title.ifEmpty { "无标题" },
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            diary.text.take(40) + if (diary.text.length > 40) "..." else "",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIds.toList()) }) {
                Text("确定", color = MaterialTheme.diaryColors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.Gray)
            }
        },
        containerColor = Color.White
    )
}


@Composable
fun ContextConfig(config: AiChatConfig){

    val diaryViewModel: DiaryViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()

    // 解析已引用的日记ID列表
    val referencedIds = remember(config.referencedDiaryId) {
        try {
            Json.decodeFromString<List<Long>>(config.referencedDiaryId)
        } catch (e: Exception) {
            emptyList()
        }
    }
    var currentReferencedIds by remember(referencedIds) { mutableStateOf(referencedIds) }

    // 获取所有笔记列表
    val allDiaries by diaryViewModel.listState.collectAsStateWithLifecycle()
    // 获取已引用的笔记
    val referencedDiaries = remember(allDiaries, currentReferencedIds) {
        allDiaries.filter { it.id in currentReferencedIds }
    }

    var showAddNoteDialog by remember { mutableStateOf(false) }




    Spacer(modifier = Modifier.height(8.dp))

    // 上下文标签
    Text(
        "上下文笔记",
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color.Gray,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    // 已添加的笔记列表
    if (referencedDiaries.isNotEmpty()) {
        DiaryColumn(referencedDiaries = referencedDiaries,
            onDismiss = {diary ->                      // 取消该笔记引用
                currentReferencedIds = currentReferencedIds - diary.id
                chatViewModel.updateAiConfig(
                    config.copy(
                        referencedDiaryId = Json.encodeToString(currentReferencedIds)
                    )
                )})

    } else {
        Text(
            "暂无上下文笔记",
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 添加笔记按钮
    Button(
        onClick = { showAddNoteDialog = true },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.diaryColors.primary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("添加笔记作为上下文")
    }

    // ---- 添加笔记对话框 ----
    if (showAddNoteDialog) {
        AddNoteDialog(
            allDiaries = allDiaries,
            alreadySelected = currentReferencedIds,
            onDismiss = { showAddNoteDialog = false },
            onConfirm = { selectedIds ->
                currentReferencedIds = selectedIds
                chatViewModel.updateAiConfig(
                    config.copy(
                        referencedDiaryId = Json.encodeToString(selectedIds)
                    )
                )
                showAddNoteDialog = false
            }
        )
    }
}