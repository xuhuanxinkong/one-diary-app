package com.xinkong.diary

import android.app.Application
import com.xinkong.diary.rag.RAG
import com.xinkong.diary.repository.ObjectBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.huawei.hms.mlsdk.common.MLApplication

class DiaryApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化华为 ML Kit（设置 API Key）
        initHuaweiMLKit()
        
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
    
    private fun initHuaweiMLKit() {
        // 检测是否为华为/荣耀设备
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            try {
                // 从 agconnect-services.json 自动读取 API Key
                MLApplication.getInstance().setApiKey("DgEDAAxbdb30LqOKRD042G7Y8kDq46bMXJEfKrou+yw0b3dyvRCCbkrUDANEj1rlIqjphnLnt8sAO5TWIf9Y/0aSVZz1KZ/855dvBw==")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
