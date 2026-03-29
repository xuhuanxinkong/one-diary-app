package com.xinkong.diary.ui.screen.alarm

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
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.xinkong.diary.ui.animation.pressScaleEffect
import com.xinkong.diary.ui.theme.diaryColors
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
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign

@Composable
fun AlarmScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Int) -> Unit
) {
    val alarmViewModel: AlarmViewModel = viewModel()
    val alarms by alarmViewModel.alarms.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAlarm,
                containerColor = MaterialTheme.diaryColors.background2,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加闹钟")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // 上半部分：数字时钟
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f), // 占比 40%
                contentAlignment = Alignment.Center
            ) {
                DigitalClockCanvas()
            }

            // 下半部分：闹钟列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding.calculateBottomPadding())
                    .weight(0.6f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onCheckedChange = { isChecked ->
                            alarmViewModel.toggleAlarm(alarm, isChecked)
                        },
                        onClick = { onEditAlarm(alarm.id) }
                    )
                }
            }
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

            .fillMaxSize(0.9f)
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
            modifier = Modifier.offset(x=(-2).dp ,y = 10.dp)
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
                modifier = Modifier.offset(85.dp,y=30.dp)
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

@Composable
fun AlarmCard(
    alarm: AlarmEntity,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp), // 使用 20.dp 以贴合苹果风格现代大圆角卡片
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                    fontSize = 42.sp, // 加大时间主体字号，增加视觉冲击力
                    fontWeight = FontWeight.W600,
                    color = if (alarm.isActive) MaterialTheme.colorScheme.onSurface else Color.Gray
                )
                Spacer(modifier = Modifier.height(6.dp))

                // 改为显示备注，文本过长截断
                val remarkStr = if (alarm.remark.isNotEmpty()) " | ${alarm.remark}" else ""

                androidx.compose.material3.Text(
                    text = alarm.name + remarkStr,
                    fontSize = 14.sp,
                    color = if (alarm.isActive) MaterialTheme.colorScheme.onSurface.copy(alpha=0.7f) else Color.Gray,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = alarm.isActive,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(1.1f) // 微调开关组件大小
            )
        }
    }
}
