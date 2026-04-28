package com.xinkong.diary.repository

import android.content.Context
import io.objectbox.BoxStore

/**
 * ObjectBox 数据库管理器
 * 用于向量存储和相似度搜索
 */
object ObjectBox {
    lateinit var store: BoxStore
        private set
    
    /**
     * 初始化 ObjectBox，应在 Application.onCreate() 中调用
     */
    fun init(context: Context) {
        if (::store.isInitialized) return
        
        store = MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .name("vectordb")
            .build()
    }
    
    /**
     * 获取 VectorEntity 的 Box
     */
    fun vectorBox() = store.boxFor(VectorEntity::class.java)
    
    /**
     * 关闭数据库（通常不需要手动调用）
     */
    fun close() {
        if (::store.isInitialized && !store.isClosed) {
            store.close()
        }
    }
}
