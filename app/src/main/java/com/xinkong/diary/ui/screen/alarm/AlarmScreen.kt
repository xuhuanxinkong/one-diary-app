package com.xinkong.diary.ui.screen.alarm

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import com.xinkong.diary.R
import com.xinkong.diary.data.AlarmEntity
import com.xinkong.diary.repository.AiChatConfig
import com.xinkong.diary.ui.animation.pressScaleEffect
import com.xinkong.diary.ViewModel.ChatViewModel
import kotlinx.coroutines.delay
import java.util.Calendar

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.ViewModel.AlarmViewModel
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.xinkong.diary.ui.theme.diaryColors

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AlarmScreen(
    onAddAlarm: (Boolean, Long?) -> Unit,  // Boolean 表示是否是AI提醒, Long? 是AI的ID
    onEditAlarm: (Int, Boolean) -> Unit,  // Int是id, Boolean是否是AI提醒
    chatViewModel: ChatViewModel,
    initialSelectedAiId: Long? = null,  // 从编辑界面返回时自动选中的AI
    onSelectedCategoryChange: (Long?) -> Unit = {}
) {
    val alarmViewModel: AlarmViewModel = viewModel()
    val alarms by alarmViewModel.alarms.collectAsStateWithLifecycle()
    val allAiConfigs by chatViewModel.allAiConfigsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    
    // 选中的分类：null 表示"用户"(普通闹钟)，否则是 AI 的 id
    var selectedCategory by rememberSaveable(initialSelectedAiId) { mutableStateOf(initialSelectedAiId) }
    
    // 是否收起顶部时钟区域
    var isClockCollapsed by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCategory) {
        onSelectedCategoryChange(selectedCategory)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            showOverlayPermissionDialog = true
        }
    }

    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = { Text("需要悬浮窗/显示在其他应用上层权限") },
            text = { Text("为了确保手机锁屏或在后台时闹钟能正常弹出提醒界面，请务必授予此权限。否则闹钟只能以通知形式静音显示。") },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayPermissionDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }
                }) {
                    Text("去设置", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 根据选中分类过滤闹钟
    val filteredAlarms = remember(alarms, selectedCategory, allAiConfigs) {
        if (selectedCategory == null) {
            // 用户分类：显示普通闹钟 (actionType != "PROCESS_NOTE")
            alarms.filter { it.actionType != "PROCESS_NOTE" }
        } else {
            // AI分类：显示该AI的提醒
            val selectedAiId = selectedCategory
            alarms.filter { alarm ->
                alarm.actionType == "PROCESS_NOTE" && 
                extractAiIdFromPayload(alarm.taskPayload) == selectedAiId
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF5F5F5) // 整个屏幕背景改为浅灰色
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()

        ) {
            // 上半部分：数字时钟 (可收起) + 收起按钮
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White) // 保留时钟区域为白色背景
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // 收起/展开按钮 (始终显示在右上角)
                    IconButton(
                        onClick = { isClockCollapsed = !isClockCollapsed },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 30.dp, end = 24.dp)
                    ) {
                        Icon(
                            imageVector = if (isClockCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isClockCollapsed) "展开" else "收起",
                            tint = Color.Gray
                        )
                    }
                }
                
                AnimatedVisibility(
                    visible = !isClockCollapsed,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DigitalClockCanvas()
                    }
                }
            }

            // 分割线
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.LightGray.copy(alpha = 0.5f)
            )
            
            // 下半部分：左右两栏布局
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 左侧：用户 + AI 列表
                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                        .padding(start = 8.dp, end = 8.dp, top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // "用户" 选项
                    CategoryItem(
                        name = "用户",
                        isSelected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        color = Color.LightGray.copy(alpha = 0.3f),
                        thickness = 1.dp
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // AI 列表
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(allAiConfigs, key = { it.id }) { aiConfig ->
                            AiCategoryItem(
                                aiConfig = aiConfig,
                                isSelected = selectedCategory == aiConfig.id,
                                onClick = { selectedCategory = aiConfig.id }
                            )
                        }
                    }
                }
                
                // 右侧：闹钟列表
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.9f)
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(filteredAlarms, key = { it.id }) { alarm ->
                            SwipeToDeleteAlarmCard(
                                alarm = alarm,
                                onCheckedChange = { isChecked ->
                                    alarmViewModel.toggleAlarm(alarm, isChecked)
                                },
                                onClick = { onEditAlarm(alarm.id, alarm.actionType == "PROCESS_NOTE") },
                                onDelete = { alarmViewModel.deleteAlarm(alarm.id) }
                            )
                        }
                        
                        // 新建卡片
                        item {
                            AddAlarmCard(
                                isAiReminder = selectedCategory != null,
                                onClick = { onAddAlarm(selectedCategory != null, selectedCategory) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// 从 taskPayload 中提取 aiId
private fun extractAiIdFromPayload(taskPayload: String?): Long? {
    if (taskPayload.isNullOrBlank()) return null
    return try {
        val id = org.json.JSONObject(taskPayload).optLong("aiId", -1L)
        id.takeIf { it > 0 }
    } catch (_: Exception) {
        null
    }
}

// 分类项 (用户)
@Composable
private fun CategoryItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFCFD8DC) else Color.Transparent,
        animationSpec = tween(300),
        label = "categoryBgColor"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "categoryTextColor"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(animatedBgColor)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            fontSize = 17.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = animatedTextColor
        )
    }
}

