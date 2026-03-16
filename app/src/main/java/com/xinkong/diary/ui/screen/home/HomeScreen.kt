package com.xinkong.diary.ui.screen.home


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.ViewModel.NavigationViewModel
import com.xinkong.diary.ViewModel.Route
import com.xinkong.diary.ViewModel.Tab

import com.xinkong.diary.repository.Diary
import com.xinkong.diary.repository.DiarySaver
import com.xinkong.diary.ui.animation.pressScaleEffect
import com.xinkong.diary.ui.animation.toggleRotateEffect
import com.xinkong.diary.ui.screen.chat.ChatScreen
import com.xinkong.diary.ui.screen.chat.TalkScreen
import com.xinkong.diary.ui.theme.DiarydTheme
import com.xinkong.diary.ui.theme.currentDiaryColors
import com.xinkong.diary.ui.theme.diaryColors
import java.text.SimpleDateFormat
import java.util.Locale
import com.xinkong.diary.ViewModel.ChatViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.combinedClickable



@Composable
fun HomeScreen(){
    val navViewModel: NavigationViewModel = viewModel()
    val selectedTab = navViewModel.selectedTab

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showMoveBar by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val diaryViewModel: DiaryViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()
    val contentList by diaryViewModel.listState.collectAsStateWithLifecycle()
    val chatList by chatViewModel.chatListState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val availableTags = remember(contentList, chatList) {
        val allTags = (contentList.mapNotNull { it.tag } + chatList.map { it.tag } +
            getAvailableTags(context)).distinct()
        listOf("未分类") + allTags.filter { it != "未分类" }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isSelectionMode) {
                Column {
                    AnimatedVisibility(visible = showMoveBar) {
                        MoveToCategoryBar(
                            tags = availableTags,
                            onTagSelected = { tag ->
                                if (selectedTab == Tab.HOME) {
                                    contentList.filter { it.id in selectedIds }.forEach {
                                        diaryViewModel.updateDiary(it.copy(tag = tag))
                                    }
                                } else {
                                    chatList.filter { it.id in selectedIds }.forEach {
                                        chatViewModel.updateChat(it.copy(tag = tag))
                                    }
                                }
                                showMoveBar = false
                                isSelectionMode = false
                                selectedIds = emptySet()
                            },
                            onDismiss = { showMoveBar = false }
                        )
                    }
                    SelectionModeBottomBar(
                        onMerge = { /* TODO */ },
                        onSplit = { /* TODO */ },
                        onMove = { showMoveBar = !showMoveBar },
                        onDelete = { showDeleteDialog = true }
                    )
                }
            } else {
                BottomNavigate(
                    selectedTab = selectedTab,
                    onTabSelected = { tab -> navViewModel.selectTab(tab) }
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            label = "TabSwitch",
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                }
            }
        ) { tab ->
            when (tab) {
                Tab.HOME -> ContentShow(
                    modifier = Modifier.fillMaxSize(),
                    isSelectionMode = isSelectionMode,
                    selectedIds = selectedIds,
                    onEnterSelection = { id ->
                        isSelectionMode = true
                        selectedIds = setOf(id)
                        showMoveBar = false
                    },
                    onToggleSelection = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        if (selectedIds.isEmpty()) isSelectionMode = false
                    },
                    onExitSelection = {
                        isSelectionMode = false
                        selectedIds = emptySet()
                        showMoveBar = false
                    },
                    onClick = { diary ->
                        navViewModel.navigateTo(Route.DiaryDetail(diary.id))
                    }
                )
                Tab.AI -> ChatScreen(
                    isSelectionMode = isSelectionMode,
                    selectedIds = selectedIds,
                    onEnterSelection = { id ->
                        isSelectionMode = true
                        selectedIds = setOf(id)
                        showMoveBar = false
                    },
                    onToggleSelection = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                        if (selectedIds.isEmpty()) isSelectionMode = false
                    },
                    onExitSelection = {
                        isSelectionMode = false
                        selectedIds = emptySet()
                        showMoveBar = false
                    },
                    onClick = { chat ->
                        navViewModel.navigateTo(Route.ChatDetail(chat.id))
                    }
                )
            }
        }
    }
    if (showDeleteDialog) {
        BatchDeleteDialog(
            count = selectedIds.size,
            onConfirm = {
                if (selectedTab == Tab.HOME) {
                    contentList.filter { it.id in selectedIds }.forEach {
                        diaryViewModel.deleteDiary(it)
                    }
                } else {
                    chatList.filter { it.id in selectedIds }.forEach {
                        chatViewModel.deleteChat(it)
                    }
                }
                showDeleteDialog = false
                isSelectionMode = false
                selectedIds = emptySet()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

//----------------中间栏目--------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContentShow(
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onEnterSelection: (Long) -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onExitSelection: () -> Unit = {},
    onClick: (Diary) -> Unit
){
    val viewModel: DiaryViewModel=viewModel()
    val contentList by viewModel.listState.collectAsStateWithLifecycle()

    var selectedTag by rememberSaveable{mutableStateOf<String>("未分类")}
    val tagList = remember(contentList,selectedTag) {
        if (selectedTag.isEmpty()){
        contentList
        }else{contentList.filter { diary -> diary.tag== selectedTag}}}

    Scaffold (
        floatingActionButton={ if (!isSelectionMode) AddButton(selectedTag) },
    ){ innerPadding ->
        Column(modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.diaryColors.background2)
            .padding(innerPadding)
        ) {
            if (isSelectionMode) {
                SelectionModeTopBar(
                    selectedCount = selectedIds.size,
                    onClose = onExitSelection
                )
            } else {
                HeaderColumn(
                    selectedTag = selectedTag,
                    contentList = contentList,
                    onTagSelect = { tag -> selectedTag = tag },
                    onTagsDelete = { deletedTags ->
                        contentList.filter { it.tag in deletedTags }.forEach { diary ->
                            viewModel.updateDiary(diary.copy(tag = "未分类"))
                        }
                    }
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = rememberLazyListState()
            ) {
                items(tagList, key = { it.id }) { diary ->
                    if (isSelectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp)
                                .animateItem()
                                .clickable { onToggleSelection(diary.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DiaryCard(diary = diary, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = diary.id in selectedIds,
                                onCheckedChange = { onToggleSelection(diary.id) }
                            )
                        }
                    } else {
                        SwipeableDiaryCard(diary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(5.dp)
                                .animateItem()
                                .combinedClickable(
                                    onClick = { onClick(diary) },
                                    onLongClick = { onEnterSelection(diary.id) }
                                ),
                            onDelete = {
                                viewModel.deleteDiary(diary)
                            })
                    }
                }
            }
        }
    }
}


//----------------目录头部--------------------
@Composable
fun HeaderColumn(
    selectedTag: String,
    contentList: List<Diary>,
    onTagSelect: (String) -> Unit,
    onTagsDelete: (List<String>) -> Unit
) {
    var isRolled by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Column() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.diaryColors.background2)
                .padding(20.dp,60.dp,20.dp,20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(selectedTag, fontSize = 35.sp, modifier = Modifier.clickable{isRolled = !isRolled })
            Icon(imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "展开",
                modifier = Modifier.padding(top = 5.dp).toggleRotateEffect(isRotated = isRolled))
            Spacer(modifier = Modifier.weight(1f))
            Text("☰", fontSize = 30.sp, modifier=Modifier.padding(8.dp).clickable { showSettings = true })
        }
        AnimatedVisibility(visible = isRolled,
            enter = expandVertically(animationSpec = tween (300)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
        ) {
            TagSetting(
                contentList,
                onTagSelect = { tag ->
                    onTagSelect(tag)
                    isRolled = false
                },
                onTagsDelete = onTagsDelete
            )
        }
    }
    if (showSettings) {
        FunScreen(onDismiss = { showSettings = false })
    }
}



//----------------添加按钮--------------------
@Composable
fun AddButton(tag: String?) {
    val viewModel: DiaryViewModel = viewModel()
    var title by remember { mutableStateOf("标题") }
    var content by remember { mutableStateOf("") }
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(MaterialTheme.diaryColors.primary),
        contentPadding = PaddingValues(0.dp),
        onClick = {
            viewModel.addDiary(title, content,tag,"Diary")
        },
        interactionSource = interactionSource, // 关键：传递 interactionSource
        modifier = Modifier
            .padding(bottom = 20.dp, end = 12.dp)
            .size(64.dp)
            .pressScaleEffect(interactionSource = interactionSource)
    ) {
        Text(
            text = "+",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

//----------------滑动包装--------------------

@Composable
fun SwipeableDiaryCard(
    diary: Diary,
    modifier: Modifier,
    onDelete:(diary: Diary)-> Unit,
    cardContent: @Composable (Diary, Modifier) -> Unit = { d, m -> DiaryCard(diary = d, modifier = m) }
){
    var showDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
    confirmValueChange = {dismissValue ->
        if(dismissValue == SwipeToDismissBoxValue.EndToStart ){
            showDialog = true
            false
        }
        else{false}
    }
    )
    SwipeToDismissBox(state = dismissState,
        modifier = modifier,
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            SwipeBackground(dismissState = dismissState)
        },
        content = { cardContent(diary, modifier) })

    if(showDialog){
        DeleteDialog(diary = diary, onConfirm = {
            onDelete(diary)
        }, onDismiss = {
            showDialog = false
        })
    }
}

@Composable
fun SwipeBackground(dismissState: SwipeToDismissBoxState){
    val color by animateColorAsState(
        when(dismissState.targetValue){
            SwipeToDismissBoxValue.Settled->Color.Transparent
            else -> Color.Gray
        },
        label = "background color"
    )
    Box(modifier = Modifier
        .fillMaxSize()
        .padding( start = 40.dp, top = 6.dp, bottom = 6.dp, end = 10.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(color),
        contentAlignment = Alignment.CenterEnd){
        Icon(imageVector = Icons.Default.Delete,
            contentDescription = "删除",
            tint = Color.White,
            modifier = Modifier.padding(10.dp,0.dp).size(24.dp))
    }
}

//----------------单个笔记--------------------

@Composable
fun DiaryCard(diary: Diary,modifier: Modifier) {
    val formattedDate = remember(diary.date) {
        try {
            val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(diary.date)
            SimpleDateFormat("M月d日", Locale.CHINA).format(dateTime)
        } catch (e: Exception) {
            diary.date // 如果解析失败，显示原始日期
        }
    }
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(25.dp), // 圆角
        border = BorderStroke(1.dp, MaterialTheme.diaryColors.border2), // 边框
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFEFE)), // 背景
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                "笔记:${diary.title}",
                fontSize = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(15.dp,20.dp,0.dp, 0.dp)
            )
            Text(formattedDate,
                fontSize = 15.sp,
                color = Color.Gray,
                modifier = Modifier
                    .padding(20.dp,0.dp)
                )
        }
    }
}

