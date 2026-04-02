package com.xinkong.diary.ui.screen.tag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.ViewModel.TagModel
import com.xinkong.diary.repository.TagFolder
import com.xinkong.diary.ui.screen.home.TagAdd
import com.xinkong.diary.ui.screen.home.TagUI
import com.xinkong.diary.ui.screen.tag.UNCLASSIFIED_TAG_NAME

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManageScreen(
    onBack: () -> Unit,
    groupedTags: Map<String, List<TagUI>>,
    currentFolder: String,
    onAddFolder: (String) -> Unit,
    onEditFolder: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onEditTag: (TagUI) -> Unit,
    onSwitchFolder: (String) -> Unit
) {
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理分类") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            groupedTags.forEach { (folderName, tags) ->
                item(key = folderName) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column {
                            // First row: Folder name and Add Tag button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSwitchFolder(folderName) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(folderName, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                
                                // 编辑按钮（只有非默认文件夹才显示）
                                if (folderName != "我的笔记") {
                                    IconButton(onClick = { onEditFolder(folderName) }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "编辑文件夹",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                                
                                TextButton(onClick = { onAddTag(folderName) }) {
                                    Text("新增")
                                }
                            }
                            Divider(color = Color.LightGray, thickness = 1.dp)

                            // Tags rows
                            tags.filter { it.name != "未分类" }.forEach { tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onEditTag(tag) }
                                        .padding(vertical = 12.dp, horizontal = 32.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = tag.color)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(tag.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
//                                    Icon(Icons.Default.Adjust, contentDescription = "Edit", tint = Color.Gray)
                                }
                                Divider(color = Color.LightGray, thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }

            // Add new folder card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable { showAddFolderDialog = true },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("新建文件夹", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showAddFolderDialog) {
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("文件夹名称") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onAddFolder(newFolderName)
                        }
                        showAddFolderDialog = false
                        newFolderName = ""
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun TagManageRoute(
    type: String,
    onBack: () -> Unit,
    tagModel: TagModel = viewModel(),
    diaryViewModel: DiaryViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val diaryDbTags by tagModel.diaryTags.collectAsStateWithLifecycle()
    val chatDbTags by tagModel.chatTags.collectAsStateWithLifecycle()
    val dbFolders by tagModel.tagFolders.collectAsStateWithLifecycle()
    val diaryList by diaryViewModel.listState.collectAsStateWithLifecycle()
    val chatList by chatViewModel.chatListState.collectAsStateWithLifecycle()
    val currentFolder by diaryViewModel.currentFolder.collectAsStateWithLifecycle()

    val folderType = if (type == "diary") "Diary" else "Chat"

    val groupedResult = remember(type, diaryList, chatList, diaryDbTags, chatDbTags, dbFolders) {
        if (type == "diary") {
            tagModel.buildDiaryGroupedTags(diaryList, includeHidden = true)
        } else {
            tagModel.buildChatGroupedTags(chatList, includeHidden = true)
        }
    }

    val groupedTags = remember(groupedResult) {
        groupedResult.groupedTags.mapValues { (_, items) ->
            items.map { item ->
                TagUI(
                    name = item.name,
                    color = Color(item.colorInt),
                    background2 = Color(item.bg2Int),
                    border2 = Color(item.border2Int),
                    folder = item.folder,
                    displayName = item.displayName,
                    itemCount = item.itemCount
                )
            }
        }
    }

    val uniqueFolders = groupedResult.availableFolders

    var showTagAdd by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<TagUI?>(null) }
    var defaultFolderAdd by remember { mutableStateOf("我的笔记") }

    var editingFolder by remember { mutableStateOf<String?>(null) }

    TagManageScreen(
        onBack = onBack,
        groupedTags = groupedTags,
        currentFolder = currentFolder,
        onAddFolder = { folderName ->
            tagModel.addTagFolder(
                TagFolder(
                    name = folderName,
                    type = folderType,
                    isHidden = false
                )
            )
        },
        onEditFolder = { folderName ->
            editingFolder = folderName
        },
        onAddTag = { folderName ->
            editingTag = null
            defaultFolderAdd = folderName
            showTagAdd = true
        },
        onEditTag = { tagUI ->
            editingTag = tagUI
            showTagAdd = true
        },
        onSwitchFolder = { folderName ->
            diaryViewModel.updateCurrentFolder(folderName)
            onBack()
        }
    )

    if (showTagAdd) {
        TagAdd(
            initialName = editingTag?.name ?: "",
            initialColor = editingTag?.color ?: com.xinkong.diary.ui.theme.ColorPalette[0],
            initialBackground2 = editingTag?.background2 ?: com.xinkong.diary.ui.theme.ThemeDefault.background2,
            initialBorder2 = editingTag?.border2 ?: com.xinkong.diary.ui.theme.ThemeDefault.border2,
            initialFolder = editingTag?.folder ?: defaultFolderAdd,
            availableFolders = uniqueFolders,
            isEditMode = editingTag != null,
            onDismiss = { showTagAdd = false },
            onDelete = if (editingTag != null) {
                {
                    if (type == "diary") {
                        diaryViewModel.listState.value
                            .filter { it.tag == editingTag!!.name && it.tagFolder == editingTag!!.folder }
                            .forEach { diary ->
                                diaryViewModel.updateDiary(
                                    diary.copy(
                                        tag = UNCLASSIFIED_TAG_NAME,
                                        tagFolder = editingTag!!.folder
                                    )
                                )
                            }
                        val toDelete = tagModel.diaryTags.value.find {
                            it.name == editingTag!!.name && it.folder == editingTag!!.folder
                        }
                        toDelete?.let { tagModel.deleteDiaryTag(it) }
                    } else {
                        chatViewModel.chatListState.value
                            .filter { it.tag == editingTag!!.name && it.tagFolder == editingTag!!.folder }
                            .forEach { chat ->
                                chatViewModel.updateChat(
                                    chat.copy(
                                        tag = UNCLASSIFIED_TAG_NAME,
                                        tagFolder = editingTag!!.folder
                                    )
                                )
                            }
                        val toDelete = tagModel.chatTags.value.find {
                            it.name == editingTag!!.name && it.folder == editingTag!!.folder
                        }
                        toDelete?.let { tagModel.deleteChatTag(it) }
                    }
                    showTagAdd = false
                }
            } else null,
            onConfirm = { name, color, background2, border2, folder ->
                if (type == "diary") {
                    val targetExists = tagModel.diaryTags.value.any { it.name == name && it.folder == folder }
                    val previous = editingTag

                    if (previous == null) {
                        if (targetExists) {
                            android.widget.Toast.makeText(context, "该文件夹下已存在同名分类", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            tagModel.addDiaryTag(
                                com.xinkong.diary.repository.DiaryTag(
                                    name = name,
                                    colorInt = color.toArgb(),
                                    bg2Int = background2.toArgb(),
                                    border2Int = border2.toArgb(),
                                    folder = folder
                                )
                            )
                        }
                    } else {
                        val sourceChanged = previous.name != name || previous.folder != folder
                        if (sourceChanged) {
                            diaryViewModel.listState.value
                                .filter { it.tag == previous.name && it.tagFolder == previous.folder }
                                .forEach { diary ->
                                    diaryViewModel.updateDiary(
                                        diary.copy(
                                            tag = name,
                                            tagFolder = folder
                                        )
                                    )
                                }
                        }

                        val toDelete = tagModel.diaryTags.value.find {
                            it.name == previous.name && it.folder == previous.folder
                        }
                        if (sourceChanged) {
                            toDelete?.let { tagModel.deleteDiaryTag(it) }
                        }

                        if (sourceChanged && targetExists) {
                            android.widget.Toast.makeText(context, "目标文件夹已存在同名分类，笔记已合并！", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            tagModel.addDiaryTag(
                                com.xinkong.diary.repository.DiaryTag(
                                    name = name,
                                    colorInt = color.toArgb(),
                                    bg2Int = background2.toArgb(),
                                    border2Int = border2.toArgb(),
                                    folder = folder
                                )
                            )
                        }
                    }

                } else {
                    val targetExists = tagModel.chatTags.value.any { it.name == name && it.folder == folder }
                    val previous = editingTag

                    if (previous == null) {
                        if (targetExists) {
                            android.widget.Toast.makeText(context, "该文件夹下已存在同名分类", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            tagModel.addChatTag(
                                com.xinkong.diary.repository.ChatTag(
                                    name = name,
                                    colorInt = color.toArgb(),
                                    bg2Int = background2.toArgb(),
                                    border2Int = border2.toArgb(),
                                    folder = folder
                                )
                            )
                        }
                    } else {
                        val sourceChanged = previous.name != name || previous.folder != folder
                        if (sourceChanged) {
                            chatViewModel.chatListState.value
                                .filter { it.tag == previous.name && it.tagFolder == previous.folder }
                                .forEach { chat ->
                                    chatViewModel.updateChat(
                                        chat.copy(
                                            tag = name,
                                            tagFolder = folder
                                        )
                                    )
                                }
                        }

                        val toDelete = tagModel.chatTags.value.find {
                            it.name == previous.name && it.folder == previous.folder
                        }
                        if (sourceChanged) {
                            toDelete?.let { tagModel.deleteChatTag(it) }
                        }

                        if (sourceChanged && targetExists) {
                            android.widget.Toast.makeText(context, "目标文件夹已存在同名分类，对话已合并！", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            tagModel.addChatTag(
                                com.xinkong.diary.repository.ChatTag(
                                    name = name,
                                    colorInt = color.toArgb(),
                                    bg2Int = background2.toArgb(),
                                    border2Int = border2.toArgb(),
                                    folder = folder
                                )
                            )
                        }
                    }
                }
                showTagAdd = false
            }
        )
    }

    if (editingFolder != null) {
        var newFolderName by remember { mutableStateOf(editingFolder!!) }
        AlertDialog(
            onDismissRequest = { editingFolder = null },
            containerColor = Color.White,
            title = { Text("编辑文件夹") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("文件夹名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newFolderName.isNotBlank() && newFolderName != editingFolder) {
                        // Rename folder
                        if (type == "diary") {
                            tagModel.diaryTags.value.filter { it.folder == editingFolder }.forEach { dbTag ->
                                val targetExists = tagModel.diaryTags.value.any {
                                    it.folder == newFolderName && it.name == dbTag.name
                                }
                                if (!targetExists) {
                                    tagModel.addDiaryTag(dbTag.copy(folder = newFolderName))
                                }
                                diaryViewModel.listState.value
                                    .filter { it.tag == dbTag.name && it.tagFolder == editingFolder }
                                    .forEach { diary ->
                                        diaryViewModel.updateDiary(
                                            diary.copy(tag = dbTag.name, tagFolder = newFolderName)
                                        )
                                    }
                                tagModel.deleteDiaryTag(dbTag)
                            }
                        } else {
                            tagModel.chatTags.value.filter { it.folder == editingFolder }.forEach { dbTag ->
                                val targetExists = tagModel.chatTags.value.any {
                                    it.folder == newFolderName && it.name == dbTag.name
                                }
                                if (!targetExists) {
                                    tagModel.addChatTag(dbTag.copy(folder = newFolderName))
                                }
                                chatViewModel.chatListState.value
                                    .filter { it.tag == dbTag.name && it.tagFolder == editingFolder }
                                    .forEach { chat ->
                                        chatViewModel.updateChat(
                                            chat.copy(tag = dbTag.name, tagFolder = newFolderName)
                                        )
                                    }
                                tagModel.deleteChatTag(dbTag)
                            }
                        }
                    }
                    editingFolder = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                // 检查是否是AI绑定的文件夹
                val folderEntity = tagModel.tagFolders.value.firstOrNull {
                    it.name == editingFolder && it.type == folderType
                }
                val isAiBoundFolder = folderEntity?.isAiBound == true
                
                if (!isAiBoundFolder) {
                    TextButton(onClick = {
                        val folderToDelete = editingFolder ?: return@TextButton
                        // Delete folder
                        if (type == "diary") {
                            tagModel.diaryTags.value.filter { it.folder == folderToDelete }.forEach { dbTag ->
                                diaryViewModel.listState.value
                                    .filter { it.tag == dbTag.name && it.tagFolder == folderToDelete }
                                    .forEach { diary ->
                                        diaryViewModel.updateDiary(
                                            diary.copy(
                                                tag = UNCLASSIFIED_TAG_NAME,
                                                tagFolder = DEFAULT_TAG_FOLDER
                                            )
                                        )
                                    }
                                tagModel.deleteDiaryTag(dbTag)
                            }
                        } else {
                            tagModel.chatTags.value.filter { it.folder == folderToDelete }.forEach { dbTag ->
                                chatViewModel.chatListState.value
                                    .filter { it.tag == dbTag.name && it.tagFolder == folderToDelete }
                                    .forEach { chat ->
                                        chatViewModel.updateChat(
                                            chat.copy(
                                                tag = UNCLASSIFIED_TAG_NAME,
                                                tagFolder = DEFAULT_TAG_FOLDER
                                            )
                                        )
                                    }
                                tagModel.deleteChatTag(dbTag)
                            }
                        }
                        val folder = tagModel.tagFolders.value.firstOrNull {
                            it.name == folderToDelete && it.type == folderType
                        } ?: TagFolder(name = folderToDelete, type = folderType, isHidden = false)
                        tagModel.deleteTagFolder(folder)
                        editingFolder = null
                    }) {
                        Text("删除", color = Color.Red)
                    }
                } else {
                    // AI绑定的文件夹显示提示
                    Text(
                        "AI绑定文件夹",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        )
    }
}