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
import com.xinkong.diary.ViewModel.TagDisplayItem
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.ChatTag
import com.xinkong.diary.ui.screen.home.TagListDisplayConfig
import com.xinkong.diary.ui.screen.home.TagUI
import com.xinkong.diary.ui.screen.tag.DEFAULT_TAG_FOLDER
import com.xinkong.diary.ui.screen.tag.UNCLASSIFIED_TAG_NAME
import com.xinkong.diary.ui.screen.tag.TagFolderListState
import com.xinkong.diary.ui.screen.tag.tagFolderItems
import com.xinkong.diary.ui.theme.currentDiaryColors
import com.xinkong.diary.ui.theme.diaryColors
import kotlin.collections.minus
import kotlin.collections.plus

private fun TagDisplayItem.toTagUi(): TagUI {
    return TagUI(
        name = name,
        color = Color(colorInt),
        background2 = Color(bg2Int),
        border2 = Color(border2Int),
        folder = folder,
        displayName = displayName,
        itemCount = itemCount
    )
}


//----------------对话分类面板（包装卡片）--------------------
@Composable
fun ChatTagSetting(
    chatList: List<Chat>,
    selectedTag: Pair<String, String> = DEFAULT_TAG_FOLDER to UNCLASSIFIED_TAG_NAME,
    onTagSelect: (Pair<String, String>) -> Unit = {},
    onTagsDelete: (List<Pair<String, String>>) -> Unit = {},
    displayConfig: TagListDisplayConfig = TagListDisplayConfig(),
    onManageClick: () -> Unit = {}
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
            displayConfig = displayConfig,
            onManageClick = onManageClick
        )
    }
}


//----------------对话分类列表（复用 TagCard、TagAdd）--------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatTagList(
    chatList: List<Chat>,
    selectedTag: Pair<String, String> = DEFAULT_TAG_FOLDER to UNCLASSIFIED_TAG_NAME,
    onTagSelect: (Pair<String, String>) -> Unit,
    onTagsDelete: (List<Pair<String, String>>) -> Unit,
    displayConfig: TagListDisplayConfig = TagListDisplayConfig(),
    tagModel: TagModel = viewModel(),
    onManageClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val dbTags by tagModel.chatTags.collectAsStateWithLifecycle()
    val dbFolders by tagModel.tagFolders.collectAsStateWithLifecycle()
    
    val groupedResult = remember(chatList, dbTags, dbFolders) {
        tagModel.buildChatGroupedTags(chatList)
    }

    val groupedTags = remember(groupedResult) {
        groupedResult.groupedTags.mapValues { (_, items) ->
            items.map { it.toTagUi() }
        }
    }

    var isTagAdd by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(setOf<Pair<String, String>>()) }

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
                    if (displayConfig.showManageAction) {
                        Text(
                            text = "管理",
                            color = MaterialTheme.diaryColors.primary,
                            modifier = Modifier
                                .clickable {
                                    onManageClick()
                                }
                                .padding(end = 16.dp)
                        )
                    }
                    if (displayConfig.enableSelection && isSelectionMode) {
                        IconButton(onClick = {
                            android.widget.Toast.makeText(context, "对话导出功能待实现", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Upload, contentDescription = "Export")
                        }
                        IconButton(onClick = {
                            val tagsToRemove = dbTags.filter { (it.folder to it.name) in selectedTags }
                            tagsToRemove.forEach {
                                tagModel.deleteChatTag(it)
                            }
                            onTagsDelete(tagsToRemove.map { it.folder to it.name })
                            isSelectionMode = false
                            selectedTags = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
            tagFolderItems(
                groupedTags = groupedTags,
                listState = TagFolderListState(
                    selectedTag = selectedTag,
                    isSelectionMode = displayConfig.enableSelection && isSelectionMode,
                    selectedTags = selectedTags
                ),
                onTagClick = { tag ->
                    if (displayConfig.enableSelection && isSelectionMode) {
                        val key = tag.folder to tag.name
                        selectedTags = if (selectedTags.contains(key)) selectedTags - key else selectedTags + key
                    } else {
                        onTagSelect(tag.folder to tag.name)
                        currentDiaryColors.value = currentDiaryColors.value.copy(
                            background3 = tag.background2,
                            border3 = tag.border2
                        )
                    }
                },
                onTagLongClick = { tag ->
                    if (displayConfig.enableSelection) {
                        isSelectionMode = true
                        selectedTags = selectedTags + (tag.folder to tag.name)
                    }
                }
            )

            if (groupedResult.hiddenItemCount > 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "有 ${groupedResult.hiddenItemCount} 个文件已隐藏",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
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
        val uniqueFolders = groupedResult.availableFolders
        com.xinkong.diary.ui.screen.home.TagAdd(
            onDismiss = { isTagAdd = false },
            availableFolders = uniqueFolders,
            onConfirm = { name, color, background2, border2, folder ->
                val exists = dbTags.any { it.folder == folder && it.name == name }
                if (exists) {
                    android.widget.Toast.makeText(context, "该文件夹下已存在同名分类", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    tagModel.addChatTag(ChatTag(
                        name = name,
                        colorInt = color.toArgb(),
                        bg2Int = background2.toArgb(),
                        border2Int = border2.toArgb(),
                        folder = folder
                    ))
                }
                isTagAdd = false
            }
        )
    }
}