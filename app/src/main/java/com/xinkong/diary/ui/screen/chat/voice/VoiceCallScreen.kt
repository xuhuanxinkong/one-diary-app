package com.xinkong.diary.ui.screen.chat.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.repository.AiChatConfig

@Composable
fun VoiceCallScreen(
    chatId: Long,
    aiId: Long,
    isGroupChat: Boolean = false,
    viewModel: CallViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    onMinimizeClick: () -> Unit = {},
    onHangUp: () -> Unit = {}
) {
    val callState by viewModel.callState.collectAsState()
    val rms by viewModel.rmsValue.collectAsState()
    val userText by viewModel.userText.collectAsState()
    val aiText by viewModel.aiText.collectAsState()
    
    val aiConfigs by chatViewModel.getAiConfigsForChat(chatId, isGroupChat).collectAsState(initial = emptyList())
    val selectedAi = aiConfigs.find { it.id == aiId }

    LaunchedEffect(selectedAi) {
        if (selectedAi != null) {
            viewModel.initData(chatId, selectedAi, chatViewModel)
            kotlinx.coroutines.delay(300) // 给语音识别器一点初始化的时间
            viewModel.startCall() // 自动拨号状态
        }
    }

    val isPaused by viewModel.isPaused.collectAsState()

    // 主体背景：深色沉浸式
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        CallTopBar(
            state = callState,
            isPaused = isPaused,
            onMinimizeClick = onMinimizeClick,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CallAvatarAndWaves(
                state = callState, 
                rmsValue = rms, 
                aiConfig = viewModel.currentAiConfig.collectAsState().value,
                isPaused = isPaused
            )
            Spacer(modifier = Modifier.height(48.dp))
            CallStreamText(userText = userText, aiText = aiText)
        }

        // 下方声音波动线设计
        if (callState == CallState.Listening && !isPaused) {
            WaveLineIndicator(
                rmsValue = rms,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 180.dp)
            )
        }

        CallBottomPanel(
            state = callState,
            isPaused = isPaused,
            isAutoRead = viewModel.isAutoRead.collectAsState().value,
            onHangUp = {
                viewModel.endCall()
                onHangUp()
            },
            onAutoReadToggle = {
                viewModel.toggleAutoRead()
            },
            onPauseToggle = {
                viewModel.togglePause()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        )
    }
}

@Composable
fun CallTopBar(
    state: CallState,
    isPaused: Boolean,
    onMinimizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateText = if (isPaused) {
        "通话已暂停"
    } else {
        when (state) {
            is CallState.Idle -> "准备中..."
            is CallState.Listening -> "正在聆听..."
            is CallState.Thinking -> "AI 思考中..."
            is CallState.Speaking -> "AI 说话中..."
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMinimizeClick) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = "缩小窗",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.scale(0.8f) // 示意缩小
            )
        }

        Text(
            text = stateText,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        // 占位符以保证中间文字绝对居中
        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
fun CallAvatarAndWaves(
    state: CallState,
    rmsValue: Float,
    aiConfig: AiChatConfig?,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    // 根据状态定义动画类型
    val infiniteTransition = rememberInfiniteTransition()

    // 呼吸动画（用于思考和说话状态）
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // 声波随音量大小缩放（用于聆听状态），稍微做一下平滑和归一化处理
    val smoothedRms by animateFloatAsState(
        targetValue = 1f + (rmsValue / 15f).coerceIn(0f, 0.4f),
        animationSpec = tween(100)
    )

    // 最终的光圈大小
    val waveScale = if (isPaused) 1f else {
        when (state) {
            is CallState.Listening -> smoothedRms
            is CallState.Thinking -> breathingScale
            is CallState.Speaking -> breathingScale * 1.05f
            is CallState.Idle -> 1f
        }
    }

    // 光圈颜色及透明度
    val waveAlpha = if (isPaused) 0f else {
        when (state) {
            is CallState.Listening -> 0.15f
            is CallState.Thinking -> 0.05f
            is CallState.Speaking -> 0.2f
            is CallState.Idle -> 0f
        }
    }

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // 外层动态波纹圈
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(waveScale)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = waveAlpha))
        )

        // 中层波纹（说话时多一层光圈）
        if (state == CallState.Speaking && !isPaused) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(waveScale * 1.1f)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
            )
        }

        // 中心固定头像/Icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0xFF3B3B3B)),
            contentAlignment = Alignment.Center
        ) {
            if (aiConfig?.avatarUri?.isNotEmpty() == true) {
                AsyncImage(
                    model = aiConfig.avatarUri,
                    contentDescription = "AI 头像",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = aiConfig?.name?.take(2) ?: "AI", 
                    color = Color.White, 
                    fontSize = 24.sp
                )
            }
        }
    }
}

@Composable
fun CallStreamText(
    userText: String,
    aiText: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 用户输入显示，显示在上方且颜色呈现区分
            if (userText.isNotEmpty()) {
                Text(
                    text = userText,
                    color = Color(0xFF07C160).copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // AI 回复或思考状态，位于用户输入下方
            if (aiText.isNotEmpty()) {
                Text(
                    text = aiText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// 基于波动的线条组件
@Composable
fun WaveLineIndicator(
    rmsValue: Float,
    modifier: Modifier = Modifier
) {
    val heightScale by animateFloatAsState(
        targetValue = (rmsValue / 10f).coerceIn(0.1f, 1f),
        animationSpec = tween(150)
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 5) {
            val h = if (i % 2 == 0) heightScale * 30 else heightScale * 60
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(4.dp)
                    .height(h.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF07C160))
            )
        }
    }
}

@Composable
fun CallBottomPanel(
    state: CallState,
    isPaused: Boolean,
    isAutoRead: Boolean,
    onHangUp: () -> Unit,
    onAutoReadToggle: () -> Unit,
    onPauseToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 最左边：暂停按钮
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onPauseToggle() }
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isPaused) 0.5f else 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "继续" else "暂停",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(if (isPaused) "继续" else "暂停", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }

        // 中间：挂断按钮
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935))
                    .clickable { onHangUp() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "挂断",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("挂断", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }

        // 最右边：自动朗读切换开关
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onAutoReadToggle() }
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isAutoRead) 0.5f else 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAutoRead) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = if (isAutoRead) "自动朗读开" else "自动朗读关",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(if (isAutoRead) "自动朗读" else "静音显示", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}
