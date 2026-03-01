package com.xinkong.diary.ui.screen.home

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.xinkong.diary.ui.theme.ThemeList
import com.xinkong.diary.ui.theme.currentDiaryColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.Http.AiConfig
import com.xinkong.diary.Http.AiType
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.ui.theme.diaryColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.text.contains
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import java.io.OutputStreamWriter
import java.io.InputStreamReader
import java.io.BufferedReader
import com.xinkong.diary.repository.Diary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingScreen(onDismiss: () -> Unit) {
    var configType by remember { mutableStateOf<AiType?>(null) }
    var showSheet by remember { mutableStateOf(true) }
    val viewModel: DiaryViewModel = viewModel()

    var aiExpanded by remember { mutableStateOf(true) }
    var themeExpanded by remember { mutableStateOf(true) }
    var exportExpanded by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val diaries by viewModel.listState.collectAsState()
    
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    
    // States for export/import logic
    var pendingExportDiaries by remember { mutableStateOf<List<Diary>>(emptyList()) }
    var pendingImportFormat by remember { mutableStateOf(true) }
    var pendingImportTag by remember { mutableStateOf("") }


    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            viewModel.exportToJson(pendingExportDiaries) { result ->
                result.onSuccess { jsonString ->
                    try {
                        context.contentResolver.openOutputStream(it)?.use { outputStream ->
                            OutputStreamWriter(outputStream).use { writer ->
                                writer.write(jsonString)
                            }
                        }
                        Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { e ->
                    Toast.makeText(context, "生成数据失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val jsonString = reader.readText()
                        viewModel.importFromJson(jsonString, pendingImportFormat, pendingImportTag) { result ->
                            result.onSuccess { count ->
                                Toast.makeText(context, "成功导入 $count 条笔记", Toast.LENGTH_SHORT).show()
                            }.onFailure { e ->
                                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    if (showExportDialog) {
        ExportDialog(
            diaries = diaries,
            onDismiss = { showExportDialog = false },
            onConfirm = { selected ->
                if (selected.isEmpty()) {
                    Toast.makeText(context, "请先选择要导出的笔记", Toast.LENGTH_SHORT).show()
                } else {
                    showExportDialog = false
                    pendingExportDiaries = selected
                    exportLauncher.launch("diary_export_${System.currentTimeMillis()}.json")
                }
            }
        )
    }

    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { isDefault, tag ->
                showImportDialog = false
                pendingImportFormat = isDefault
                pendingImportTag = tag
                importLauncher.launch(arrayOf("application/json", "*/*"))
            }
        )
    }

    if (configType != null) {
        ConfigDialog(
            type = configType!!,
            onDismiss = { configType = null; showSheet = true },
            onConfirm = { config ->
                viewModel.updateAiConfig(config)
                configType = null
                onDismiss()
            }
        )
    }

    if (showSheet) {
        Dialog (onDismissRequest = { showSheet = false; onDismiss() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(
                    rememberScrollState()
                )) {
                    Text("设置", fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp), fontWeight = FontWeight.Bold)

                    Divider(color = MaterialTheme.diaryColors.tertiary.copy(alpha = 0.5f))
                    
                    //      AI设置
                    SettingSectionHeader(
                        title = "AI 设置",
                        isExpanded = aiExpanded,
                        onClick = { aiExpanded = !aiExpanded }
                    )
                    if (aiExpanded) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AiOptionChip("自定义", onClick = { showSheet = false; configType = AiType.CUSTOM })
                            AiOptionChip("DeepSeek", onClick = { showSheet = false; configType = AiType.DEEPSEEK })
                            AiOptionChip("本地 Ollama", onClick = { showSheet = false; configType = AiType.LOCAL })
                            AiOptionChip("测试环境", onClick = {
                                viewModel.updateAiConfig(
                                    AiConfig(
                                        AiType.TEST,
                                        "http://10.0.2.2:11434/v1/chat/completions",
                                        "deepseek-r1:7b"
                                    )
                                )
                                showSheet = false
                                onDismiss()
                            })
                        }
                    }

                    Divider(color = MaterialTheme.diaryColors.tertiary.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                    //      主题设置
                    SettingSectionHeader(
                        title = "主题设置",
                        isExpanded = themeExpanded,
                        onClick = { themeExpanded = !themeExpanded }
                    )
                    if (themeExpanded) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            ThemeList.forEach { (name, theme) ->
                                ThemeOptionItem(
                                    name = name,
                                    theme = theme,
                                    isSelected = currentDiaryColors.value == theme,
                                    onClick = {
                                        currentDiaryColors.value = theme
                                    }
                                )
                            }
                        }
                    }
                    Divider(color = MaterialTheme.diaryColors.tertiary.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                    //      导入导出设置
                    SettingSectionHeader(
                        title = "文件设置",
                        isExpanded = exportExpanded,
                        onClick = {exportExpanded = !exportExpanded}
                    )
                    if (exportExpanded){
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = { showExportDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.diaryColors.primary)
                            ) {
                                Text("笔记导出", color = Color.White)
                            }
                            Button(
                                onClick = { showImportDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.diaryColors.secondary)
                            ) {
                                Text("笔记导入", color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun SettingSectionHeader(title: String, isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.diaryColors.primary)
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.graphicsLayer(rotationZ = if (isExpanded) 180f else 0f),
            tint = MaterialTheme.diaryColors.primary
        )
    }
}

@Composable
fun AiOptionChip(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.diaryColors.sweetBackground),
        border = BorderStroke(1.dp, MaterialTheme.diaryColors.sweetBorder)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 14.sp,
            color = MaterialTheme.diaryColors.sweetText
        )
    }
}

@Composable
fun ThemeOptionItem(name: String, theme: com.xinkong.diary.ui.theme.DiaryColors, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(theme.background, shape = androidx.compose.foundation.shape.CircleShape)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.diaryColors.primary else Color.Gray,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.diaryColors.primary else Color.Black
        )
    }
    Divider(color = Color.Gray.copy(alpha = 0.2f))
}

