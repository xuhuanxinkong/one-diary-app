package com.xinkong.diary.ui.screen.home


import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
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
import com.xinkong.diary.repository.Diary
import com.xinkong.diary.ui.animation.pressScaleEffect
import com.xinkong.diary.ui.animation.toggleRotateEffect
import com.xinkong.diary.ui.theme.DiarydTheme
import com.xinkong.diary.ui.theme.diaryColors

//----------------总的调用处--------------------
@Composable
fun HomeScreen(onNav :(Int)-> Unit) {
    val  viewModel: DiaryViewModel = viewModel()
    var selected by remember { mutableStateOf(1) }

    var selectedDiary by rememberSaveable{ mutableStateOf<Diary?>(null) }
    var showDiary by remember{ mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {BottomNavigate(
            selectedItem =selected,
            onItem = {newIndex -> selected = newIndex}
        )}
    ) { innerPadding ->
        ViewPages(bottom = innerPadding.calculateBottomPadding(),
            selected = selected,
            onClick ={diary ->
                selectedDiary = diary
                showDiary = true})
    }


        AnimatedVisibility(
            visible = showDiary,
            enter = expandIn(expandFrom = Alignment.TopEnd),
            exit = fadeOut()
        ) {
            selectedDiary?.let {diary ->
                DiaryDetail(diary = diary,
                    onClose = {showDiary = false},
                    onSave = {updateDiary ->
                        viewModel.updateDiary(updateDiary)
                        showDiary = false})
            }
        }
}


//----------------中间栏目--------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContentShow(modifier: Modifier= Modifier,onClick: (Diary) -> Unit){
    val viewModel: DiaryViewModel=viewModel()
    val contentList by viewModel.listState.collectAsStateWithLifecycle()

    var selectedTag by rememberSaveable{mutableStateOf<String>("未分类")}
    val tagList = remember(contentList,selectedTag) {
        if (selectedTag.isEmpty()){
        contentList
        }else{contentList.filter { diary -> diary.tag== selectedTag}}}

    Scaffold (
        floatingActionButton={AddButton(selectedTag)},
    ){ innerPadding ->
        Column(modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.diaryColors.background)
            .padding(innerPadding)
        ) {
            HeaderColumn(
                selectedTag = selectedTag,
                contentList = contentList,
                onTagSelect = { tag -> selectedTag = tag },
                onTagsDelete = { deletedTags ->
                    // Loop through diaries and update those with deleted tags
                    contentList.filter { it.tag in deletedTags }.forEach { diary ->
                        viewModel.updateDiary(diary.copy(tag = "未分类"))
                    }
                }
            )
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = rememberLazyListState()
            ) {
                items(tagList, key = { it.id }) { diary ->
                    SwipeableDiaryCard(diary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp)
                            .animateItem()
                            .clickable {onClick(diary)},
                        onDelete = {
                            viewModel.deleteDiary(diary)
                        })
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
    var showAiSettings by remember { mutableStateOf(false) }

    Column() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.diaryColors.background)
                .padding(20.dp,60.dp,20.dp,20.dp)
        ) {
            Text(selectedTag, fontSize = 35.sp, modifier = Modifier.clickable{isRolled = !isRolled })
            Text(" ▾", fontSize = 30.sp,
                modifier = Modifier.padding(top = 5.dp).toggleRotateEffect(isRotated = isRolled))
            Spacer(modifier = Modifier.weight(1f))
            Text("☰", fontSize = 30.sp, modifier=Modifier.padding(8.dp).clickable { showAiSettings = true })
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
    if (showAiSettings) {
        SettingScreen(onDismiss = { showAiSettings = false })
    }
}



//----------------添加按钮--------------------
@Composable
fun AddButton(tag: String?) {
    val viewModel: DiaryViewModel = viewModel()
    var title by remember { mutableStateOf("标题") }
    var content by remember { mutableStateOf("内容") }
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(MaterialTheme.diaryColors.primary),
        contentPadding = PaddingValues(0.dp),
        onClick = {
            viewModel.addDiary(title, content,tag)
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
        content = { DiaryCard(diary = diary,modifier =modifier) })

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
            modifier = Modifier.size(24.dp))
    }
}

//----------------单个笔记--------------------
@Composable
fun DiaryCard(diary: Diary,modifier: Modifier) {
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(40.dp), // 圆角
        border = BorderStroke(2.dp, MaterialTheme.diaryColors.border2), // 边框
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFEFE)), // 背景
        modifier = modifier
    ) {
        Text(
            "笔记:${diary.title}",
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        )
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
//----------------界面切换动画包装--------------------
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ViewPages(bottom: Dp, selected: Int,onClick:(Diary)-> Unit) {
    AnimatedContent(
        targetState = selected,
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottom),
        transitionSpec = {
            if (targetState > initialState) {
                // 下一页动画
                (
                        slideInHorizontally(
                            animationSpec = tween(300),
                            initialOffsetX = { fullWidth -> fullWidth }
                        ) + fadeIn(animationSpec = tween(300))
                        ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(300),
                            targetOffsetX = { fullWidth -> -fullWidth }
                        ) + fadeOut(animationSpec = tween(300))
                    )
            } else {
                // 上一页动画
                (
                        slideInHorizontally(
                            animationSpec = tween(300),
                            initialOffsetX = { fullWidth -> -fullWidth }
                        ) + fadeIn(animationSpec = tween(300))
                        ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(300),
                            targetOffsetX = { fullWidth -> fullWidth }
                        ) + fadeOut(animationSpec = tween(300))
                    )
            }
        },
        label = "screen_transition"
    ) { page ->
        when (page) {
            1 -> ContentShow(
                modifier = Modifier.fillMaxSize(),
                onClick = onClick
            )
            2 -> FunScreen()
            else -> Box {} // 默认空页面
        }
    }
}


//----------------底部栏--------------------
@Composable
fun BottomNavigate(selectedItem: Int,
                   onItem:(Int)-> Unit){
    Row (
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.diaryColors.background)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val homeInteractionSource = remember { MutableInteractionSource() }
        val funInteractionSource = remember { MutableInteractionSource() }
        Icon(imageVector = Icons.Filled.Home, contentDescription = "首页",
            modifier = Modifier.clickable(interactionSource = homeInteractionSource,
                indication = null){onItem(1)}
                .pressScaleEffect(homeInteractionSource,0.75f).weight(1f))
        Icon(imageVector = Icons.Filled.DateRange, contentDescription = "AI",
            modifier = Modifier.clickable(interactionSource = funInteractionSource,
                indication = null){onItem(2)}
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