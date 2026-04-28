package com.xinkong.diary.ui.screen.chat.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xinkong.diary.ViewModel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallSelectAiScreen(
    chatId: Long,
    isGroupChat: Boolean = false,
    onBack: () -> Unit,
    onAiSelected: (Long) -> Unit
) {
    val chatViewModel: ChatViewModel = viewModel()
    val aiConfigs by chatViewModel.getAiConfigsForChat(chatId, isGroupChat).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择通话对象", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF7F7F7)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F7F7))
                .padding(paddingValues)
        ) {
            if (aiConfigs.isEmpty()) {
                Text(
                    text = "暂无可用AI",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(aiConfigs) { ai ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAiSelected(ai.id) }
                                .background(Color.White)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 头像
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF5B9BD5)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (ai.avatarUri?.isNotEmpty() == true) {
                                    AsyncImage(
                                        model = ai.avatarUri,
                                        contentDescription = "AI头像",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = ai.name.take(2),
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // 名字
                            Text(
                                text = ai.name,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )

                            // 图标
                            IconButton(onClick = { onAiSelected(ai.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "拨打语音",
                                    tint = Color(0xFF07C160)
                                )
                            }
                        }
                        Divider(color = Color(0xFFEEEEEE), thickness = 1.dp, modifier = Modifier.padding(start = 82.dp))
                    }
                }
            }
        }
    }
}