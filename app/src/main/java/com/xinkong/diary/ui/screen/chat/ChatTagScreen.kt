package com.xinkong.diary.ui.screen.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.ViewModel.TagModel
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.ChatTag
import androidx.compose.foundation.lazy.items
import com.xinkong.diary.ui.screen.home.TagCard
import com.xinkong.diary.ui.screen.home.TagListDisplayConfig
import com.xinkong.diary.ui.screen.home.TagUI
import com.xinkong.diary.ui.screen.tag.UNCLASSIFIED_TAG_NAME
import com.xinkong.diary.ui.theme.currentDiaryColors
import com.xinkong.diary.ui.theme.diaryColors
import kotlin.collections.minus
import kotlin.collections.plus

private const val CHAT_TAG_FOLDER = "我的笔记"


//----------------对话分类面板（包装卡片）--------------------
@Composable
fun ChatTagSetting(
    chatList: List<Chat>,
    selectedTag: String = UNCLASSIFIED_TAG_NAME,
    onTagSelect: (String) -> Unit = {},
    onTagsDelete: (List<String>) -> Unit = {},
    displayConfig: TagListDisplayConfig = TagListDisplayConfig()
) {
    Card(
        modifier = Modifier
            .padding(5.dp, 0.dp)
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        ChatTagList(
            chatList = chatList,
            selectedTag = selectedTag,
            onTagSelect = onTagSelect,
            onTagsDelete = onTagsDelete,
            displayConfig = displayConfig
        )
    }
}


//----------------对话分类列表（复用 TagCard、TagAdd）--------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatTagList(
    chatList: List<Chat>,
    selectedTag: String = UNCLASSIFIED_TAG_NAME,
    onTagSelect: (String) -> Unit,
    onTagsDelete: (List<String>) -> Unit,
    displayConfig: TagListDisplayConfig = TagListDisplayConfig(),
    tagModel: TagModel = viewModel()
) {
    val context = LocalContext.current
    val dbTags by tagModel.chatTags.collectAsStateWithLifecycle()
    val bg = MaterialTheme.diaryColors.background3
    val color = MaterialTheme.diaryColors.primary
    val border = MaterialTheme.diaryColors.border3
    
    val allTags = remember(chatList, dbTags) {
        val usedTagNames = chatList.map { it.tag }.toSet()
        val existingTags = dbTags.map { it.name }.toSet()
        val allNames = usedTagNames + existingTags + UNCLASSIFIED_TAG_NAME
        
        allNames.map { tagName ->
            val dbTag = dbTags.find { it.name == tagName }
            val count = chatList.count { it.tag == tagName }
            TagUI(
                name = tagName,
                color = dbTag?.colorInt?.let { Color(it) } ?: color,
                background2 = dbTag?.bg2Int?.let { Color(it) } ?: bg,
                border2 = dbTag?.border2Int?.let { Color(it) } ?: border,
                folder = CHAT_TAG_FOLDER,
                displayName = tagName,
                itemCount = count
            )
        }.sortedByDescending { it.itemCount }
    }

    var isTagAdd by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("分类：", fontSize = 24.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    if (displayConfig.enableSelection && isSelectionMode) {
                        IconButton(onClick = {
                            android.widget.Toast.makeText(context, "对话导出功能待实现", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Upload, contentDescription = "Export")
                        }
                        IconButton(onClick = {
                            val tagsToRemove = dbTags.filter { it.name in selectedTags }
                            tagsToRemove.forEach {
                                tagModel.deleteChatTag(it)
                            }
                            onTagsDelete(tagsToRemove.map { it.name })
                            isSelectionMode = false
                            selectedTags = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
            items(allTags) { tag ->
                val isUnclassified = tag.displayName == "未分类"
                TagCard(
                    tag = tag,
                    isSelectionMode = displayConfig.enableSelection && isSelectionMode && !isUnclassified,
                    isSelected = selectedTags.contains(tag.name),
                    onLongClick = {
                        if (displayConfig.enableSelection && !isUnclassified) {
                            isSelectionMode = true
                            selectedTags = selectedTags + tag.name
                        }
                    },
                    onClick = {
                        if (displayConfig.enableSelection && isSelectionMode && !isUnclassified) {
                            val key = tag.name
                            selectedTags = if (selectedTags.contains(key)) selectedTags - key else selectedTags + key
                        } else if (!isSelectionMode || !isUnclassified) {
                            onTagSelect(tag.name)
                            currentDiaryColors.value = currentDiaryColors.value.copy(
                                background3 = tag.background2,
                                border3 = tag.border2
                            )
                        }
                    }
                )
            }
        }

        if (displayConfig.showAddAction && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                OutlinedButton(onClick = { isTagAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新增")
                }
            }
        }
    }

    // 复用 TagScreen 的 TagAdd 对话框
    if (displayConfig.showAddAction && isTagAdd) {
        com.xinkong.diary.ui.screen.home.TagAdd(
            onDismiss = { isTagAdd = false },
            availableFolders = listOf(CHAT_TAG_FOLDER),
            showFolderSelection = false,
            initialFolder = CHAT_TAG_FOLDER,
            onConfirm = { name, color, background2, border2, folder ->
                val exists = dbTags.any { it.name == name }
                if (exists) {
                    android.widget.Toast.makeText(context, "已存在同名分类", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    tagModel.addChatTag(ChatTag(
                        name = name,
                        colorInt = color.toArgb(),
                        bg2Int = background2.toArgb(),
                        border2Int = border2.toArgb(),
                        folder = CHAT_TAG_FOLDER
                    ))
                }
                isTagAdd = false
            }
        )
    }
}