// AI 分类项
@Composable
private fun AiCategoryItem(
    aiConfig: AiChatConfig,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF90CAF9) else Color.Transparent,
        animationSpec = tween(300),
        label = "aiBgColor"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300),
        label = "aiTextColor"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(animatedBgColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = aiConfig.name,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = animatedTextColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

// 新建闹钟/提醒卡片
@Composable
private fun AddAlarmCard(
    isAiReminder: Boolean,
    onClick: () -> Unit
) {
    val addCardBorder = if (isAiReminder) Color(0xFFD6E9FF) else Color(0xFFEEEEEE)
    val addIconTint = if (isAiReminder) Color(0xFF2F6DB2) else Color.Gray
    val addTextTint = if (isAiReminder) Color(0xFF2F6DB2) else Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, addCardBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加",
                tint = addIconTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isAiReminder) "新建AI提醒" else "新建闹钟",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = addTextTint
            )
        }
    }
}

// 滑动删除闹钟卡片
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteAlarmCard(
    alarm: AlarmEntity,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFFF5252)
                    else -> Color.Transparent
                },
                label = "swipeColor"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(20.dp))
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.White
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        CompactAlarmCard(
            alarm = alarm,
            onCheckedChange = onCheckedChange,
            onClick = onClick
        )
    }
}