//----------------对话框--------------------
@Composable
fun DeleteDialog(
    diary: Diary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除这篇日记吗？") },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                }
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}


//----------------底部栏--------------------
@Composable
fun BottomNavigate(selectedTab: Tab,
                   onTabSelected:(Tab)-> Unit){

    val background1 = MaterialTheme.diaryColors.background
    val background2 = MaterialTheme.diaryColors.background0

    val border1 = MaterialTheme.diaryColors.border1
    val border2 = MaterialTheme.diaryColors.sweetBorder
    Row (
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.diaryColors.background2)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val homeInteractionSource = remember { MutableInteractionSource() }
        val funInteractionSource = remember { MutableInteractionSource() }
        Icon(imageVector = Icons.Filled.Home, contentDescription = "首页",
            modifier = Modifier.clickable(interactionSource = homeInteractionSource,
                indication = null){onTabSelected(Tab.HOME)
                currentDiaryColors.value = currentDiaryColors.value.copy(
                    background2 = background1,
                    border2 = border1
                )}
                .pressScaleEffect(homeInteractionSource,0.75f).weight(1f))
        Icon(imageVector = Icons.Filled.DateRange, contentDescription = "AI",
            modifier = Modifier.clickable(interactionSource = funInteractionSource,
                indication = null){onTabSelected(Tab.AI)
                currentDiaryColors.value = currentDiaryColors.value.copy(
                    background2 = background2,
                    border2 = border2
                )}
                .pressScaleEffect(funInteractionSource,0.75f).weight(1f))
    }
}



@Preview(showBackground = true)
@Composable
fun Preview() {
    DiarydTheme {
//        HomeScreen()
//        DiaryCard()
//        HeaderColumn()
//        AddButton()
//        BottomNavigate()
    }
}