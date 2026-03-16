package com.xinkong.diary.ui.screen.home


import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.lifecycle.viewmodel.compose.viewModel
import com.xinkong.diary.ViewModel.DiaryViewModel
import com.xinkong.diary.repository.Diary


// 全屏详情
@Composable
fun DiaryDetail(
    diary: Diary,
    onClose: () -> Unit,
    onSave: (Diary) -> Unit,
) {
    val viewModel: DiaryViewModel = viewModel()
    DiaryEditorWebView(
        diary = diary,
        onClose = onClose,
        onSave = onSave
    )
}

@Composable
fun DiaryEditorWebView(
    diary: Diary,  // 非空参数
    onClose: () -> Unit,
    onSave: (Diary) -> Unit
) {

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                addJavascriptInterface(
                    object {
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
                            return diary.content
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

                loadUrl("file:///android_asset/editor/editor.html")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}