// 紧凑型闹钟卡片 (右侧显示)
@Composable
private fun CompactAlarmCard(
    alarm: AlarmEntity,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val cardBorder = Color(0xFFEDEDED)
    val timeColor = if (alarm.isActive) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {

                    // 显示时间
                    Text(
                        text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = timeColor
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                val remarkStr = if (alarm.remark.isNotEmpty()) " | ${alarm.remark}" else ""
                Text(
                    text = alarm.name + remarkStr,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (alarm.isActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else Color.Gray,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Switch(
                checked = alarm.isActive,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.68f)
            )
        }
    }
}

@Composable
fun DigitalClockCanvas() {
    var is24Hour by remember { mutableStateOf(true) }
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    val interactionSource = remember { MutableInteractionSource() }

    // 每秒刷新时间
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000)
        }
    }

    val hour = currentTime.get(if (is24Hour) Calendar.HOUR_OF_DAY else Calendar.HOUR)
    val displayHour = if (!is24Hour && hour == 0) 12 else hour
    val minute = currentTime.get(Calendar.MINUTE)
    val amPm = if (currentTime.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"

    // 将整个时钟放置在包含背景底图、限制尺寸为 240.dp 的 Box 中
    Box(
        modifier = Modifier

            .fillMaxSize(0.93f)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                is24Hour = !is24Hour
            }
            .pressScaleEffect(interactionSource, 0.9f)
,
        contentAlignment = Alignment.Center
    ) {
        // 挂钟底图
        Image(
            painter = painterResource(id = R.drawable.clock),
            contentDescription = "Clock Background",
            modifier = Modifier.fillMaxSize()
        )

        // 数字部分应用缩放和偏移限制在底板内
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // y轴往下稍微偏移以修正表盘视觉中心，x轴水平微调
            modifier = Modifier.offset(x=(-8).dp ,y = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // 按要求整体缩放使其正好放在时钟图片中
                modifier = Modifier.scale(0.65f)
            ) {
                // 十位和个位 小时
                SevenSegmentDigit(displayHour / 10,showZero = true)
                Spacer(modifier = Modifier.width(4.dp))
                SevenSegmentDigit(displayHour % 10, showZero = true)

                // 冒号
                Canvas(modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(16.dp, 60.dp)) {
                    
                    // 冒号的底层阴影
                    val shadowOffset = 4f
                    drawCircle(Color.Black.copy(alpha = 0.3f), radius = 4.dp.toPx(), center = Offset(size.width / 2 + shadowOffset, size.height / 3 + shadowOffset))
                    drawCircle(Color.Black.copy(alpha = 0.3f), radius = 4.dp.toPx(), center = Offset(size.width / 2 + shadowOffset, size.height * 2 / 3 + shadowOffset))
                    
                    drawCircle(Color.DarkGray, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height / 3))
                    drawCircle(Color.DarkGray, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height * 2 / 3))
                }

                // 十位和个位 分钟
                SevenSegmentDigit(minute / 10, showZero = true)
                Spacer(modifier = Modifier.width(4.dp))
                SevenSegmentDigit(minute % 10, showZero = true)
            }
        }

        if (!is24Hour) {
            Text(
                text = amPm,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(80.dp,y=30.dp)
            )
        }
    }
}

@Composable
fun SevenSegmentDigit(digit: Int, showZero: Boolean = true) {
    if (!showZero && digit == 0) {
        // Empty space for leading zero in 12h format
        Spacer(modifier = Modifier.size(40.dp, 80.dp))
        return
    }

    Canvas(modifier = Modifier.size(40.dp, 80.dp)) {
        val segmentWidth = size.width * 0.8f
        val segmentHeight = size.height * 0.1f
        val padding = size.width * 0.1f
        
        val colorOn = Color.DarkGray
        val colorOff = Color.LightGray.copy(alpha = 0.2f)

        // 7 segments layout (A-G)
        //  A
        // F B
        //  G
        // E C
        //  D
        
        val segments = getSegmentStates(digit)
        
        translate(left = padding, top = padding) {
            // A
            drawSegment(segments[0], colorOn, colorOff, 0f, 0f, segmentWidth, segmentHeight, true)
            // B
            drawSegment(segments[1], colorOn, colorOff, segmentWidth, 0f, segmentHeight, size.height/2 - padding, false)
            // C
            drawSegment(segments[2], colorOn, colorOff, segmentWidth, size.height/2 - padding, segmentHeight, size.height/2 - padding, false)
            // D
            drawSegment(segments[3], colorOn, colorOff, 0f, size.height - padding*2 - segmentHeight, segmentWidth, segmentHeight, true)
            // E
            drawSegment(segments[4], colorOn, colorOff, 0f, size.height/2 - padding, segmentHeight, size.height/2 - padding, false)
            // F
            drawSegment(segments[5], colorOn, colorOff, 0f, 0f, segmentHeight, size.height/2 - padding, false)
            // G
            drawSegment(segments[6], colorOn, colorOff, 0f, size.height/2 - padding - segmentHeight/2, segmentWidth, segmentHeight, true)
        }
    }
}

