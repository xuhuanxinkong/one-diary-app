package com.xinkong.diary.ui.screen.home

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.repository.AiChatConfig
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
import com.xinkong.diary.ViewModel.ChatViewModel
import java.io.OutputStreamWriter
import java.io.InputStreamReader
import java.io.BufferedReader
import com.xinkong.diary.repository.Diary
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FunScreen(onDismiss: () -> Unit) {
    var showSheet by remember { mutableStateOf(true) }
    val viewModel: DiaryViewModel = viewModel()


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
fun AiSection(config: AiChatConfig? = null, onSave: ((AiChatConfig) -> Unit)? = null) {
    // 如果传入了 config，使用 config 的值作为初始值
    var aiBaseUrl by remember(config?.baseUrl) { mutableStateOf(config?.baseUrl ?: "https://api.deepseek.com/v1/chat/completions") }
    var aiModel by remember(config?.model) { mutableStateOf(config?.model ?: "deepseek-chat") }
    var aiApiKey by remember(config?.apiKey) { mutableStateOf(config?.apiKey ?: "") }
    var aiModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showUrlDropdown by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = remember { CoroutineScope(Dispatchers.Main) }
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel()
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        // Base URL Input with Preset Dropdown
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = aiBaseUrl,
                onValueChange = { aiBaseUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                trailingIcon = {
                    Box {
                        IconButton(onClick = { showUrlDropdown = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select URL")
                        }
                        DropdownMenu(
                            expanded = showUrlDropdown,
                            onDismissRequest = { showUrlDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("DeepSeek") },
                                onClick = {
                                    aiBaseUrl = "https://api.deepseek.com/v1/chat/completions"
                                    aiModel = "deepseek-chat"
                                    showUrlDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ollama测试") },
                                onClick = {
                                    aiBaseUrl = "http://10.0.2.2:11434/v1/chat/completions"
                                    aiModel = "llama3"
                                    showUrlDropdown = false
                                }
                            )
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Model Input with Fetch
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = aiModel,
                onValueChange = { aiModel = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                        viewModel.fetchModels(aiBaseUrl, aiApiKey).onSuccess {
                                aiModels = it
                                showModelDropdown = true
                                Toast.makeText(context, "已获取模型列表", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "获取模型失败: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Fetch Models")
                    }
                }
            )
            DropdownMenu(
                expanded = showModelDropdown,
                onDismissRequest = { showModelDropdown = false }
            ) {
                if(aiModels.isEmpty()) {
                    DropdownMenuItem(text = { Text("无可用模型") }, onClick = { showModelDropdown = false })
                }
                aiModels.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m) },
                        onClick = { aiModel = m; showModelDropdown = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = aiApiKey,
            onValueChange = { aiApiKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    isTesting = true
                    scope.launch {
                        val testConfig = AiChatConfig(chatId = 0, baseUrl = aiBaseUrl, model = aiModel, apiKey = aiApiKey)
                        viewModel.testConnection(testConfig).onSuccess {
                            testResult = "测试成功"
                        }.onFailure {
                            testResult = "测试失败: ${it.message}"
                        }
                        isTesting = false
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.diaryColors.secondary)
            ) {
                Text(if(isTesting) "测试中..." else "测试连接")
            }
            Button(
                onClick = {
                    val newConfig = config?.copy(baseUrl = aiBaseUrl, model = aiModel, apiKey = aiApiKey)
                    if (newConfig != null && onSave != null) {
                        onSave(newConfig)
                    }
                    Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.diaryColors.primary)
            ) {
                Text("保存配置")
            }
        }
        if (testResult != null) {
            Text(
                text = testResult!!,
                color = if(testResult!!.contains("成功")) Color(0xFF4CAF50) else Color.Red,
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 12.sp
            )
        }
    }
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
        containerColor = Color.White
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
        containerColor = Color.White
    )
}

//================== 选择模式可复用组件 ==================

@Composable
fun SelectionModeTopBar(
    selectedCount: Int,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.diaryColors.background2)
            .padding(20.dp, 60.dp, 20.dp, 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "退出选择",
            modifier = Modifier
                .size(32.dp)
                .clickable { onClose() }
        )
        Text(
            text = "已选择 $selectedCount 项",
            fontSize = 20.sp,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun SelectionModeBottomBar(
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.diaryColors.background2)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onMerge() }.padding(8.dp)
        ) {
            Text("📎", fontSize = 20.sp)
            Text("合并", fontSize = 12.sp, color = Color.Gray)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onSplit() }.padding(8.dp)
        ) {
            Text("✂", fontSize = 20.sp)
            Text("拆分", fontSize = 12.sp, color = Color.Gray)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onMove() }.padding(8.dp)
        ) {
            Text("📁", fontSize = 20.sp)
            Text("移动", fontSize = 12.sp)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onDelete() }.padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = Color.Red,
                modifier = Modifier.size(20.dp)
            )
            Text("删除", fontSize = 12.sp, color = Color.Red)
        }
    }
}

@Composable
fun MoveToCategoryBar(
    tags: List<String>,
    onTagSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("移动到:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "取消",
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onDismiss() }
            )
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tags) { tag ->
                Button(
                    onClick = { onTagSelected(tag) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.diaryColors.primary
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(tag, color = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun BatchDeleteDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除选中的 $count 项吗？") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("删除", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

fun getAvailableTags(context: Context): List<String> {
    val prefs = context.getSharedPreferences("tag_prefs", Context.MODE_PRIVATE)
    val set = prefs.getStringSet("custom_tags", emptySet()) ?: emptySet()
    val customTags = set.mapNotNull { entry ->
        val parts = entry.split("|")
        if (parts.isNotEmpty()) parts[0] else null
    }
    return listOf("未分类") + customTags
}
