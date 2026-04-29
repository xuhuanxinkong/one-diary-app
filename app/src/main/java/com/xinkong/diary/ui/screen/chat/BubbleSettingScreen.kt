package com.xinkong.diary.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.ViewModel.BubbleConfigViewModel
import com.xinkong.diary.data.BubbleConfig

@Composable
fun BubbleSettingScreen(onBack: () -> Unit) {
    val bubbleViewModel: BubbleConfigViewModel = viewModel()
    val config by bubbleViewModel.config.collectAsStateWithLifecycle()

    var textSize by remember { mutableFloatStateOf(config.textSize) }
    var showRagResult by remember { mutableStateOf(config.showRagResult) }
    var showToolResult by remember { mutableStateOf(config.showToolResult) }
    var showVisibility by remember { mutableStateOf(config.showVisibility) }

    // 每次值变化自动保存
    LaunchedEffect(textSize, showRagResult, showToolResult, showVisibility) {
        bubbleViewModel.updateConfig(
            BubbleConfig(
                textSize = textSize,
                showRagResult = showRagResult,
                showToolResult = showToolResult,
                showVisibility = showVisibility
            )
        )
    }

    Scaffold(
        topBar = {
            Column {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.White)
                        .fillMaxWidth()
                        .padding(0.dp, 36.dp, 0.dp, 4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                    Text(
                        text = "气泡属性设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Divider(color = Color.LightGray, thickness = 0.5.dp)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp)
            ) {
                // 文字大小调节
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "气泡文字大小", fontSize = 16.sp, color = Color.Black)
                        Text(text = "${textSize.toInt()} sp", fontSize = 14.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = textSize,
                        onValueChange = { textSize = it },
                        valueRange = 9f..24f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))

                // 是否显示 RAG
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "显示 RAG 检索结果", fontSize = 16.sp, color = Color.Black)
                    Switch(checked = showRagResult, onCheckedChange = { showRagResult = it })
                }

                Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))

                // 是否显示 工具调用
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "显示工具调用结果", fontSize = 16.sp, color = Color.Black)
                    Switch(checked = showToolResult, onCheckedChange = { showToolResult = it })
                }

                Divider(color = Color(0xFFF0F0F0), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))

                // 是否显示 气泡可见性
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "显示可见性标识 (群聊)", fontSize = 16.sp, color = Color.Black)
                    Switch(checked = showVisibility, onCheckedChange = { showVisibility = it })
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "提示：上方各项配置将实时生效并保存在本地数据库中。",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}
