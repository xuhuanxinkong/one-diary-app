package com.xinkong.diary.ui.screen.tag

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xinkong.diary.ui.screen.home.TagCard
import com.xinkong.diary.ui.screen.home.TagUI

data class TagFolderListState(
    val selectedTag: Pair<String, String>,
    val isSelectionMode: Boolean = false,
    val selectedTags: Set<Pair<String, String>> = emptySet()
)

fun LazyListScope.tagFolderItems(
    groupedTags: Map<String, List<TagUI>>,
    listState: TagFolderListState,
    onTagClick: (TagUI) -> Unit,
    onTagLongClick: (TagUI) -> Unit
) {
    groupedTags.forEach { (folderName, tagsInFolder) ->
        item(key = folderName) {
            val isActive = listState.selectedTag.first == folderName &&
                tagsInFolder.any { it.name == listState.selectedTag.second }
            TagFolderSection(
                folderName = folderName,
                tags = tagsInFolder,
                isActive = isActive,
                isSelectionMode = listState.isSelectionMode,
                selectedTags = listState.selectedTags,
                onTagClick = onTagClick,
                onTagLongClick = onTagLongClick
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagFolderSection(
    folderName: String,
    tags: List<TagUI>,
    isActive: Boolean = false,
    isSelectionMode: Boolean,
    selectedTags: Set<Pair<String, String>>,
    onTagClick: (TagUI) -> Unit,
    onTagLongClick: (TagUI) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Folder Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Folder else Icons.Default.FolderOpen,
                contentDescription = "Folder", 
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = folderName, 
                fontSize = 18.sp, 
                color =Color.Black,
                fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = Color.Gray
            )
        }
        Divider(color = Color.LightGray, thickness = 1.dp)

        // Tags List
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                tags.forEach { tag ->
                    val isUnclassified = tag.displayName == "未分类"
                    // Resuing the original TagCard inside loop
                    TagCard(
                        tag = tag,
                        isSelectionMode = isSelectionMode && !isUnclassified,
                        isSelected = selectedTags.contains(tag.folder to tag.name),
                        onLongClick = {
                            if (!isUnclassified) {
                                onTagLongClick(tag)
                            }
                        },
                        onClick = {
                            if (!isSelectionMode || !isUnclassified) {
                                onTagClick(tag)
                            }
                        }
                    )
                }
            }
        }
    }
}