@Composable
fun ExportDialog(
    diaries: List<Diary>,
    onDismiss: () -> Unit,
    onConfirm: (List<Diary>) -> Unit
){
    var selectedDiaries by remember { mutableStateOf(setOf<Diary>()) }
    // Implement "Select All" logic if needed, or simple multiselect
    val isAllSelected = selectedDiaries.size == diaries.size && diaries.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出设置", color = MaterialTheme.diaryColors.primary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("选择要导出的笔记:", fontSize = 14.sp, color = MaterialTheme.diaryColors.sweetText, modifier = Modifier.padding(bottom = 8.dp))
                
                // Select All Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (isAllSelected) {
                            selectedDiaries = emptySet()
                        } else {
                            selectedDiaries = diaries.toSet()
                        }
                    }.padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = isAllSelected,
                        onCheckedChange = { checked ->
                            if (checked) {
                                selectedDiaries = diaries.toSet()
                            } else {
                                selectedDiaries = emptySet()
                            }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.diaryColors.primary)
                    )
                    Text("全选", fontSize = 14.sp, color = MaterialTheme.diaryColors.sweetText)
                }
                Divider()

                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).border(1.dp, MaterialTheme.diaryColors.tertiary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                        if (diaries.isEmpty()) {
                            Text("暂无笔记", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
                        } else {
                            diaries.forEach { diary ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        if (selectedDiaries.contains(diary)) {
                                            selectedDiaries = selectedDiaries - diary
                                        } else {
                                            selectedDiaries = selectedDiaries + diary
                                        }
                                    }.padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = selectedDiaries.contains(diary),
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                selectedDiaries = selectedDiaries + diary
                                            } else {
                                                selectedDiaries = selectedDiaries - diary
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.diaryColors.primary)
                                    )
                                    Column {
                                        Text(diary.title.ifEmpty { "无标题" }, fontSize = 14.sp, color = MaterialTheme.diaryColors.sweetText, maxLines = 1)
                                        Text(diary.tag ?: "未分类", fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDiaries.toList()) }) {
                Text("确定", color = MaterialTheme.diaryColors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.Gray)
            }
        },
        containerColor = MaterialTheme.diaryColors.sweetBackground
    )
}

