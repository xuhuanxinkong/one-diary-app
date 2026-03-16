package com.xinkong.diary.ui.screen.home

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.xinkong.diary.repository.Diary
import com.xinkong.diary.ui.theme.ColorPalette
import com.xinkong.diary.ui.theme.*

// Data representing a Tag for UI
data class TagUI(
    val name: String,
    val color: Color,
    val background2: Color = ThemeDefault.background2,
    val border2: Color = ThemeDefault.border2
)

@Composable
fun TagSetting(
    contentList: List<Diary>,
    onTagSelect: (String) -> Unit = {},
    onTagsDelete: (List<String>) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .padding(5.dp,0.dp)
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

    if (isTagAdd) {
        TagAdd(
            onDismiss = { isTagAdd = false },
            onConfirm = { name, color, background2, border2 ->
                addTag(context, name, color, background2, border2)
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
    onConfirm: (String, Color, Color, Color) -> Unit
) {
    var tagName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(ColorPalette[0]) }
    var selectedBackground2 by remember { mutableStateOf(ThemeDefault.background2) }
    var selectedBorder2 by remember { mutableStateOf(ThemeDefault.border2) }

    var showColorPicker by remember { mutableStateOf(false) }
    var showBgPicker by remember { mutableStateOf(false) }
    var showBorderPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("新建分类", fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))

                Row(
                   verticalAlignment = Alignment.CenterVertically,
                   modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(selectedColor)
                            .clickable {
                                showColorPicker = !showColorPicker
                                showBgPicker = false
                                showBorderPicker = false
                            }
                            .border(1.dp, Color.Gray, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedTextField(
                        value = tagName,
                        onValueChange = { tagName = it },
                        label = { Text("输入便签") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (showColorPicker) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("选择标签颜色", fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(32.dp),
                        modifier = Modifier
                            .height(150.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ColorPalette) { color ->
                            ColorItem(
                                color = color,
                                isSelected = color == selectedColor,
                                onClick = { selectedColor = color; showColorPicker = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                var selectedThemeName by remember { mutableStateOf("默认主题") }

                Text("选择主题", fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ThemeList.forEach { (name, theme) ->
                        ThemeOptionItem(
                            name = name,
                            background2 = theme.background2,
                            border2 = theme.border2,
                            isSelected = selectedThemeName == name,
                            onClick = {
                                selectedThemeName = name
                                selectedBackground2 = theme.background2
                                selectedBorder2 = theme.border2
                                showColorPicker = false
                                showBgPicker = false
                                showBorderPicker = false
                            }
                        )
                    }
                    
                    ThemeOptionItem(
                        name = "自定义主题",
                        background2 = if (selectedThemeName == "自定义主题") selectedBackground2 else Color.Transparent,
                        border2 = if (selectedThemeName == "自定义主题") selectedBorder2 else Color.Gray,
                        isSelected = selectedThemeName == "自定义主题",
                        onClick = {
                            selectedThemeName = "自定义主题"
                            showColorPicker = false
                        }
                    )
                }

                if (selectedThemeName == "自定义主题") {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("编辑样式", fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("背景颜色", fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(selectedBackground2)
                                    .border(1.dp, Color.LightGray, CircleShape)
                                    .clickable {
                                        showBgPicker = !showBgPicker
                                        showColorPicker = false
                                        showBorderPicker = false
                                    }
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("边框颜色", fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(selectedBorder2)
                                    .border(1.dp, Color.LightGray, CircleShape)
                                    .clickable {
                                        showBorderPicker = !showBorderPicker
                                        showColorPicker = false
                                        showBgPicker = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                 if(selectedBorder2 == Color.Transparent) {
                                      Icon(Icons.Default.Close, contentDescription = "None", tint = Color.Gray, modifier = Modifier.size(24.dp))
                                 }
                            }
                        }
                    }
                    
                    if (showBgPicker) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("选择背景颜色", fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(32.dp),
                            modifier = Modifier
                                .height(150.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(ColorPalette) { color ->
                                ColorItem(
                                    color = color,
                                    isSelected = color == selectedBackground2,
                                    onClick = { selectedBackground2 = color; showBgPicker = false }
                                )
                            }
                        }
                    }

                    if (showBorderPicker) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("选择边框颜色", fontSize = 14.sp, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(32.dp),
                            modifier = Modifier
                                .height(150.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, Color.Gray, CircleShape)
                                        .clickable { selectedBorder2 = Color.Transparent; showBorderPicker = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "None", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            }
                            items(ColorPalette) { color ->
                                ColorItem(
                                    color = color,
                                    isSelected = color == selectedBorder2,
                                    onClick = { selectedBorder2 = color; showBorderPicker = false }
                                )
                            }
                        }
                    }
                }

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
                                onConfirm(tagName, selectedColor, selectedBackground2, selectedBorder2)
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

@Composable
fun ThemeOptionItem(name: String, background2: Color, border2: Color, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(background2, shape = CircleShape)
                    .border(
                         width = 2.dp,
                         color = if (border2 == Color.Transparent) Color.LightGray else border2,
                         shape = CircleShape
                    )
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.diaryColors.primary else Color.Black
        )
         if (isSelected) {
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.diaryColors.primary)
        }
    }
    Divider(color = Color.Gray.copy(alpha = 0.2f))
}

private const val PREFS_NAME = "tag_prefs"
private const val KEY_CUSTOM_TAGS = "custom_tags"

private fun loadCustomTags(context: Context): List<TagUI> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_CUSTOM_TAGS, emptySet()) ?: emptySet()
    return set.mapNotNull { entry ->
        val parts = entry.split("|")
        if (parts.size >= 2) {
            val name = parts[0]
            val colorInt = parts[1].toIntOrNull()
            val bg2Int = if (parts.size > 2) parts[2].toIntOrNull() else null
            val border2Int = if (parts.size > 3) parts[3].toIntOrNull() else null

            if (colorInt != null) {
                TagUI(
                    name = name,
                    color = Color(colorInt),
                    background2 = bg2Int?.let { Color(it) } ?: ThemeDefault.background2,
                    border2 = border2Int?.let { Color(it) } ?: ThemeDefault.border2
                )
            } else null
        } else null
    }
}

private fun addTag(context: Context, name: String, color: Color, background2: Color, border2: Color) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_CUSTOM_TAGS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()

    set.removeIf { it.startsWith("$name|") }
    val colorInt = color.toArgb()
    val bg2Int = background2.toArgb()
    val border2Int = border2.toArgb()
    set.add("$name|$colorInt|$bg2Int|$border2Int")

    prefs.edit().putStringSet(KEY_CUSTOM_TAGS, set).apply()
}

private fun saveTags(context: Context, tags: List<TagUI>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = tags.map { "${it.name}|${it.color.toArgb()}|${it.background2.toArgb()}|${it.border2.toArgb()}" }.toSet()
    prefs.edit().putStringSet(KEY_CUSTOM_TAGS, set).apply()
}
