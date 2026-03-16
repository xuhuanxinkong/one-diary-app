package com.xinkong.diary.ui.screen.home

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.Data.AiState
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.repository.Diary
import com.xinkong.diary.ui.theme.DiarydTheme
import com.xinkong.diary.ui.theme.diaryColors

import kotlinx.coroutines.delay

@Composable
fun FunScreen(viewModel: DiaryViewModel = viewModel()) {
    // 主题色定义
    val sweetBg = MaterialTheme.diaryColors.background
    val sweetText = MaterialTheme.diaryColors.sweetText
    val sweetButton = MaterialTheme.diaryColors.sweetButton
    val sweetHighlight = MaterialTheme.diaryColors.sweetHighlight


    val bgColor =  sweetBg

    var isAiCardExpanded by remember { mutableStateOf(false) }
    var userInput by remember { mutableStateOf("") }
    var selectedDiary by remember { mutableStateOf<Diary?>(null) }

    // AI State Processing
    val aiState by viewModel.aiState.collectAsState()
    var displayedAiText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }

    LaunchedEffect(aiState) {
        if (aiState is AiState.Success) {
            val fullText = (aiState as AiState.Success).result
            displayedAiText = ""
            isTyping = true
            fullText.forEach { char ->
                displayedAiText += char
                delay(30) // Typing speed
            }
            isTyping = false
        } else if (aiState is AiState.Loading) {
            displayedAiText = "Thinking..."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(10.dp)
    ) {
        // AI Card
        AiCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp,60.dp,5.dp,5.dp)
                .weight(if (isAiCardExpanded) 0.7f else 0.6f)
                .animateContentSize(),
            isExpanded = isAiCardExpanded,
            onExpandToggle = { isAiCardExpanded = !isAiCardExpanded },
            displayedText = displayedAiText,
            isTyping = isTyping,
            viewModel = viewModel,
            selectedDiary = selectedDiary,
            onDiarySelected = { selectedDiary = it },
            onDiaryDeselected = { selectedDiary = null },
            textColor = sweetText,
            highlightColor = sweetHighlight,
            buttonColor = sweetButton,
            bgColor = bgColor
        )

        // User Input Card (Hidden when expanded)
        if (!isAiCardExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            UserCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .padding(bottom = 60.dp),
                userInput = userInput,
                onUserInputChange = { userInput = it },
                onSubmit = {
                    val contentToSum = buildString {
                        if (selectedDiary != null) {
                            append("【日记标题】：${selectedDiary!!.title}\n")
                            append("【日记内容】：${selectedDiary!!.content}\n")
                        }
                        append("【用户需求】：$userInput")
                    }
                    viewModel.sumDiary(contentToSum)
                },
                textColor = sweetText,
                buttonColor = sweetButton,
                bgColor = bgColor
            )
        }
    }
}

@Composable
fun AiCard(
    modifier: Modifier,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    displayedText: String,
    isTyping: Boolean,
    viewModel: DiaryViewModel,
    selectedDiary: Diary?,
    onDiarySelected: (Diary) -> Unit,
    onDiaryDeselected: () -> Unit,
    textColor: Color,
    highlightColor: Color,
    buttonColor: Color,
    bgColor: Color
) {
    var showNoteList by remember { mutableStateOf(false) }
    val noteListState = remember { mutableStateListOf<Diary>() }
    
    LaunchedEffect(Unit) {
        viewModel.findAllDiary().collect { list ->
            noteListState.clear()
            noteListState.addAll(list)
        }
    }

    // Border Animation
    val infiniteTransition = rememberInfiniteTransition(label = "border_anim")
    val gradientColor1 by infiniteTransition.animateColor(
        initialValue = MaterialTheme.diaryColors.sweetPink, // Pink
        targetValue = MaterialTheme.diaryColors.sweetPurple,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "color1"
    )
    
    val borderBrush = if (isTyping) {
        Brush.linearGradient(colors = listOf(gradientColor1, Color.Blue))
    } else {
        SolidColor(MaterialTheme.diaryColors.sweetPink) // Static Pink
    }

    Card(
        modifier = modifier
            .border(width = 3.dp, brush = borderBrush, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Note Button
                    IconButton(
                        onClick = { showNoteList = !showNoteList },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = buttonColor)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Note")
                    }
                    // Selected Note Display
                    if (selectedDiary != null) {
                        Row(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(highlightColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedDiary.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                                modifier = Modifier.widthIn(max = 100.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = highlightColor,
                                modifier = Modifier.size(16.dp).clickable { onDiaryDeselected() }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // Expand/Collapse Button
                    TextButton(
                        onClick = onExpandToggle,
                        colors = ButtonDefaults.textButtonColors(containerColor = buttonColor)
                    ) {
                        Text(if (isExpanded) "缩小" else "放大")
                    }
                }
                
                Divider(color = highlightColor.copy(alpha = 0.3f))
                // Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = displayedText,
                        color = textColor,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp)
                    )
                    // Note List Overlay
                    if (showNoteList) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .zIndex(2f),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            border = BorderStroke(1.dp, highlightColor),
                            colors = CardDefaults.cardColors(containerColor = bgColor)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(noteListState) { diary ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onDiarySelected(diary)
                                                showNoteList = false
                                            }
                                            .padding(8.dp)
                                    ) {
                                        Text(text = diary.title, fontWeight = FontWeight.Bold, color = highlightColor)
                                        Text(
                                            text = diary.content.take(20),
                                            color = textColor.copy(alpha = 0.5f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Divider(color = highlightColor.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserCard(
    modifier: Modifier,
    userInput: String,
    onUserInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    textColor: Color,
    buttonColor: Color,
    bgColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.diaryColors.primary),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            TextField(
                value = userInput,
                onValueChange = onUserInputChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 50.dp),
                placeholder = { Text("请输入...", color = textColor.copy(alpha = 0.5f)) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                )
            )
            Button(
                onClick = onSubmit,
                modifier = Modifier.align(Alignment.BottomEnd),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Text("提交", color = textColor)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp), tint = textColor)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Preview2() {
    DiarydTheme {
        FunScreen()
    }
}