package com.xinkong.diary.ui.screen.chat

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.xinkong.diary.repository.Chat
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
    onTagsDelete: (List<String>) -> Unit
) {
    val context = LocalContext.current
    var customTags by remember { mutableStateOf(loadChatCustomTags(context)) }

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
                    _root_ide_package_.com.xinkong.diary.ui.screen.home.TagUI(
                        name,
                        ColorPalette[colorIndex]
                    )
                }
            }
    }

    // "未分类" 置顶
    val displayTags = remember(mergedTags) {
        listOf(
            _root_ide_package_.com.xinkong.diary.ui.screen.home.TagUI(
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
                            saveChatTags(context, customTags.filter { it.name !in tagsToRemove })
                            customTags = loadChatCustomTags(context)
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
                _root_ide_package_.com.xinkong.diary.ui.screen.home.TagCard(
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
        _root_ide_package_.com.xinkong.diary.ui.screen.home.TagAdd(
            onDismiss = { isTagAdd = false },
            onConfirm = { name, color, background2, border2 ->
                addChatTag(context, name, color, background2, border2)
                customTags = loadChatCustomTags(context)
                isTagAdd = false
            }
        )
    }
}


// ========== Chat 分类的 SharedPreferences 存储（独立于 Diary 分类）==========

private const val CHAT_PREFS_NAME = "chat_tag_prefs"
private const val KEY_CUSTOM_CHAT_TAGS = "custom_chat_tags"

private fun loadChatCustomTags(context: Context): List<com.xinkong.diary.ui.screen.home.TagUI> {
    val prefs = context.getSharedPreferences(CHAT_PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_CUSTOM_CHAT_TAGS, emptySet()) ?: emptySet()
    return set.mapNotNull { entry ->
        val parts = entry.split("|")
        if (parts.size >= 2) {
            val name = parts[0]
            val colorInt = parts[1].toIntOrNull()
            val bg2Int = if (parts.size > 2) parts[2].toIntOrNull() else null
            val border2Int = if (parts.size > 3) parts[3].toIntOrNull() else null
            if (colorInt != null) {
                _root_ide_package_.com.xinkong.diary.ui.screen.home.TagUI(
                    name = name,
                    color = Color(colorInt),
                    background2 = bg2Int?.let { Color(it) } ?: ThemeDefault.background2,
                    border2 = border2Int?.let { Color(it) } ?: ThemeDefault.border2
                )
            } else null
        } else null
    }
}

private fun addChatTag(context: Context, name: String, color: Color, background2: Color, border2: Color) {
    val prefs = context.getSharedPreferences(CHAT_PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_CUSTOM_CHAT_TAGS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    set.removeIf { it.startsWith("$name|") }
    set.add("$name|${color.toArgb()}|${background2.toArgb()}|${border2.toArgb()}")
    prefs.edit().putStringSet(KEY_CUSTOM_CHAT_TAGS, set).apply()
}

private fun saveChatTags(context: Context, tags: List<com.xinkong.diary.ui.screen.home.TagUI>) {
    val prefs = context.getSharedPreferences(CHAT_PREFS_NAME, Context.MODE_PRIVATE)
    val set = tags.map { "${it.name}|${it.color.toArgb()}|${it.background2.toArgb()}|${it.border2.toArgb()}" }.toSet()
    prefs.edit().putStringSet(KEY_CUSTOM_CHAT_TAGS, set).apply()
}