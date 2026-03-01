package com.xinkong.diary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xinkong.diary.Data.Routes
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.ui.screen.home.DiaryDetail
import com.xinkong.diary.ui.screen.home.HomeScreen
import com.xinkong.diary.ui.theme.DiarydTheme

class MainActivity : ComponentActivity() {
    val viewModel: DiaryViewModel by viewModels ()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiarydTheme {
            DiaryApp()

            }
        }
    }
}


@Composable
fun DiaryApp(){
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ){
        composable(Routes.HOME){
            HomeScreen(onNav ={})
        }
//        composable(route = Routes.DETAIL,
//            arguments = listOf(navArgument("diaryId"){type = NavType.IntType
//            defaultValue = -1})){backStackEntry ->
//            val diaryId = backStackEntry.arguments?.getInt("diaryId")?:-1
//            val viewModel: DiaryViewModel=viewModel()
//            DiaryDetail (diaryId = diaryId,
//                onClose = {navController.popBackStack()},
//                onSave = {newDiary ->
//                viewModel.updateDiary(newDiary)}
//            )
//        }
    }
}