@Composable
fun ImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (Boolean, String) -> Unit
){
    var isDefaultImportFormat by remember { mutableStateOf(true) }
    var importTargetTag by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入设置", color = MaterialTheme.diaryColors.primary) },
        text = {
            Column {
                Text("导入方式:", fontWeight = FontWeight.Bold, color = MaterialTheme.diaryColors.sweetText, modifier = Modifier.padding(bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isDefaultImportFormat = true }) {
                    RadioButton(
                        selected = isDefaultImportFormat,
                        onClick = { isDefaultImportFormat = true },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.diaryColors.primary)
                    )
                    Text("默认格式 (本应用导出样式)", color = MaterialTheme.diaryColors.sweetText)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isDefaultImportFormat = false }) {
                    RadioButton(
                        selected = !isDefaultImportFormat,
                        onClick = { isDefaultImportFormat = false },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.diaryColors.primary)
                    )
                    Text("其他 JSON 格式 (作为单条笔记导入)", color = MaterialTheme.diaryColors.sweetText)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("导入位置 (标签):", fontWeight = FontWeight.Bold, color = MaterialTheme.diaryColors.sweetText, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = importTargetTag,
                    onValueChange = { importTargetTag = it },
                    placeholder = { Text("留空则使用原标签或默认标签", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.diaryColors.primary,
                        unfocusedBorderColor = MaterialTheme.diaryColors.tertiary,
                        focusedTextColor = MaterialTheme.diaryColors.sweetText,
                        unfocusedTextColor = MaterialTheme.diaryColors.sweetText
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(isDefaultImportFormat, importTargetTag)
            }) {
                Text("选择文件", color = MaterialTheme.diaryColors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.Gray)
            }
        },
        containerColor = MaterialTheme.diaryColors.sweetBackground
    )
}

