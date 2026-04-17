package com.xinkong.diary.ui.screen.chat.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.huawei.hms.mlsdk.asr.MLAsrConstants
import com.huawei.hms.mlsdk.asr.MLAsrListener
import com.huawei.hms.mlsdk.asr.MLAsrRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceInteractionManager(private val context: Context) {
    private val TAG = "VoiceInteractionManager"

    private var mlAsrRecognizer: MLAsrRecognizer? = null
    private var androidSpeechRecognizer: SpeechRecognizer? = null

    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    // ASR Callbacks
    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onRmsChanged: ((Float) -> Unit)? = null
    var onSpeechEnd: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // TTS Callback
    var onTtsDone: (() -> Unit)? = null

    private val isHuaweiDevice: Boolean
        get() {
            val manufacturer = Build.MANUFACTURER.lowercase()
            return manufacturer.contains("huawei") || manufacturer.contains("honor")
        }

    init {
        initTts()
        initAsr()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.CHINESE) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                ttsReady = false
            }
        }
        
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                // 如果是分块朗读，只在最后一块时触发完成；目前不支持分块的复杂回调，以最后为准
                if (utteranceId?.contains("chunk_") == false) {
                    onTtsDone?.invoke()
                }
            }
            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId)"))
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS Error for utteranceId: $utteranceId")
                if (utteranceId?.contains("chunk_") == false) {
                    onTtsDone?.invoke() // 即使朗读报错也不能卡死主流程
                }
            }
        })
    }

    // 重新初始化TTS（恢复环境）
    fun reInitTts() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        textToSpeech = null
        ttsReady = false
        initTts()
    }

    private fun initAsr() {
        if (isHuaweiDevice) {
            mlAsrRecognizer = MLAsrRecognizer.createAsrRecognizer(context)
            mlAsrRecognizer?.setAsrListener(object : MLAsrListener {
                override fun onStartListening() {}
                override fun onStartingOfSpeech() {}
                override fun onVoiceDataReceived(data: ByteArray?, energy: Float, bundle: Bundle?) {
                    onRmsChanged?.invoke(energy)
                }
                override fun onRecognizingResults(partialResults: Bundle?) {
                    val partial = partialResults?.getString(MLAsrRecognizer.RESULTS_RECOGNIZING)
                    if (!partial.isNullOrEmpty()) onPartialResult?.invoke(partial)
                }
                override fun onResults(results: Bundle?) {
                    val finalResult = results?.getString(MLAsrRecognizer.RESULTS_RECOGNIZED)
                    if (!finalResult.isNullOrEmpty()) onFinalResult?.invoke(finalResult)
                    onSpeechEnd?.invoke()
                }
                override fun onError(error: Int, errorMessage: String?) {
                    val msg = errorMessage ?: "识别错误: $error"
                    onError?.invoke(msg)
                }
                override fun onState(state: Int, params: Bundle?) {}
            })
        } else {
            androidSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            androidSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    onRmsChanged?.invoke(rmsdB)
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    onSpeechEnd?.invoke()
                }
                override fun onError(error: Int) {
                    val msg = "System ASR Error: $error"
                    this@VoiceInteractionManager.onError?.invoke(msg)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) onFinalResult?.invoke(matches[0])
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!partial.isNullOrEmpty()) onPartialResult?.invoke(partial[0])
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening() {
        requestAudioFocus()
        if (isHuaweiDevice) {
            val intent = Intent(MLAsrConstants.ACTION_HMS_ASR_SPEECH).apply {
                putExtra(MLAsrConstants.LANGUAGE, "zh-CN")
                putExtra(MLAsrConstants.FEATURE, MLAsrConstants.FEATURE_ALLINONE)
            }
            mlAsrRecognizer?.startRecognizing(intent)
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            androidSpeechRecognizer?.startListening(intent)
        }
    }

    fun stopListening() {
        if (isHuaweiDevice) {
            mlAsrRecognizer?.destroy() // ML Kit needs to be recreated if destroyed or stopped manually? Wait, we can just let it finish. But to interrupt, we might need a reset. Actually, ML Kit doesn't have a reliable `stopListening()` without destroying/recreating, or it does finish itself... Wait, destroy drops the listener. I'll just destroy & re-create for Huawei if interrupted.
            mlAsrRecognizer = null
            initAsr()
        } else {
            androidSpeechRecognizer?.stopListening()
        }
    }

    fun speak(text: String, utteranceId: String = "TTS_CALL") {
        if (ttsReady && textToSpeech != null && text.isNotBlank()) {
            requestAudioFocus()
            val maxLen = TextToSpeech.getMaxSpeechInputLength().let { if (it > 0) it else 4000 }
            
            var success = false
            if (text.length > maxLen - 10) {
                // 如果文字过长，进行分块，避免触碰引擎的字符上限而报错丢弃
                val chunks = text.chunked(maxLen - 100)
                chunks.forEachIndexed { index, chunk ->
                    val isFirst = index == 0
                    val isLast = index == chunks.lastIndex
                    val currentId = if (isLast) utteranceId else "chunk_$index"
                    val queueMode = if (isFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    val code = textToSpeech?.speak(chunk, queueMode, null, currentId)
                    if (isLast && code == TextToSpeech.SUCCESS) success = true
                }
            } else {
                val code = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (code == TextToSpeech.SUCCESS) success = true
            }
            
            // 如果底层发送给引擎失败，强制拉起空闲状态
            if (!success) {
                Log.e(TAG, "TTS speak() reported error return code.")
                onTtsDone?.invoke()
            }
        } else {
            // 如果TTS还没准备好或者文字为空，直接回调以免卡死
            onTtsDone?.invoke()
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener {}
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null, 
                AudioManager.STREAM_VOICE_CALL, 
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun destroy() {
        stopSpeaking()
        textToSpeech?.shutdown()
        mlAsrRecognizer?.destroy()
        androidSpeechRecognizer?.destroy()
        abandonAudioFocus()
    }
}
