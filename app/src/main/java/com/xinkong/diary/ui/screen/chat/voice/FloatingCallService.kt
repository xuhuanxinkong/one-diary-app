package com.xinkong.diary.ui.screen.chat.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.view.isVisible
import coil.load
import coil.transform.CircleCropTransformation
import com.xinkong.diary.MainActivity
import com.xinkong.diary.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

class FloatingCallService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var breathView: View? = null

    companion object {
        private const val CHANNEL_ID = "voice_call_channel"
        private const val NOTIFICATION_ID = 101

        var isServiceRunning = false
        var currentChatId: Long = -1L
        var currentAiId: Long = -1L
        var currentIsGroup: Boolean = false

        // Legacy compatibility: floating UI is now Flow-driven, so these methods are no-op.
        fun updateBubble(userText: String, aiText: String) {
            // no-op
        }

        // Common legacy typo compatibility.
        fun updateBuble(userText: String, aiText: String) {
            updateBubble(userText, aiText)
        }

        fun start(context: Context, chatId: Long, aiId: Long, isGroup: Boolean) {
            currentChatId = chatId
            currentAiId = aiId
            currentIsGroup = isGroup

            val intent = Intent(context, FloatingCallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingCallService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showFloatingWindow()
        observeCallManagerState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "语音通话服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action = "RESUME_VOICE_CALL" // Custom action to identify resume action in MainActivity
            putExtra("chatId", CallManager.chatId)
            putExtra("aiId", CallManager.aiConfig?.id ?: -1L)
            putExtra("isGroup", CallManager.isGroup)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音通话中")
            .setContentText("点击返回通话")
            .setSmallIcon(R.mipmap.ic_launcher) 
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        val rootView = FrameLayout(this)
        
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 8, 8, 8)
        }

        // Top Row: Avatar + Options Menu
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Avatar Container
        val avatarContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
        }

        breathView = View(this).apply {
            val breathBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#804CAF50"))
            }
            background = breathBg
            layoutParams = FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER)
            scaleX = 0f
            scaleY = 0f
        }
        
        val avatarImg = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER)
            clipToOutline = true
        }

        val avatarNameView = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            val nameBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#335C6BC0"))
            }
            background = nameBg
            isVisible = false
        }

        val currentAiConfig = CallManager.currentAiConfig.value
        val avatarUri = currentAiConfig?.avatarUri?.trim().orEmpty()
        if (avatarUri.isNotEmpty()) {
            avatarImg.isVisible = true
            avatarNameView.isVisible = false
            avatarImg.load(avatarUri) {
                transformations(CircleCropTransformation())
                error(R.mipmap.ic_launcher)
                fallback(R.mipmap.ic_launcher)
            }
        } else {
            avatarImg.isVisible = false
            avatarNameView.isVisible = true
            val displayName = currentAiConfig?.name?.trim().orEmpty()
            avatarNameView.text = if (displayName.isNotEmpty()) displayName.take(1) else "AI"
        }
        
        avatarContainer.addView(breathView)
        avatarContainer.addView(avatarImg)
        avatarContainer.addView(avatarNameView)
        
        topRow.addView(avatarContainer)

        // Options Layout (Horizontal)
        val optionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isVisible = false
            setPadding(dp(8), dp(4), dp(12), dp(4))
            val optBg = GradientDrawable().apply {
                setColor(Color.parseColor("#CC000000"))
                cornerRadius = dp(25).toFloat()
            }
            background = optBg
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8)
            }
        }

        fun createOptBtn(iconText: String, label: String): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(4), dp(12), dp(4))
                
                val icon = TextView(this@FloatingCallService).apply {
                    text = iconText
                    textSize = 18f
                    gravity = Gravity.CENTER
                }
                val txt = TextView(this@FloatingCallService).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    gravity = Gravity.CENTER
                }
                addView(icon)
                addView(txt)
            }
        }
        
        val voiceBtn = createOptBtn("🎤", "语音")
        val textBtn = createOptBtn("⌨️", "打字")
        val screenBtn = createOptBtn("📷", "截图")
        val returnBtn = createOptBtn("🏠", "返回")

        optionsLayout.addView(voiceBtn)
        optionsLayout.addView(textBtn)
        optionsLayout.addView(screenBtn)
        optionsLayout.addView(returnBtn)
        
        topRow.addView(optionsLayout)
        mainContainer.addView(topRow)

        // Middle Layer: Text Input Area
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isVisible = false
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val inBg = GradientDrawable().apply {
                setColor(Color.parseColor("#EE000000"))
                cornerRadius = dp(16).toFloat()
            }
            background = inBg
            layoutParams = LinearLayout.LayoutParams(
                dp(260),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }
        
        val editText = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            hint = "发消息..."
            textSize = 14f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val sendBtn = TextView(this).apply {
            text = "发送"
            setTextColor(Color.parseColor("#4CAF50"))
            textSize = 14f
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        inputLayout.addView(editText)
        inputLayout.addView(sendBtn)
        mainContainer.addView(inputLayout)

        // Bottom Layer: Conversation Texts
        val conversationLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isVisible = false
            setPadding(dp(12), dp(8), dp(12), dp(8))
            val convBg = GradientDrawable().apply {
                setColor(Color.parseColor("#CC000000"))
                cornerRadius = dp(16).toFloat()
            }
            background = convBg
            layoutParams = LinearLayout.LayoutParams(
                dp(260),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }

        val userTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#A5D6A7")) 
            textSize = 12f
            isVisible = false
            setPadding(0, 0, 0, dp(4))
        }

        val aiTextView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            isVisible = false
        }
        
        conversationLayout.addView(userTextView)
        conversationLayout.addView(aiTextView)
        mainContainer.addView(conversationLayout)
        
        // Add container to root
        rootView.addView(mainContainer)

        // Interactions
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        avatarContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams!!.x
                    initialY = layoutParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    // 防止微小的抖动被判定为移动
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        moved = true
                        layoutParams!!.x = initialX + dx.toInt()
                        layoutParams!!.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(floatView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        optionsLayout.isVisible = !optionsLayout.isVisible
                        if (!optionsLayout.isVisible && inputLayout.isVisible) {
                            inputLayout.isVisible = false
                            layoutParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        }
                        windowManager.updateViewLayout(floatView, layoutParams)
                    }
                    true
                }
                else -> false
            }
        }
        
        voiceBtn.setOnClickListener {
            CallManager.togglePause()
        }
        
        textBtn.setOnClickListener {
            inputLayout.isVisible = !inputLayout.isVisible
            if (inputLayout.isVisible) {
                layoutParams?.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                editText.requestFocus()
            } else {
                layoutParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            }
            windowManager.updateViewLayout(floatView, layoutParams)
        }
        
        screenBtn.setOnClickListener {
            android.widget.Toast.makeText(this, "正在返回并准备截图...", android.widget.Toast.LENGTH_SHORT).show()
            // 发送带截图动作的Intent，交由MainActivity处理真实截图
            val intent = Intent(this@FloatingCallService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                action = "RESUME_VOICE_CALL_AND_SCREENSHOT"
                putExtra("chatId", currentChatId)
                putExtra("aiId", currentAiId)
                putExtra("isGroup", currentIsGroup)
            }
            startActivity(intent)
        }

        returnBtn.setOnClickListener {
            val intent = Intent(this@FloatingCallService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                action = "RESUME_VOICE_CALL"
                putExtra("chatId", currentChatId)
                putExtra("aiId", currentAiId)
                putExtra("isGroup", currentIsGroup)
            }
            startActivity(intent)
        }

        sendBtn.setOnClickListener {
            val txt = editText.text.toString().trim()
            if (txt.isNotEmpty()) {
                CallManager.sendManualText(txt)
                editText.setText("")
                inputLayout.isVisible = false
                
                layoutParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                windowManager.updateViewLayout(floatView, layoutParams)
            }
        }
        
        serviceScope.launch {
            CallManager.isPaused.collectLatest { paused ->
                val iconTxt = voiceBtn.getChildAt(0) as TextView
                iconTxt.text = if (paused) "⏸️" else "🎤"
                if (paused) {
                    breathView?.scaleX = 0f
                    breathView?.scaleY = 0f
                }
            }
        }

        serviceScope.launch {
            CallManager.userText.collectLatest { text ->
                if (text.isNotEmpty()) {
                    conversationLayout.isVisible = true
                    userTextView.isVisible = true
                    userTextView.text = "我: $text"
                } else {
                    userTextView.isVisible = false
                    if (!aiTextView.isVisible) conversationLayout.isVisible = false
                }
            }
        }

        serviceScope.launch {
            CallManager.aiText.collectLatest { text ->
                if (text.isNotEmpty()) {
                    conversationLayout.isVisible = true
                    aiTextView.isVisible = true
                    aiTextView.text = "AI: $text"
                } else {
                    aiTextView.isVisible = false
                    if (!userTextView.isVisible) conversationLayout.isVisible = false
                }
            }
        }

        serviceScope.launch {
            CallManager.rmsValue.collectLatest { rms ->
                if (!CallManager.isPaused.value && CallManager.callState.value == CallState.Listening) {
                    val scale = 0.5f + (max(0f, rms) / 20f)
                    val targetScale = scale.coerceIn(0.5f, 1.2f)
                    breathView?.animate()?.scaleX(targetScale)?.scaleY(targetScale)?.setDuration(100)?.start()
                } else if (CallManager.callState.value == CallState.Speaking) {
                    val scale = 0.8f + (max(0f, rms) / 10f)
                    val targetScale = scale.coerceIn(0.7f, 1.3f)
                    breathView?.animate()?.scaleX(targetScale)?.scaleY(targetScale)?.setDuration(100)?.start()
                } else {
                    breathView?.scaleX = 0f
                    breathView?.scaleY = 0f
                }
            }
        }
        
        // Removed bottom drag zone

        
        floatView = rootView
        windowManager.addView(floatView, layoutParams)
    }

    private fun observeCallManagerState() {
        // Observer hook implemented in flow collect blocks inside showFloatingWindow.
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        if (floatView != null) {
            windowManager.removeView(floatView)
            floatView = null
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