fun DrawScope.drawSegment(isOn: Boolean, onColor: Color, offColor: Color, x: Float, y: Float, w: Float, h: Float, isHorizontal: Boolean) {
    val color = if (isOn) onColor else offColor
    val path = Path()
    if (isHorizontal) {
        path.moveTo(x + h/2, y)
        path.lineTo(x + w - h/2, y)
        path.lineTo(x + w, y + h/2)
        path.lineTo(x + w - h/2, y + h)
        path.lineTo(x + h/2, y + h)
        path.lineTo(x, y + h/2)
        path.close()
    } else {
        path.moveTo(x + w/2, y)
        path.lineTo(x + w, y + w/2)
        path.lineTo(x + w, y + h - w/2)
        path.lineTo(x + w/2, y + h)
        path.lineTo(x, y + h - w/2)
        path.lineTo(x, y + w/2)
        path.close()
    }

    // 5. 为点亮的字母底层额外绘制阴影实现纵深特效
    if (isOn) {
        translate(left = 4f, top = 4f) {
            drawPath(path, Color.Black.copy(alpha = 0.3f))
        }
    }
    drawPath(path, color)
}

fun getSegmentStates(digit: Int): BooleanArray {
    return when(digit) {
        0 -> booleanArrayOf(true, true, true, true, true, true, false)
        1 -> booleanArrayOf(false, true, true, false, false, false, false)
        2 -> booleanArrayOf(true, true, false, true, true, false, true)
        3 -> booleanArrayOf(true, true, true, true, false, false, true)
        4 -> booleanArrayOf(false, true, true, false, false, true, true)
        5 -> booleanArrayOf(true, false, true, true, false, true, true)
        6 -> booleanArrayOf(true, false, true, true, true, true, true)
        7 -> booleanArrayOf(true, true, true, false, false, false, false)
        8 -> booleanArrayOf(true, true, true, true, true, true, true)
        9 -> booleanArrayOf(true, true, true, true, false, true, true)
        else -> booleanArrayOf(false, false, false, false, false, false, false)
    }
}

//@Composable
//fun AlarmCard(
//    alarm: AlarmEntity,
//    onCheckedChange: (Boolean) -> Unit,
//    onClick: () -> Unit
//) {
//    val isAiReminder = alarm.actionType == "PROCESS_NOTE"
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(horizontal = 12.dp)
//            .clickable { onClick() },
//        shape = RoundedCornerShape(20.dp),
//        colors = CardDefaults.cardColors(
//            containerColor = MaterialTheme.colorScheme.surfaceVariant
//        )
//    ) {
//        Row(
//            modifier = Modifier
//                .padding(20.dp)
//                .fillMaxWidth(),
//            horizontalArrangement = Arrangement.SpaceBetween,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Column(modifier = Modifier.weight(1f)) {
//                Row(verticalAlignment = Alignment.CenterVertically) {
//                    Text(
//                        text = String.format("%02d:%02d", alarm.hour, alarm.minute),
//                        fontSize = 42.sp,
//                        fontWeight = FontWeight.W600,
//                        color = if (alarm.isActive) MaterialTheme.colorScheme.onSurface else Color.Gray
//                    )
//                    if (isAiReminder) {
//                        Spacer(modifier = Modifier.width(8.dp))
//                        Box(
//                            modifier = Modifier
//                                .background(Color(0xFF5B9BD5).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
//                                .padding(horizontal = 8.dp, vertical = 4.dp)
//                        ) {
//                            Text(
//                                "AI",
//                                fontSize = 12.sp,
//                                fontWeight = FontWeight.Bold,
//                                color = Color(0xFF5B9BD5)
//                            )
//                        }
//                    }
//                }
//                Spacer(modifier = Modifier.height(6.dp))
//
//                val remarkStr = if (alarm.remark.isNotEmpty()) " | ${alarm.remark}" else ""
//
//                Text(
//                    text = alarm.name + remarkStr,
//                    fontSize = 14.sp,
//                    color = if (alarm.isActive) MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f) else Color.Gray,
//                    maxLines = 1,
//                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
//                )
//            }
//            Switch(
//                checked = alarm.isActive,
//                onCheckedChange = onCheckedChange,
//                modifier = Modifier.scale(1.1f)
//            )
//        }
//    }
//}
