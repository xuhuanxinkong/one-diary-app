package com.xinkong.diary

import android.app.Application
import com.xinkong.diary.rag.RAG
import com.xinkong.diary.repository.ObjectBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DiaryApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 ObjectBox 向量数据库
        ObjectBox.init(this)
        
        // 后台初始化 RAG 模块（预加载 embedding 模型）
        applicationScope.launch {
            try {
                RAG.initialize(this@DiaryApplication)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
