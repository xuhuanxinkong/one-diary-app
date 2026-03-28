package com.xinkong.diary.ui.screen.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.xinkong.diary.ViewModel.AlarmViewModel
import com.xinkong.diary.data.AlarmEntity
import com.xinkong.diary.ui.theme.diaryColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    id: Int,
    alarmViewModel: AlarmViewModel,
    onBack: () -> Unit
) {
    val isNew = id == 0
    var initialAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    
    LaunchedEffect(id) {
        if (!isNew) {
            initialAlarm = alarmViewModel.getAlarmById(id)
        }
    }
    
    val defaultAlarm = remember {
        AlarmEntity(
            hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
            minute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
        )
    }

    var alarm by remember(initialAlarm) {
        mutableStateOf(initialAlarm ?: defaultAlarm)
    }

    // Only render the UI if it's new, or if we have successfully loaded the existing alarm.
    if (!isNew && initialAlarm == null) {
        // You could show a loading spinner here
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showDurationSheet by remember { mutableStateOf(false) }
    var showSnoozeSheet by remember { mutableStateOf(false) }
    var showRingtoneSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建闹钟" else "编辑闹钟") },
                navigationIcon = {
                    IconButton(onClick = { 
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "取消")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        alarmViewModel.saveAlarm(alarm)
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
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 时间滚轮
            TimeWheelPicker(
                initialHour = alarm.hour,
                initialMinute = alarm.minute,
                onTimeChanged = { h, m ->
                    alarm = alarm.copy(hour = h, minute = m)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(vertical = 16.dp)
            )

            // 配置列表
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 第一栏：闹钟名称
                item {
                    ConfigCard {
                        OutlinedTextField(
                            value = alarm.name,
                            onValueChange = { alarm = alarm.copy(name = it) },
                            label = { Text("闹钟名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                
                // 第二栏：响铃时长
                item {
                    ConfigCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showDurationSheet = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("响铃时长", fontSize = 16.sp)
                            Text("${alarm.ringDuration} 分钟", color = Color.Gray)
                        }
                    }
                }

                // 第三栏：再响间隔 (时间与次数)
                item {
                    ConfigCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showSnoozeSheet = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("再响间隔", fontSize = 16.sp)
                            val snoozeText = if (alarm.snoozeInterval == 0) "关闭" else "${alarm.snoozeInterval}分 / ${alarm.snoozeCount}次"
                            Text(snoozeText, color = Color.Gray)
                        }
                    }
                }

                item {
                    ConfigCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showRingtoneSheet = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("铃声", fontSize = 16.sp)
                            Text(alarm.ringtoneUri.ifEmpty { "默认铃声" }, color = Color.Gray)
                        }
                    }
                }

                // 第五栏：重复
                item {
                    ConfigCard {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("重复", fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val days = listOf("日", "一", "二", "三", "四", "五", "六")
                                days.forEachIndexed { index, day ->
                                    val dayValue = index + 1 // 1=Sun (or modify logic for Mon=1)
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
                    }
                }

                // 第六栏：备注
                item {
                    ConfigCard {
                        OutlinedTextField(
                            value = alarm.remark,
                            onValueChange = { alarm = alarm.copy(remark = it) },
                            label = { Text("备注") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }
                }
            }
        }
    }

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
                            alarm = alarm.copy(ringtoneUri = if (name == "默认铃声") "" else name) // Just saving names for now
                            showRingtoneSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(name, fontSize = 16.sp, color = if ((alarm.ringtoneUri.ifEmpty{"默认铃声"}) == name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
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

@Composable
fun TimeWheelPicker(
    initialHour: Int,
    initialMinute: Int,
    onTimeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 60.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = initialHour)
    val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = initialMinute)

    Row(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp)),
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
        
        LaunchedEffect(hourState.isScrollInProgress, minuteState.isScrollInProgress) {
            if (!hourState.isScrollInProgress && !minuteState.isScrollInProgress) {
                val selH = hourState.firstVisibleItemIndex + if (hourState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
                val selM = minuteState.firstVisibleItemIndex + if (minuteState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
                onTimeChanged(selH, selM)
            }
        }
    }
}
