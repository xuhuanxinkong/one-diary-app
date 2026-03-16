package com.xinkong.diary.ui.screen.home

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xinkong.diary.repository.Diary
import com.xinkong.diary.ui.theme.ColorPalette

// Data representing a Tag for UI
data class TagUI(
    val name: String,
    val color: Color
)

@Composable
fun TagSetting(
    contentList: List<Diary>,
    onTagSelect: (String) -> Unit = {},
    onTagsDelete: (List<String>) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        TagList(contentList, onTagSelect, onTagsDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagList(
    contentList: List<Diary>,
    onTagSelect: (String) -> Unit,
    onTagsDelete: (List<String>) -> Unit
) {
    val context = LocalContext.current
    var customTags by remember { mutableStateOf(loadCustomTags(context)) }

    val distinctDiaryTags = remember(contentList) {
        contentList.mapNotNull { it.tag }.distinct()
    }

    val mergedTags = remember(distinctDiaryTags, customTags) {
        val allTagNames = (distinctDiaryTags + customTags.map { it.name }).distinct()
        allTagNames
            .filter { it != "未分类" }
            .map { name ->
                val custom = customTags.find { it.name == name }
                if (custom != null) {
                    custom
                } else {
                    val colorIndex = kotlin.math.abs(name.hashCode()) % ColorPalette.size
                    TagUI(name, ColorPalette[colorIndex])
                }
            }
    }

    //未分类置顶
    val displayTags = remember(mergedTags) {
        listOf(TagUI("未分类", Color.Black)) + mergedTags
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
                    Text(
                        "分类：",
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val tagsToRemove = selectedTags.toList()
                            saveTags(context, customTags.filter { it.name !in tagsToRemove })
                            customTags = loadCustomTags(context)
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
                TagCard(
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

    if (isTagAdd) {
        TagAdd(
            onDismiss = { isTagAdd = false },
            onConfirm = { name, color ->
                addTag(context, name, color)
                customTags = loadCustomTags(context)
                isTagAdd = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagCard(
    tag: TagUI,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(tag.color)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = tag.name, fontSize = 18.sp, modifier = Modifier.weight(1f))
            
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null // Handled by onClick of the row
                )
            }
        }
        Divider(color = Color.LightGray, thickness = 1.dp)
    }
}

@Composable
fun TagAdd(
    onDismiss: () -> Unit,
    onConfirm: (String, Color) -> Unit
) {
    var tagName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(ColorPalette[0]) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("新建笔记本", fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))

                Text("选择颜色", fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(32.dp),
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ColorPalette) { color ->
                        ColorItem(
                            color = color,
                            isSelected = color == selectedColor,
                            onClick = { selectedColor = color }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text("输入标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (tagName.isNotBlank()) {
                                onConfirm(tagName, selectedColor)
                            }
                        }
                    ) {
                        Text("确认")
                    }
                }
            }
        }
    }
}

@Composable
fun ColorItem(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color.Black else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private const val PREFS_NAME = "tag_prefs"
private const val KEY_CUSTOM_TAGS = "custom_tags"

private fun loadCustomTags(context: Context): List<TagUI> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_CUSTOM_TAGS, emptySet()) ?: emptySet()
    return set.mapNotNull { entry ->
        val parts = entry.split("|")
        if (parts.size == 2) {
            val name = parts[0]
            val colorInt = parts[1].toIntOrNull()
            if (colorInt != null) {
                TagUI(name, Color(colorInt))
            } else null
        } else null
    }
}

private fun addTag(context: Context, name: String, color: Color) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_CUSTOM_TAGS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()

    set.removeIf { it.startsWith("$name|") }
    val colorInt = color.toArgb()
    set.add("$name|$colorInt")

    prefs.edit().putStringSet(KEY_CUSTOM_TAGS, set).apply()
}

private fun saveTags(context: Context, tags: List<TagUI>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = tags.map { "${it.name}|${it.color.toArgb()}" }.toSet()
    prefs.edit().putStringSet(KEY_CUSTOM_TAGS, set).apply()
}
