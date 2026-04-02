package com.xinkong.diary.ui.screen.home


import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.repository.Diary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID


// 全屏详情
@Composable
fun DiaryDetail(
    diary: Diary,
    onClose: () -> Unit,
    onSave: (Diary) -> Unit,
) {
    val viewModel: DiaryViewModel = viewModel()
    DiaryEditorWebView(diary = diary,
        onClose = onClose,
        onSave = onSave)
}

@Composable
fun DiaryEditorWebView(
    diary: Diary,  // 非空参数
    onClose: () -> Unit,
    onSave: (Diary) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 注册获取图片的 Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // 在 IO 线程中将图片复制到私有目录，实现高性能本地缓存
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val imagesDir = File(context.filesDir, "diary_images")
                    if (!imagesDir.exists()) {
                        imagesDir.mkdirs()
                    }
                    val fileName = "img_${UUID.randomUUID()}.jpg"
                    val targetFile = File(imagesDir, fileName)

                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // 切换回主线程注入图片，由于目标文件就在本地，网页可以直接加载高性能的本地路径
                    withContext(Dispatchers.Main) {
                        val localUrl = "file://${targetFile.absolutePath}"
                        webViewRef?.evaluateJavascript(
                            "javascript:window.insertImage('$localUrl');", null
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true        // 允许加载 file:// 协议文件
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun pickImage() {
                            // 调用 Android Launcher 必须在主线程执行
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                imagePickerLauncher.launch("image/*")
                            }
                        }

                        @JavascriptInterface
                        fun deleteImage(url: String) {
                            try {
                                if (url.startsWith("file://")) {
                                    val path = url.removePrefix("file://")
                                    val file = File(path)
                                    // 仅允许删除 diary_images 目录下的文件，防止误删或越权
                                    if (file.exists() && file.absolutePath.contains("diary_images")) {
                                        file.delete()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        @JavascriptInterface
                        fun onSaved(title: String, content: String,text: String) {
                            val newDiary = diary.copy(
                                title = title,
                                content = content,
                                text = text
                            )
                            onSave(newDiary)
                        }

                        @JavascriptInterface
                        fun getContent(): String {
                            // 优先使用HTML内容，如果为空则用纯文本包装成简单HTML
                            return if (diary.content.isNotBlank()) {
                                diary.content
                            } else if (diary.text.isNotBlank()) {
                                // 将纯文本转换为基础HTML（按换行分段）
                                diary.text.split("\n").joinToString("") { "<p>$it</p>" }
                            } else {
                                ""
                            }
                        }

                        @JavascriptInterface
                        fun getTitle(): String {
                            return diary.title
                        }

                        @JavascriptInterface
                        fun onClosed() {
                            // 必须切换到主线程执行 UI/导航操作
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                onClose()
                            }
                        }
                    },
                    "Android"
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // 可以在这里注入初始数据
                    }
                }
                
                webViewRef = this
                loadUrl("file:///android_asset/editor/editor.html")
            }
        },
        update = {
            webViewRef = it
        },
        modifier = Modifier.fillMaxSize()
    )
}















