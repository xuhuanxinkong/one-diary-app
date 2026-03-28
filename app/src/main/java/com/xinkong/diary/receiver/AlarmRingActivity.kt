package com.xinkong.diary.receiver

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xinkong.diary.ui.theme.DiarydTheme

class AlarmRingActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
        
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        startAlarmSound()
        
        val alarmName = intent.getStringExtra("ALARM_NAME") ?: "闹钟"
        
        // This activity should show above lock screen on modern Android
        // WindowCompat.setDecorFitsSystemWindows(window, false)
        // window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | FLAG_TURN_SCREEN_ON | FLAG_DISMISS_KEYGUARD)
        setContent {
            DiarydTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "⏰ 响铃了！", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = alarmName, fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(64.dp))
                        Button(onClick = { 
                            stopAlarmSound()
                            finish() 
                        }) {
                            Text("停止闹钟", fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        stopAlarmSound()
        super.onDestroy()
    }

    private fun startAlarmSound() {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)
        ringtone?.play()
    }

    private fun stopAlarmSound() {
        ringtone?.stop()
        ringtone = null
    }
}
