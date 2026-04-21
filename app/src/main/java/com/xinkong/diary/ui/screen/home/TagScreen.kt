package com.xinkong.diary.ui.screen.home

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.xinkong.diary.ViewModel.TagModel
import com.xinkong.diary.ViewModel.TagDisplayItem
import com.xinkong.diary.repository.DiaryTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyListScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.ui.screen.tag.tagFolderItems
import com.xinkong.diary.ui.screen.tag.TagFolderListState
import com.xinkong.diary.ui.screen.tag.DEFAULT_TAG_FOLDER
import com.xinkong.diary.ui.screen.tag.UNCLASSIFIED_TAG_NAME

// Data representing a Tag for UI
data class TagUI(
    val name: String,
    val color: Color,
    val background2: Color = ThemeDefault.background2,
    val border2: Color = ThemeDefault.border2,
    val folder: String = "我的笔记",
    val displayName: String = name,
    val itemCount: Int = 0
)

data class TagListDisplayConfig(
    val showManageAction: Boolean = true,
    val showAddAction: Boolean = true,
    val enableSelection: Boolean = true,
    val filterByCurrentFolder: Boolean = true
)

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

@Composable
fun TagSetting(
    contentList: List<Diary>,
    selectedTag: Pair<String, String> = DEFAULT_TAG_FOLDER to UNCLASSIFIED_TAG_NAME,
    onTagSelect: (Pair<String, String>) -> Unit = {},
    onTagsDelete: (List<Pair<String, String>>) -> Unit = {},
    displayConfig: TagListDisplayConfig = TagListDisplayConfig(),
    onManageClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .padding(5.dp,0.dp)
            .fillMaxWidth()
            .heightIn(max = 400.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        TagList(
            contentList = contentList,
            selectedTag = selectedTag,
            onTagSelect = onTagSelect,
            onTagsDelete = onTagsDelete,
            displayConfig = displayConfig,
            onManageClick = onManageClick
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagList(
    contentList: List<Diary>,
    selectedTag: Pair<String, String> = DEFAULT_TAG_FOLDER to UNCLASSIFIED_TAG_NAME,
    onTagSelect: (Pair<String, String>) -> Unit,
    onTagsDelete: (List<Pair<String, String>>) -> Unit,
    displayConfig: TagListDisplayConfig = TagListDisplayConfig(),
    onManageClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val tagModel: TagModel = viewModel()
    val dbTags by tagModel.diaryTags.collectAsStateWithLifecycle()
    val dbFolders by tagModel.tagFolders.collectAsStateWithLifecycle()

    
    val groupedResult = remember(contentList, dbTags, dbFolders) {
        tagModel.buildDiaryGroupedTags(contentList)
    }

    val diaryModel: DiaryViewModel = viewModel()
    val currentFolder by diaryModel.currentFolder.collectAsStateWithLifecycle()

    val groupedTags = remember(groupedResult, currentFolder, displayConfig.filterByCurrentFolder) {
        if (displayConfig.filterByCurrentFolder) {
            groupedResult.groupedTags.filterKeys { it == currentFolder }.mapValues { (_, items) ->
                items.map { it.toTagUi() }
            }
        } else {
            groupedResult.groupedTags.mapValues { (_, items) ->
                items.map { it.toTagUi() }
            }
        }
    }

    var isTagAdd by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(setOf<Pair<String, String>>()) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val selectedDiaries = contentList.filter { (it.tagFolder to it.tag) in selectedTags }
            if (selectedDiaries.isNotEmpty()) {
                diaryModel.exportToJson(selectedDiaries, uri, context) { result ->
                    result.onSuccess {
                        Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                        isSelectionMode = false
                        selectedTags = emptySet()
                    }.onFailure { e ->
                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "没有可导出的日记", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                            if (selectedTags.isNotEmpty()) {
                                exportLauncher.launch("diary_export_${System.currentTimeMillis()}.json")
                            } else {
                                android.widget.Toast.makeText(context, "请先选择要导出的分类", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Upload, contentDescription = "Export")
                        }
                        IconButton(onClick = {
                            val tagsToRemove = dbTags.filter { (it.folder to it.name) in selectedTags }
                            tagsToRemove.forEach {
                                tagModel.deleteDiaryTag(it)
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
                            background2 = tag.background2,
                            border2 = tag.border2
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

    if (displayConfig.showAddAction && isTagAdd) {
        val uniqueFolders = groupedResult.availableFolders
        TagAdd(
            onDismiss = { isTagAdd = false },
            availableFolders = uniqueFolders,
            onConfirm = { name, color, background2, border2, folder ->
                val exists = dbTags.any { it.folder == folder && it.name == name }
                if (exists) {
                    android.widget.Toast.makeText(context, "该文件夹下已存在同名分类", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    tagModel.addDiaryTag(DiaryTag(
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
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp).padding(start = 14.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = tag.color)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = tag.displayName, fontSize = 18.sp, modifier = Modifier.weight(1f))

            if (tag.itemCount > 0) {
                Text(
                    text = "${tag.itemCount}",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagAdd(
    initialName: String = "",
    initialColor: Color = ColorPalette[0],
    initialBackground2: Color = ThemeDefault.background2,
    initialBorder2: Color = ThemeDefault.border2,
    initialFolder: String = "我的笔记",
    availableFolders: List<String> = listOf("我的笔记"),
    isEditMode: Boolean = false,
    showFolderSelection: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String, Color, Color, Color, String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var tagName by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedBackground2 by remember { mutableStateOf(initialBackground2) }
    var selectedBorder2 by remember { mutableStateOf(initialBorder2) }
    var selectedFolder by remember { mutableStateOf(initialFolder) }

    var showColorPicker by remember { mutableStateOf(false) }
    var showBgPicker by remember { mutableStateOf(false) }
    var showBorderPicker by remember { mutableStateOf(false) }
    var folderDropdownExpanded by remember { mutableStateOf(false) }

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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isEditMode) "编辑分类" else "新建分类", fontSize = 20.sp)
                    if (isEditMode && onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red)
                        }
                    }
                }

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

                Spacer(modifier = Modifier.height(16.dp))
                
                if (showFolderSelection) {
                    // Folder drop down
                    ExposedDropdownMenuBox(
                        expanded = folderDropdownExpanded,
                        onExpandedChange = { folderDropdownExpanded = !folderDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedFolder,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("所属文件夹") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = folderDropdownExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = folderDropdownExpanded,
                            onDismissRequest = { folderDropdownExpanded = false }
                        ) {
                            availableFolders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder) },
                                    onClick = {
                                        selectedFolder = folder
                                        folderDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
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
                                onConfirm(tagName, selectedColor, selectedBackground2, selectedBorder2, selectedFolder)
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
