package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.Diary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.text.SimpleDateFormat
import java.util.Date


class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val diaryDao = db.diaryDao()
    private val _listState = MutableStateFlow(listOf<Diary>())
    val listState: StateFlow<List<Diary>> = _listState.asStateFlow()

    init {
        viewModelScope.launch {
            diaryDao.getAll().collect { diaries ->
                _listState.update { diaries }
            }
        }
    }

    // 添加
    fun addDiary(title: String, content: String, tag: String?, type: String) {
        viewModelScope.launch {
            val diary = Diary(
                title = title,
                content = content,
                date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                tag = tag ?: "未分类",
                type = type
            )
            diaryDao.insert(diary)
        }
    }

    // 删除
    fun deleteDiary(diary: Diary) {
        viewModelScope.launch {
            diaryDao.delete(diary)
        }
    }
    // 更新
    fun updateDiary(diary: Diary) {
        viewModelScope.launch {
            diaryDao.update(diary = diary)
        }
    }

    // 查找
    fun findDiary(id: Long): Flow<Diary> {
        return diaryDao.getByID(id)
    }

    fun findAllDiary(): Flow<List<Diary>> {
        return diaryDao.getAll()
    }

    fun findDiaryByTag(tag: String): Flow<List<Diary>> {
        return diaryDao.getByTag(tag)
    }

    fun findDiaryByType(type: String): Flow<List<Diary>> {
        return diaryDao.getByType(type)
    }

    // 导入导出
    @Serializable
    data class ExportData(
        val version: String,
        val timeStamp: String,
        val data: List<Diary>
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun exportToJson(diaries: List<Diary>, callback: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val exportData = ExportData(
                    version = "1.0",
                    timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()),
                    data = diaries
                )
                val jsonString = json.encodeToString(exportData)
                callback(Result.success(jsonString))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    fun importFromJson(jsonString: String, isDefaultFormat: Boolean, targetTag: String, callback: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            try {
                if (isDefaultFormat) {
                    val exportData = json.decodeFromString<ExportData>(jsonString)
                    var count = 0
                    for (diary in exportData.data) {
                        val newDiary = Diary(
                            title = diary.title,
                            content = diary.content,
                            date = diary.date,
                            tag = if (targetTag.isNotEmpty()) targetTag else (diary.tag ?: "未分类"),
                            type = diary.type
                        )
                        diaryDao.insert(newDiary)
                        count++
                    }
                    callback(Result.success(count))
                } else {
                    // 其他JSON格式，将整个内容格式化放到content
                    val formattedJson = try {
                        val element = json.parseToJsonElement(jsonString)
                        json.encodeToString(JsonElement.serializer(), element)
                    } catch (e: Exception) {
                        jsonString // 如果不是合法JSON，直接作为文本导入
                    }

                    val newDiary = Diary(
                        title = "导入的笔记",
                        content = formattedJson,
                        date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                        tag = if (targetTag.isNotEmpty()) targetTag else "导入"
                    )
                    diaryDao.insert(newDiary)
                    callback(Result.success(1))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }
}


