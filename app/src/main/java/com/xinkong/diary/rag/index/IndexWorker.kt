package com.xinkong.diary.rag.index

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.Diary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 后台索引 Worker
 * 用于批量重建索引或增量更新
 */
class IndexWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val KEY_ACTION = "action"
        const val ACTION_REBUILD_ALL = "rebuild_all"
        const val ACTION_INDEX_DIARIES = "index_diaries"
        const val ACTION_INDEX_MESSAGES = "index_messages"
        
        /**
         * 启动全量重建索引
         */
        fun scheduleRebuildAll(context: Context) {
            val request = OneTimeWorkRequestBuilder<IndexWorker>()
                .setInputData(workDataOf(KEY_ACTION to ACTION_REBUILD_ALL))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
        
        /**
         * 启动日记索引
         */
        fun scheduleIndexDiaries(context: Context) {
            val request = OneTimeWorkRequestBuilder<IndexWorker>()
                .setInputData(workDataOf(KEY_ACTION to ACTION_INDEX_DIARIES))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val indexManager = IndexManager.getInstance(applicationContext)
            val db = AppDatabase.getDatabase(applicationContext)
            
            when (inputData.getString(KEY_ACTION)) {
                ACTION_REBUILD_ALL -> {
                    // 清空并重建所有索引
                    indexManager.clearAll()
                    indexAllDiaries(indexManager, db)
                }
                ACTION_INDEX_DIARIES -> {
                    indexAllDiaries(indexManager, db)
                }
                ACTION_INDEX_MESSAGES -> {
                    // TODO: 实现消息索引
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
    
    private suspend fun indexAllDiaries(indexManager: IndexManager, db: AppDatabase) {
        val diaryDao = db.diaryDao()
        val indexedIds = db.embeddingDao().getIndexedSourceIds(IndexManager.SOURCE_TYPE_DIARY).toSet()
        
        // 获取所有日记
        val allDiaries: List<Diary> = diaryDao.getAll().first()
        
        for (diary in allDiaries) {
            if (diary.id !in indexedIds) {
                try {
                    indexManager.indexDiary(diary)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
