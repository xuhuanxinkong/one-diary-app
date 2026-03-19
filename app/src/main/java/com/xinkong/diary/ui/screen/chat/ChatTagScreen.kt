package com.xinkong.diary.ui.screen.chat

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.xinkong.diary.ViewModel.ChatTagModel
import com.xinkong.diary.repository.Chat
import com.xinkong.diary.repository.ChatTag
import com.xinkong.diary.ui.theme.ColorPalette
import com.xinkong.diary.ui.theme.SweetBorder
import com.xinkong.diary.ui.theme.SweetWhite
import com.xinkong.diary.ui.theme.ThemeDefault
import com.xinkong.diary.ui.theme.currentDiaryColors
import com.xinkong.diary.ui.theme.diaryColors
import kotlin.collections.minus
import kotlin.collections.plus


//----------------对话分类面板（包装卡片）--------------------
@Composable
fun ChatTagSetting(
    chatList: List<Chat>,
    onTagSelect: (String) -> Unit = {},
    onTagsDelete: (List<String>) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .padding(5.dp, 0.dp)
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        ChatTagList(chatList, onTagSelect, onTagsDelete)
    }
}


//----------------对话分类列表（复用 TagCard、TagAdd）--------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatTagList(
    chatList: List<Chat>,
    onTagSelect: (String) -> Unit,
    onTagsDelete: (List<String>) -> Unit,
    tagModel: ChatTagModel = viewModel()
) {
    val context = LocalContext.current
    val dbTags by tagModel.tags.collectAsStateWithLifecycle()

    val customTags = remember(dbTags) {
        dbTags.map { dbTag ->
            com.xinkong.diary.ui.screen.home.TagUI(
                name = dbTag.name,
                color = Color(dbTag.colorInt),
                background2 = Color(dbTag.bg2Int),
                border2 = Color(dbTag.border2Int)
            )
        }
    }

    val distinctChatTags = remember(chatList) {
        chatList.map { it.tag }.distinct()
    }

    // 合并：Chat 已有标签 + 用户自定义标签
    val mergedTags = remember(distinctChatTags, customTags) {
        val allTagNames = (distinctChatTags + customTags.map { it.name }).distinct()
        allTagNames
            .filter { it != "未分类" }
            .map { name ->
                val custom = customTags.find { it.name == name }
                if (custom != null) {
                    custom
                } else {
                    val colorIndex = kotlin.math.abs(name.hashCode()) % ColorPalette.size
                    com.xinkong.diary.ui.screen.home.TagUI(
                        name,
                        ColorPalette[colorIndex]
                    )
                }
            }
    }

    // "未分类" 置顶
    val displayTags = remember(mergedTags) {
        listOf(
            com.xinkong.diary.ui.screen.home.TagUI(
                "未分类",
                Color.Black,
                background2 = SweetWhite,
                border2 = SweetBorder
            )
        ) + mergedTags
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
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val tagsToRemove = selectedTags.toList()
                            dbTags.filter { it.name in tagsToRemove }.forEach {
                                tagModel.deleteTag(it)
                            }
                            onTagsDelete(tagsToRemove)
                            isSelectionMode = false
                            selectedTags = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
            items(displayTags) { tag ->
                val isUnclassified = tag.name == "未分类"
                // 复用 TagScreen 的 TagCard
                com.xinkong.diary.ui.screen.home.TagCard(
                    tag = tag,
                    isSelectionMode = isSelectionMode && !isUnclassified,
                    isSelected = selectedTags.contains(tag.name),
                    onLongClick = {
                        if (!isUnclassified) {
                            isSelectionMode = true
                            selectedTags = selectedTags + tag.name
                        }
                    },
                    onClick = {
                        if (isSelectionMode) {
                            if (!isUnclassified) {
                                selectedTags = if (selectedTags.contains(tag.name)) {
                                    selectedTags - tag.name
                                } else {
                                    selectedTags + tag.name
                                }
                            }
                        } else {
                            onTagSelect(tag.name)
                            currentDiaryColors.value = currentDiaryColors.value.copy(
                                background2 = tag.background2,
                                border2 = tag.border2
                            )
                        }
                    }
                )
            }
        }

        if (!isSelectionMode) {
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
    if (isTagAdd) {
        com.xinkong.diary.ui.screen.home.TagAdd(
            onDismiss = { isTagAdd = false },
            onConfirm = { name, color, background2, border2 ->
                tagModel.addTag(ChatTag(
                    name = name,
                    colorInt = color.toArgb(),
                    bg2Int = background2.toArgb(),
                    border2Int = border2.toArgb()
                ))
                isTagAdd = false
            }
        )
    }
}