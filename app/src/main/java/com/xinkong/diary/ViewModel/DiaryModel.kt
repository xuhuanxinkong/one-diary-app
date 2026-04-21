package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.ChatTag
import com.xinkong.diary.repository.Diary
import com.xinkong.diary.repository.DiaryTag
import com.xinkong.diary.ui.theme.ColorPalette
import com.xinkong.diary.ui.theme.ThemeDefault
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
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date


class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val diaryDao = db.diaryDao()
    private val tagDao = db.tagDao()
    private val _listState = MutableStateFlow(listOf<Diary>())
    val listState: StateFlow<List<Diary>> = _listState.asStateFlow()

    private val _currentFolder = MutableStateFlow("我的笔记")
    val currentFolder: StateFlow<String> = _currentFolder.asStateFlow()

    fun updateCurrentFolder(folderName: String) {
        _currentFolder.value = folderName
    }

    init {
        viewModelScope.launch {
            diaryDao.getAll().collect { diaries ->
                _listState.update { diaries }
            }
        }
    }

    // 添加
    fun addDiary(title: String, content: String, tag: String, tagFolder: String, type: String) {
        viewModelScope.launch {
            val diary = Diary(
                title = title,
                content = content,
                date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                tag = tag,
                tagFolder = tagFolder,
                type = type
            )
            val newId = diaryDao.insert(diary)
            
            // 异步创建 RAG 索引
            try {
                val insertedDiary = diaryDao.getDiaryById(newId) ?: diary.copy(id = newId)
                com.xinkong.diary.rag.RAG.indexDiary(getApplication(), insertedDiary)
            } catch (e: Exception) {
                // 索引失败不影响主流程
                e.printStackTrace()
            }
        }
    }

    // 删除
    fun deleteDiary(diary: Diary) {
        viewModelScope.launch {
            diaryDao.delete(diary)
            
            // 删除 RAG 索引
            try {
                com.xinkong.diary.rag.RAG.deleteIndex(
                    getApplication(),
                    com.xinkong.diary.rag.index.IndexManager.SOURCE_TYPE_DIARY,
                    diary.id
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 复制日记
    fun copyDiary(diary: Diary, newTag: String, newTagFolder: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val cloned = diary.copy(id = 0, tag = newTag, tagFolder = newTagFolder)
            val newId = diaryDao.insert(cloned)
            
            // 异步创建 RAG 索引
            try {
                val insertedDiary = diaryDao.getDiaryById(newId) ?: cloned.copy(id = newId)
                com.xinkong.diary.rag.RAG.indexDiary(getApplication(), insertedDiary)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            onComplete?.invoke()
        }
    }
    // 更新
    fun updateDiary(diary: Diary) {
        viewModelScope.launch {
            diaryDao.update(diary = diary)
            
            // 更新 RAG 索引
            try {
                com.xinkong.diary.rag.RAG.indexDiary(getApplication(), diary)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun searchDiaries(keyword: String): Flow<List<Diary>> {
        return diaryDao.searchAllByKeyword(keyword)
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

    fun exportToJson(diaries: List<Diary>, uri: android.net.Uri, context: android.content.Context, callback: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            try {
                val exportData = ExportData(
                    version = "1.0",
                    timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()),
                    data = diaries
                )
                val jsonString = json.encodeToString(exportData)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    java.io.OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonString)
                    }
                }
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    fun importFromJson(jsonString: String, isDefaultFormat: Boolean, targetTag: String, targetFolder: String, callback: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            try {
                if (isDefaultFormat) {
                    val exportData = json.decodeFromString<ExportData>(jsonString)
                    var count = 0
                    val ensuredDiaryTags = mutableSetOf<Pair<String, String>>()
                    val ensuredChatTags = mutableSetOf<Pair<String, String>>()
                    for (diary in exportData.data) {
                        val newTag = targetTag.ifBlank { diary.tag }
                        val newFolder = targetFolder.ifBlank { diary.tagFolder }
                        val newDiary = Diary(
                            title = diary.title,
                            text = diary.text,
                            content = diary.content,
                            date = diary.date,
                            tag = newTag,
                            tagFolder = newFolder,
                            type = diary.type
                        )
                        val newId = diaryDao.insert(newDiary)

                        try {
                            val insertedDiary = diaryDao.getDiaryById(newId) ?: newDiary.copy(id = newId)
                            com.xinkong.diary.rag.RAG.indexDiary(getApplication(), insertedDiary)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        ensureTagExists(
                            type = diary.type,
                            tag = newTag,
                            folder = newFolder,
                            ensuredDiaryTags = ensuredDiaryTags,
                            ensuredChatTags = ensuredChatTags
                        )

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

                    val newTag = targetTag.ifBlank { "导入" }
                    val newFolder = targetFolder.ifBlank { "我的笔记" }

                    val newDiary = Diary(
                        title = "导入的笔记",
                        text = formattedJson,
                        content = formattedJson,
                        date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                        tag = newTag,
                        tagFolder = newFolder
                    )
                    val newId = diaryDao.insert(newDiary)
                    try {
                        val insertedDiary = diaryDao.getDiaryById(newId) ?: newDiary.copy(id = newId)
                        com.xinkong.diary.rag.RAG.indexDiary(getApplication(), insertedDiary)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    tagDao.insertDiaryTagIgnore(buildDiaryTag(newTag, newFolder))
                    callback(Result.success(1))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    private suspend fun ensureTagExists(
        type: String,
        tag: String,
        folder: String,
        ensuredDiaryTags: MutableSet<Pair<String, String>>,
        ensuredChatTags: MutableSet<Pair<String, String>>
    ) {
        val key = tag to folder
        if (type == "chat") {
            if (!ensuredChatTags.add(key)) return
            tagDao.insertChatTagIgnore(buildChatTag(tag, folder))
        } else {
            if (!ensuredDiaryTags.add(key)) return
            tagDao.insertDiaryTagIgnore(buildDiaryTag(tag, folder))
        }
    }

    private fun buildDiaryTag(name: String, folder: String): DiaryTag {
        return DiaryTag(
            name = name,
            colorInt = defaultTagColor(name),
            bg2Int = ThemeDefault.background2.toArgb(),
            border2Int = ThemeDefault.border2.toArgb(),
            folder = folder
        )
    }

    private fun buildChatTag(name: String, folder: String): ChatTag {
        return ChatTag(
            name = name,
            colorInt = defaultTagColor(name),
            bg2Int = ThemeDefault.background2.toArgb(),
            border2Int = ThemeDefault.border2.toArgb(),
            folder = folder
        )
    }

    private fun defaultTagColor(tagName: String): Int {
        return ColorPalette[abs(tagName.hashCode()) % ColorPalette.size].toArgb()
    }
}


