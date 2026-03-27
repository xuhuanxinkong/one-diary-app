package com.xinkong.diary.ui.screen.clock

import android.os.Build

import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xinkong.diary.R
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter


@Preview
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ClockWithTime(){
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true){
            delay(1000)
            currentTime = LocalTime.now()
        }
    }
    val timeString = currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    Box(modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center){
        Image(painter = painterResource(id = R.drawable.clock),
            contentDescription = "电子钟",
            modifier = Modifier.size(200.dp))
        Text(
            text = timeString,
            fontSize = 32.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = 2.dp, y= 5.dp)
        )

    }
}

