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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

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
import com.xinkong.diary.ui.screen.chat.AiListScreen
import com.xinkong.diary.ui.theme.DiarydTheme
import com.xinkong.diary.ui.theme.ThemeDefault
import com.xinkong.diary.ui.theme.currentDiaryColors
import com.xinkong.diary.ui.theme.diaryColors
import java.text.SimpleDateFormat
import java.util.Locale
import com.xinkong.diary.ViewModel.TagModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MarkUnreadChatAlt
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.xinkong.diary.ui.screen.alarm.AlarmScreen
import com.xinkong.diary.ui.screen.tag.DEFAULT_TAG_FOLDER
import com.xinkong.diary.ui.screen.tag.UNCLASSIFIED_TAG_NAME


@Composable
fun HomeScreen(){
    val navViewModel: NavigationViewModel = viewModel()
    val selectedTab = navViewModel.selectedTab

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showMoveBar by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val diaryViewModel: DiaryViewModel = viewModel()
    val chatViewModel: com.xinkong.diary.ViewModel.ChatViewModel = viewModel()
    val tagModel: TagModel = viewModel()
    
    val contentList by diaryViewModel.listState.collectAsStateWithLifecycle()
    val chatList by chatViewModel.chatListState.collectAsStateWithLifecycle()
    val diaryTags by tagModel.diaryTags.collectAsStateWithLifecycle()
    val chatTags by tagModel.chatTags.collectAsStateWithLifecycle()
    val tagFolders by tagModel.tagFolders.collectAsStateWithLifecycle()

    var homeSelectedTag by rememberSaveable { mutableStateOf(DEFAULT_TAG_FOLDER to UNCLASSIFIED_TAG_NAME) }

    val homeTagDisplayMap = remember(contentList, diaryTags, tagFolders) {
        tagModel.buildDiaryGroupedTags(contentList)
            .groupedTags
            .values
            .flatten()
            .associateBy { it.folder to it.name }
    }

    LaunchedEffect(homeSelectedTag, homeTagDisplayMap) {
        val homeMatched = homeTagDisplayMap[homeSelectedTag]
        val homeBg = homeMatched?.let { Color(it.bg2Int) } ?: ThemeDefault.background2
        val homeBorder = homeMatched?.let { Color(it.border2Int) } ?: ThemeDefault.border2

        // 使用默认背景色
        val chatBg = ThemeDefault.background0
        val chatBorder = ThemeDefault.sweetBorder

        currentDiaryColors.value = currentDiaryColors.value.copy(
            background2 = homeBg,
            border2 = homeBorder,
            background3 = chatBg,
            border3 = chatBorder
        )
    }

    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isSelectionMode) {
                Column {
                    if (showMoveBar) {
                        MoveToCategoryDialog(
                            isDiary = selectedTab == Tab.HOME,
                            diaryList = contentList,
                            chatList = chatList,
                            onDiaryTagSelected = { tag: Pair<String, String> ->
                                contentList.filter { it.id in selectedIds }.forEach {
                                    diaryViewModel.updateDiary(
                                        it.copy(
                                            tag = tag.second,
                                            tagFolder = tag.first
                                        )
                                    )
                                }
                                showMoveBar = false
                                isSelectionMode = false
                                selectedIds = emptySet()
                            },
                            onChatTagSelected = { tag: String ->
                                chatList.filter { it.id in selectedIds }.forEach {
                                    chatViewModel.updateChat(
                                        it.copy(
                                            tag = tag
                                        )
                                    )
                                }
                                showMoveBar = false
                                isSelectionMode = false
                                selectedIds = emptySet()
                            },
                            onDismiss = { showMoveBar = false }
                        )
                    }
                    SelectionModeBottomBar(
                        isDiary = selectedTab == Tab.HOME,
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
                    selectedTag = homeSelectedTag,
                    onSelectedTagChange = { homeSelectedTag = it },
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
                    onManageTags = {
                        navViewModel.navigateTo(com.xinkong.diary.ViewModel.Route.TagManage("diary"))
                    },
                    onClick = { diary ->
                        navViewModel.navigateTo(Route.DiaryDetail(diary.id))
                    }
                )
                Tab.AI -> AiListScreen(
                    onAiChatClick = { chat ->
                        navViewModel.navigateTo(Route.ChatDetail(chat.id))
                    },
                    onGroupChatClick = { chat ->
                        navViewModel.navigateTo(Route.GroupChatDetail(chat.id))
                    }
                )
                Tab.ALARM -> AlarmScreen(
                    onAddAlarm = { navViewModel.navigateTo(Route.AlarmEdit(0)) },
                    onEditAlarm = { id -> navViewModel.navigateTo(Route.AlarmEdit(id)) }
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
    selectedTag: Pair<String, String> = DEFAULT_TAG_FOLDER to UNCLASSIFIED_TAG_NAME,
    onSelectedTagChange: (Pair<String, String>) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onEnterSelection: (Long) -> Unit = {},
    onToggleSelection: (Long) -> Unit = {},
    onExitSelection: () -> Unit = {},
    onManageTags: () -> Unit = {},
    onClick: (Diary) -> Unit
){
    val viewModel: DiaryViewModel=viewModel()
    val contentList by viewModel.listState.collectAsStateWithLifecycle()

    // ----------- 搜索状态 -------------
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchMode by rememberSaveable { mutableStateOf(false) }
    
    val searchFlow = remember(searchQuery) {
        if (searchQuery.isNotBlank()) viewModel.searchDiaries(searchQuery)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val searchResults by searchFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val displayList = remember(contentList, selectedTag, isSearchMode, searchResults) {
        if (isSearchMode && searchQuery.isNotBlank()) {
            searchResults
        } else {
            contentList.filter { diary ->
                diary.tag == selectedTag.second && diary.tagFolder == selectedTag.first
            }
        }
    }
    // --------------------------------

    val listState = rememberLazyListState()
    var isCollapsed by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (index == 0 && offset < 50) {
                isCollapsed = false
            } else if (index > 0 || offset > 200) {
                isCollapsed = true
            }
        }
    }

    Scaffold (
        floatingActionButton={ if (!isSelectionMode && !isSearchMode) AddButton(selectedTag) },
    ){ innerPadding ->
        Column(modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.diaryColors.background2)
            .padding(innerPadding)
        ) {
            if (isSelectionMode) {
                SelectionModeTopBar(
                    isDiary = true,
                    selectedCount = selectedIds.size,
                    onClose = onExitSelection
                )
            } else if (isSearchMode) {
                SelectionModeTopBar(
                    isDiary = true,
                    selectedCount = displayList.size,
                    onClose = {
                        isSearchMode = false
                        searchQuery = ""
                    },
                    title = "搜索结果: ${displayList.size} 条"
                )
            } else {
                HeaderColumn(
                    selectedTag = selectedTag,
                    contentList = contentList,
                    onTagSelect = { tag ->
                        onSelectedTagChange(tag)
                        isSearchMode = false
                        searchQuery = ""
                    },
                    onManageTags = onManageTags,
                    onTagsDelete = { deletedTags ->
                        deletedTags.forEach { (folder, tagName) ->
                            contentList
                                .filter { it.tag == tagName && it.tagFolder == folder }
                                .forEach { diary ->
                                    viewModel.updateDiary(
                                        diary.copy(
                                            tag = UNCLASSIFIED_TAG_NAME,
                                            tagFolder = folder
                                        )
                                    )
                                }
                            }
                    },
                    isCollapsed = isCollapsed
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState
            ) {
                item {
                    if (!isSelectionMode) {
                        SearchBarItem(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { 
                                searchQuery = it 
                                isSearchMode = true
                            },
                            onSearchExecute = { isSearchMode = true },
                            onClear = { 
                                searchQuery = ""
                            }
                        )
                    }
                }
                
                items(displayList, key = { it.id }) { diary ->
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
    selectedTag: Pair<String, String>,
    contentList: List<Diary>,
    onTagSelect: (Pair<String, String>) -> Unit,
    onTagsDelete: (List<Pair<String, String>>) -> Unit,
    onManageTags: () -> Unit = {},
    isCollapsed: Boolean = false
) {
    var isRolled by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val topPadding by androidx.compose.animation.core.animateDpAsState(targetValue = if (isCollapsed) 10.dp else 40.dp, label = "topPadding")
    val titleFontSize by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isCollapsed) 24f else 35f, label = "titleFontSize")
    val subtitleHeight by androidx.compose.animation.core.animateDpAsState(targetValue = if (isCollapsed) 0.dp else 22.dp, label = "subtitleHeight")
    val subtitleAlpha by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isCollapsed) 0f else 1f, label = "subtitleAlpha")

    Column() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.diaryColors.background2)
                .padding(20.dp, topPadding, 20.dp, 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayTitle = selectedTag.second
            Text(displayTitle, fontSize = titleFontSize.sp, modifier = Modifier.clickable{isRolled = !isRolled })
            Icon(imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "展开",
                modifier = Modifier.padding(top = 5.dp).toggleRotateEffect(isRotated = isRolled))
            Spacer(modifier = Modifier.weight(1f))
            Text("☰", fontSize = 30.sp, modifier=Modifier.padding(8.dp).clickable { showSettings = true })
        }
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(subtitleHeight)
            .graphicsLayer { alpha = subtitleAlpha }
            .padding(start = 20.dp)
        ) {
            Text(
                "当前文件夹：${selectedTag.first}",
                fontSize = 15.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
        Spacer(modifier = Modifier.height(if(isCollapsed) 10.dp else 20.dp))
        AnimatedVisibility(visible = isRolled,
            enter = expandVertically(animationSpec = tween (300)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut()
        ) {
            TagSetting(
                contentList = contentList,
                selectedTag = selectedTag,
                onTagSelect = { tag ->
                    onTagSelect(tag)
                    isRolled = false
                },
                onTagsDelete = onTagsDelete,
                onManageClick = {
                    isRolled = false
                    onManageTags()
                }
            )
        }
    }
    if (showSettings) {
        FunScreen(onDismiss = { showSettings = false })
    }
}



//----------------添加按钮--------------------
@Composable
fun AddButton(tag: Pair<String, String>) {
    val viewModel: DiaryViewModel = viewModel()
    var title by remember { mutableStateOf("标题") }
    var content by remember { mutableStateOf("") }
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(MaterialTheme.diaryColors.primary),
        contentPadding = PaddingValues(0.dp),
        onClick = {
            viewModel.addDiary(title, content, tag.second, tag.first, "Diary")
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
        shape = RoundedCornerShape(20.dp), // 圆角
        border = BorderStroke(1.dp, MaterialTheme.diaryColors.border2), // 边框
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFEFE)), // 背景
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                "笔记:${diary.title}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(15.dp,16.dp,0.dp, 0.dp)
            )
            Text("$formattedDate | ${diary.text}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier
                    .padding(15.dp,0.dp,0.dp, 8.dp)
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
        containerColor = Color.White,
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
fun BottomNavigate(selectedTab: Tab, onTabSelected: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when (selectedTab) {
                    Tab.HOME -> MaterialTheme.diaryColors.background2
                    Tab.AI -> MaterialTheme.diaryColors.background3
                    Tab.ALARM -> MaterialTheme.diaryColors.background1
                }
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly // 均匀分布
    ) {
        val homeInteractionSource = remember { MutableInteractionSource() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = 6.dp, bottom = 4.dp)
                .clickable(
                    interactionSource = homeInteractionSource,
                    indication = null
                ) {
                    onTabSelected(Tab.HOME)
                }
                .pressScaleEffect(homeInteractionSource, 0.75f)
        ) {
            Icon(
                imageVector = Icons.Default.NoteAlt,
                contentDescription = "笔记",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("笔记", fontSize = 10.sp)
        }

        val funInteractionSource = remember { MutableInteractionSource() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = 6.dp, bottom = 4.dp)
                .clickable(
                    interactionSource = funInteractionSource,
                    indication = null
                ) {
                    onTabSelected(Tab.AI)
                }
                .pressScaleEffect(funInteractionSource, 0.75f)
        ) {
            Icon(
                imageVector = Icons.Default.MarkUnreadChatAlt,
                contentDescription = "对话",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("对话", fontSize = 10.sp)
        }

        val alarmInteractionSource = remember { MutableInteractionSource() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = 6.dp, bottom = 4.dp)
                .clickable(
                    interactionSource = alarmInteractionSource,
                    indication = null
                ) {
                    onTabSelected(Tab.ALARM)
                }
                .pressScaleEffect(alarmInteractionSource, 0.75f)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "闹钟",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("闹钟", fontSize = 10.sp)
        }
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