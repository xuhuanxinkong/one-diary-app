package com.xinkong.diary.ui.screen.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import coil.compose.AsyncImage
import com.xinkong.diary.ViewModel.AlarmViewModel
import com.xinkong.diary.ViewModel.ChatViewModel
import com.xinkong.diary.data.AlarmEntity
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.ui.theme.diaryColors
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    id: Int,
    isAiReminder: Boolean,
    selectedAiId: Long? = null,
    alarmViewModel: AlarmViewModel,
    chatViewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val isNew = id == 0
    var initialAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    LaunchedEffect(id) {
        if (!isNew) {
            initialAlarm = alarmViewModel.getAlarmById(id)
        }
    }
    val defaultAlarm = remember(isAiReminder) {
        AlarmEntity(
            hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
            minute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE),
            actionType = if (isAiReminder) "PROCESS_NOTE" else "REMIND"
        )
    }
    var alarm by remember(initialAlarm) {
        mutableStateOf(initialAlarm ?: defaultAlarm)
    }
    if (!isNew && initialAlarm == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // 根据传入参数或现有闹钟类型决定模式
    val alarmType = if (isNew) {
        if (isAiReminder) 1 else 0
    } else {
        if (alarm.actionType == "PROCESS_NOTE") 1 else 0
    }
    
    var showDurationSheet by remember { mutableStateOf(false) }
    var showSnoozeSheet by remember { mutableStateOf(false) }
    var showRingtoneSheet by remember { mutableStateOf(false) }
    val allAiConfigs by chatViewModel.allAiConfigsState.collectAsStateWithLifecycle()
    val chatList by chatViewModel.chatListState.collectAsStateWithLifecycle()
    
    // AI提醒模式：直接使用传入的selectedAiId，不需要用户选择
    var currentAiId by remember(initialAlarm?.taskPayload, selectedAiId) {
        mutableStateOf(
            if (isNew && selectedAiId != null) selectedAiId
            else extractAiIdFromTaskPayload(initialAlarm?.taskPayload)
        )
    }
    val currentAi = allAiConfigs.firstOrNull { it.id == currentAiId }
    val currentAiChat = chatList.firstOrNull { it.id == currentAi?.chatId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isNew) {
                            if (alarmType == 1) "新建AI提醒" else "新建闹钟"
                        } else {
                            if (alarmType == 1) "编辑AI提醒" else "编辑闹钟"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "取消")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val payload = if (alarmType == 1) {
                            buildAiTaskPayload(currentAi)
                        } else null
                        alarmViewModel.saveAlarm(alarm.copy(taskPayload = payload))
                        onBack()
                    }) {
                        Text("确定", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!isNew) {
                        IconButton(onClick = {
                            alarmViewModel.deleteAlarm(id)
                            onBack()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            // 时间滚轮
            TimeWheelPicker(
                initialHour = alarm.hour,
                initialMinute = alarm.minute,
                onTimeChanged = { h, m -> alarm = alarm.copy(hour = h, minute = m) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(vertical = 16.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                // 名称输入框
                OutlinedTextField(
                    value = alarm.name,
                    onValueChange = { alarm = alarm.copy(name = it) },
                    label = { Text(if (alarmType == 1) "提醒名称" else "闹钟名称") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    singleLine = true
                )
            }

            // 主体卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                if (alarmType == 0) {
                    // 闹钟模式：合并卡片+分割线
                    Column(modifier = Modifier.padding(0.dp)) {
                        AlarmEditItem(
                            label = "响铃时长",
                            value = "${alarm.ringDuration} 分钟",
                            onClick = { showDurationSheet = true }
                        )
                        Divider(color = Color.LightGray.copy(alpha = 0.5f))
                        AlarmEditItem(
                            label = "再响间隔",
                            value = if (alarm.snoozeInterval == 0) "关闭" else "${alarm.snoozeInterval}分 / ${alarm.snoozeCount}次",
                            onClick = { showSnoozeSheet = true }
                        )
                        Divider(color = Color.LightGray.copy(alpha = 0.5f))
                        AlarmEditItem(
                            label = "铃声",
                            value = alarm.ringtoneUri.ifEmpty { "默认铃声" },
                            onClick = { showRingtoneSheet = true }
                        )
                        Divider(color = Color.LightGray.copy(alpha = 0.5f))
                        // 重复
                        Column(modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp)) {
                            Text("重复", fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val days = listOf("日", "一", "二", "三", "四", "五", "六")
                                days.forEachIndexed { index, day ->
                                    val dayValue = index + 1
                                    val isSelected = alarm.repeatDays.contains(dayValue)
                                    val actBorderColor by animateColorAsState(if (isSelected) MaterialTheme.diaryColors.background3 else Color.Transparent, label = "borderColor")
                                    val actBgColor by animateColorAsState(if (isSelected) MaterialTheme.diaryColors.background3 else Color.LightGray.copy(alpha=0.2f), label = "bgColor")
                                    val actContentColor by animateColorAsState(if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface, label = "textColor")
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .shadow(if (isSelected) 4.dp else 0.dp, shape = RoundedCornerShape(12.dp))
                                            .background(
                                                color = actBgColor,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .border(1.dp, actBorderColor, RoundedCornerShape(12.dp))
                                            .clickable {
                                                val newDays = if (isSelected) {
                                                    alarm.repeatDays - dayValue
                                                } else {
                                                    alarm.repeatDays + dayValue
                                                }
                                                alarm = alarm.copy(repeatDays = newDays.sorted())
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = day, color = actContentColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                        Divider(color = Color.LightGray.copy(alpha = 0.5f))
                        // 备注
                        OutlinedTextField(
                            value = alarm.remark,
                            onValueChange = { alarm = alarm.copy(remark = it) },
                            label = { Text("备注") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp, 8.dp, 16.dp, 16.dp),
                            minLines = 2
                        )
                    }
                } else {
                    // Ai提醒模式 - 简化：AI已自动选择，只显示AI信息和提示词
                    Column(modifier = Modifier.padding(0.dp).background(Color.White)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentAi != null) {
                                AiAvatar(
                                    avatarUri = currentAi.avatarUri,
                                    name = currentAi.name,
                                    size = 56.dp,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(currentAi.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(currentAiChat?.title ?: "未找到所属对话", color = Color.Gray, fontSize = 13.sp)
                                }
                            } else {
                                Box(
                                    modifier = Modifier.size(56.dp).background(Color(0xFF5B9BD5), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("AI", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("未选择AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("请从AI列表中选择", color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                        }

                        // 提示词（实际用remark字段）
                        OutlinedTextField(
                            value = alarm.remark,
                            onValueChange = { alarm = alarm.copy(remark = it) },
                            label = { Text("提示词") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            minLines = 2
                        )
                    }
                }
            }
        }

        // 各种底部弹窗
        if (showDurationSheet) {
            ModalBottomSheet(onDismissRequest = { showDurationSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("响铃时长", modifier = Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    listOf(1, 5, 10, 30).forEach { mins ->
                        TextButton(
                            onClick = {
                                alarm = alarm.copy(ringDuration = mins)
                                showDurationSheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("$mins 分钟", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
        if (showSnoozeSheet) {
            ModalBottomSheet(onDismissRequest = { showSnoozeSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("再响间隔选项", modifier = Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    val snoozeOptions = listOf(Pair(5, 3), Pair(10, 3), Pair(0, 0))
                    snoozeOptions.forEach { (interval, count) ->
                        TextButton(
                            onClick = {
                                alarm = alarm.copy(snoozeInterval = interval, snoozeCount = count)
                                showSnoozeSheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (interval == 0) "关闭" else "$interval 分钟 / $count 次", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        if (showRingtoneSheet) {
            ModalBottomSheet(onDismissRequest = { showRingtoneSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("选择铃声", modifier = Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    val ringtones = listOf(
                        "默认铃声" to "",
                        "Oxygen" to "content://settings/system/alarm_alert",
                        "Argon" to "content://media/internal/audio/media/12"
                    )
                    ringtones.forEach { (name, uri) ->
                        TextButton(
                            onClick = {
                                alarm = alarm.copy(ringtoneUri = if (name == "默认铃声") "" else name)
                                showRingtoneSheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(name, fontSize = 16.sp, color = if ((alarm.ringtoneUri.ifEmpty { "默认铃声" }) == name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// 分段按钮实现
@Composable
fun SegmentedButton(options: List<String>, selectedIndex: Int, onOptionSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onOptionSelected(idx) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp
                )
            }
            if (idx != options.lastIndex) {
                Divider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.7f)
                )
            }
        }
    }
}

// 合并卡片的单项
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditItem(label: String, value: String,  onClick: () -> Unit) {



    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp, 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp)
        Text(value, color = Color.Gray, fontSize = 15.sp)
    }


//    if (showDurationSheet) {
//        ModalBottomSheet(onDismissRequest = { showDurationSheet = false }) {
//            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
//                Text("响铃时长", modifier = Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
//                listOf(1, 5, 10, 30).forEach { mins ->
//                    TextButton(
//                        onClick = {
//                            alarm = alarm.copy(ringDuration = mins)
//                            showDurationSheet = false
//                        },
//                        modifier = Modifier.fillMaxWidth()
//                    ) { Text("$mins 分钟", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface) }
//                }
//            }
//        }
//    }
//
//    if (showSnoozeSheet) {
//        ModalBottomSheet(onDismissRequest = { showSnoozeSheet = false }) {
//            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
//                Text("再响间隔选项", modifier = Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
//                val snoozeOptions = listOf(Pair(5, 3), Pair(10, 3), Pair(0, 0))
//                snoozeOptions.forEach { (interval, count) ->
//                    TextButton(
//                        onClick = {
//                            alarm = alarm.copy(snoozeInterval = interval, snoozeCount = count)
//                            showSnoozeSheet = false
//                        },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        Text(if (interval == 0) "关闭" else "$interval 分钟 / $count 次", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
//                    }
//                }
//            }
//        }
//    }
//
//    if (showRingtoneSheet) {
//        ModalBottomSheet(onDismissRequest = { showRingtoneSheet = false }) {
//            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
//                Text("选择铃声", modifier = Modifier.padding(16.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
//                val ringtones = listOf(
//                    "默认铃声" to "",
//                    "Oxygen" to "content://settings/system/alarm_alert",
//                    "Argon" to "content://media/internal/audio/media/12"
//                )
//                ringtones.forEach { (name, uri) ->
//                    TextButton(
//                        onClick = {
//                            alarm = alarm.copy(ringtoneUri = if (name == "默认铃声") "" else name) // Just saving names for now
//                            showRingtoneSheet = false
//                        },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        Text(name, fontSize = 16.sp, color = if ((alarm.ringtoneUri.ifEmpty{"默认铃声"}) == name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
//                    }
//                }
//            }
//        }
//    }
}

@Composable
fun ConfigCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

private fun extractAiIdFromTaskPayload(taskPayload: String?): Long? {
    if (taskPayload.isNullOrBlank()) return null
    return try {
        val id = JSONObject(taskPayload).optLong("aiId", -1L)
        id.takeIf { it > 0 }
    } catch (_: Exception) {
        null
    }
}

private fun buildAiTaskPayload(aiConfig: AiChatConfig?): String? {
    if (aiConfig == null) return null
    return JSONObject()
        .put("aiId", aiConfig.id)
        .put("chatId", aiConfig.chatId)
        .put("aiName", aiConfig.name)
        .put("avatarUri", aiConfig.avatarUri)
        .put("referencedDiaryId", aiConfig.referencedDiaryId)
        .toString()
}

@Composable
private fun AiAvatar(
    avatarUri: String,
    name: String,
    size: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    if (avatarUri.isNotBlank()) {
        AsyncImage(
            model = avatarUri,
            contentDescription = "AI头像",
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF5B9BD5))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color(0xFF5B9BD5), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(2), color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TimeWheelPicker(
    initialHour: Int,
    initialMinute: Int,
    onTimeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 60.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = initialHour)
    val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = initialMinute)

    // 计算当前选中的小时和分钟
    fun getSelectedHour(): Int {
        return hourState.firstVisibleItemIndex + 
            if (hourState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
    }
    
    fun getSelectedMinute(): Int {
        return minuteState.firstVisibleItemIndex + 
            if (minuteState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
    }

    Row(
        modifier = modifier.background(Color.White, RoundedCornerShape(24.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyColumn(
            state = hourState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = hourState),
            modifier = Modifier.weight(1f).height(itemHeight * 3),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight)
        ) {
            items(24) { h ->
                val isCenter by remember { 
                    derivedStateOf { 
                        val actualIndex = hourState.firstVisibleItemIndex + if (hourState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
                        actualIndex == h
                    }
                }
                Box(modifier = Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = String.format("%02d", h),
                        fontSize = if (isCenter) 40.sp else 24.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter) MaterialTheme.colorScheme.onSurface else Color.Gray,
                        modifier = Modifier.alpha(if (isCenter) 1f else 0.4f)
                    )
                }
            }
        }

        Text(":", fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))

        LazyColumn(
            state = minuteState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = minuteState),
            modifier = Modifier.weight(1f).height(itemHeight * 3),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = itemHeight)
        ) {
            items(60) { m ->
                val isCenter by remember { 
                    derivedStateOf { 
                        val actualIndex = minuteState.firstVisibleItemIndex + if (minuteState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
                        actualIndex == m
                    }
                }
                Box(modifier = Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = String.format("%02d", m),
                        fontSize = if (isCenter) 40.sp else 24.sp,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter) MaterialTheme.colorScheme.onSurface else Color.Gray,
                        modifier = Modifier.alpha(if (isCenter) 1f else 0.4f)
                    )
                }
            }
        }
        
        // 滚动停止后自动对齐并更新时间
        LaunchedEffect(hourState.isScrollInProgress) {
            if (!hourState.isScrollInProgress) {
                val targetHour = getSelectedHour().coerceIn(0, 23)
                // 滚动到精确位置
                hourState.animateScrollToItem(targetHour)
                onTimeChanged(targetHour, getSelectedMinute().coerceIn(0, 59))
            }
        }
        
        LaunchedEffect(minuteState.isScrollInProgress) {
            if (!minuteState.isScrollInProgress) {
                val targetMinute = getSelectedMinute().coerceIn(0, 59)
                // 滚动到精确位置
                minuteState.animateScrollToItem(targetMinute)
                onTimeChanged(getSelectedHour().coerceIn(0, 23), targetMinute)
            }
        }
    }
}