@Composable
fun ConfigDialog(
    type: AiType,
    onDismiss: () -> Unit,
    onConfirm: (AiConfig) -> Unit
) {
    Dialog(onDismissRequest = onDismiss,
    ) {

        var baseUrl by remember { mutableStateOf("") }
        var model by remember { mutableStateOf("") }
        var apiKey by remember { mutableStateOf("") }
        var testResult by remember { mutableStateOf<String?>(null) }
        var testing by remember { mutableStateOf(false) }
        // Model Fetching State
        var models by remember { mutableStateOf<List<String>>(emptyList()) }
        var showModelDropdown by remember { mutableStateOf(false) }
        var fetchingModels by remember { mutableStateOf(false) }
        val scope = remember { CoroutineScope(Dispatchers.Main) }
        // Set defaults based on type
        remember(type) {
            when(type) {
                AiType.LOCAL -> {
                    baseUrl = "http://10.0.2.2:11434/v1/chat/completions"
                    model = "llama2"
                }
                AiType.DEEPSEEK -> {
                    baseUrl = "https://api.deepseek.com/v1/chat/completions"
                    model = "deepseek-chat"
                }
                else -> {}
            }
            true
        }
        Card(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when(type) {
                            AiType.LOCAL -> "设置本地 Ollama"
                            AiType.DEEPSEEK -> "设置 DeepSeek"
                            else -> "自定义 AI"
                        },
                        fontSize = 20.sp,
                        color = MaterialTheme.diaryColors.sweetText,
                        style = androidx.compose.ui.text.TextStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.clickable { onDismiss() },
                        tint = MaterialTheme.diaryColors.sweetHighlight
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Base URL Input
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL", color = MaterialTheme.diaryColors.sweetText.copy(alpha = 0.7f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.diaryColors.sweetHighlight,
                        unfocusedBorderColor = MaterialTheme.diaryColors.sweetBorder,
                        focusedTextColor = MaterialTheme.diaryColors.sweetText,
                        unfocusedTextColor = MaterialTheme.diaryColors.sweetText,
                        cursorColor = MaterialTheme.diaryColors.sweetHighlight
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Model Input & Fetch logic
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Model Name", color = MaterialTheme.diaryColors.sweetText.copy(alpha = 0.7f)) },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { showModelDropdown = !showModelDropdown }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Model", tint = MaterialTheme.diaryColors.sweetHighlight)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.diaryColors.sweetHighlight,
                                unfocusedBorderColor = MaterialTheme.diaryColors.sweetBorder,
                                focusedTextColor = MaterialTheme.diaryColors.sweetText,
                                unfocusedTextColor = MaterialTheme.diaryColors.sweetText,
                                cursorColor = MaterialTheme.diaryColors.sweetHighlight
                            )
                        )
                        DropdownMenu(
                            expanded = showModelDropdown,
                            onDismissRequest = { showModelDropdown = false },
                            modifier = Modifier.background(MaterialTheme.diaryColors.sweetBackground)
                        ) {
                            if (models.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("无模型数据，请先获取", color = Color.Gray) },
                                    onClick = { showModelDropdown = false }
                                )
                            } else {
                                models.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m, color = MaterialTheme.diaryColors.sweetText) },
                                        onClick = {
                                            model = m
                                            showModelDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (type == AiType.LOCAL || type == AiType.CUSTOM || type == AiType.DEEPSEEK) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                fetchingModels = true
                                scope.launch {
                                    val http = com.xinkong.diary.Http.AiHttp()
                                    val result = http.getModels(baseUrl, apiKey)
                                    fetchingModels = false
                                    result.fold(
                                        onSuccess = { ms ->
                                            if (ms.isNotEmpty()) {
                                                models = ms
                                                showModelDropdown = true
                                                testResult = "获取模型成功: ${ms.size} 个"
                                            } else {
                                                testResult = "获取成功但无模型列表"
                                            }
                                        },
                                        onFailure = { e ->
                                            testResult = "获取模型失败: ${e.message}"
                                        }
                                    )
                                }
                            },
                            enabled = !fetchingModels,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.diaryColors.sweetButton),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            if (fetchingModels) {
                                Text("...", color = MaterialTheme.diaryColors.sweetText)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Get Models", tint = MaterialTheme.diaryColors.sweetText)
                            }
                        }
                    }
                }
                if (type == AiType.DEEPSEEK || type == AiType.CUSTOM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key", color = MaterialTheme.diaryColors.sweetText.copy(alpha = 0.7f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.diaryColors.sweetHighlight,
                            unfocusedBorderColor = MaterialTheme.diaryColors.sweetBorder,
                            focusedTextColor = MaterialTheme.diaryColors.sweetText,
                            unfocusedTextColor = MaterialTheme.diaryColors.sweetText,
                            cursorColor = MaterialTheme.diaryColors.sweetHighlight
                        )
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Action Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = {
                            testing = true
                            testResult = null
                            val config = AiConfig(type, baseUrl, model, apiKey)
                            scope.launch {
                                val http = com.xinkong.diary.Http.AiHttp()
                                http.updateConfig(config)
                                val result = http.chatWithAi("你好")
                                testing = false
                                testResult = result.fold(
                                    onSuccess = { "测试成功" },
                                    onFailure = { "失败: ${it.message}" }
                                )
                            }
                        },
                        enabled = !testing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.diaryColors.sweetButton.copy(alpha=0.8f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (testing) "测试中..." else "测试", color = MaterialTheme.diaryColors.sweetText)
                    }
                    Button(
                        onClick = {
                            onConfirm(AiConfig(type, baseUrl, model, apiKey))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.diaryColors.sweetHighlight),
                        modifier = Modifier
                    ) {
                        Text("确认", color = Color.White)
                    }
                }
                if (testResult != null) {
                    Text(
                        testResult!!,
                        color = if (testResult!!.contains("成功")) Color(0xFF4CAF50) else Color.Red,
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}