package com.xinkong.diary.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.xinkong.diary.Http.AiHttp
import com.xinkong.diary.Data.AiState
import com.xinkong.diary.repository.AppDatabase
import com.xinkong.diary.repository.Diary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date


class DiaryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val diaryDao = db.diaryDao()
    private val _listState= MutableStateFlow(listOf<Diary>())
    val listState: StateFlow<List<Diary>> =_listState.asStateFlow()

    init {
        viewModelScope.launch {
            diaryDao.getAll().collect {
                diaries ->
                _listState.update { diaries }
                }
            }
        }

    // 添加
    fun addDiary(title: String, content: String,tag: String?) {
        viewModelScope.launch {
            val diary = Diary(
                title = title,
                content = content,
                date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date()),
                tag = tag?:"未分类"
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

    //更新
    fun updateDiary(diary: Diary){
        viewModelScope.launch {
        diaryDao.update(diary = diary)
    }
        }

    //查找
    fun findDiary(id: Int): Flow<Diary> {
        return diaryDao.getByID(id)
    }
    fun findAllDiary(): Flow<List<Diary>>{
        return diaryDao.getAll()
    }
    fun findDiaryByTag(tag: String): Flow<List<Diary>>{
        return diaryDao.getByTag(tag)
    }



    //AI总结
    val aiHttp = AiHttp()
    private val ai_State = MutableStateFlow<AiState>(AiState.Idle)
    val aiState : StateFlow<AiState> =ai_State.asStateFlow()

    fun sumDiary(content: String){
        viewModelScope.launch {
            ai_State.value = AiState.Loading
            val result = aiHttp.chatWithAi("请用中文总结以下日记内容：\n${content}")
            ai_State.value=result.fold(
                onSuccess =  {summary->
                    println("成功:${summary}")
                     AiState.Success(result = summary)},
                onFailure = {error ->
                    println("失败:${error.message}")
                    AiState.Error(message = error.message?:"AI总结失败")
                }
            )
            }
        }
    
    fun updateAiConfig(config: com.xinkong.diary.Http.AiConfig) {
        aiHttp.updateConfig(config)
    }

    fun resetAiState(){
        ai_State.value= AiState.Idle
    }




    //导入导出
    data class ExportData(
        val version: String,
        val timeStamp: String,
        val data: List<Diary>
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    
    fun exportToJson(diaries: List<Diary>, callback: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val exportData = ExportData(
                    version = "1.0",
                    timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date()),
                    data = diaries
                )
                val jsonString = gson.toJson(exportData)
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
                    val exportData = gson.fromJson(jsonString, ExportData::class.java)
                    if (exportData.data != null) {
                        var count = 0
                        for (diary in exportData.data) {
                            val newDiary = Diary(
                                title = diary.title,
                                content = diary.content,
                                date = diary.date,
                                tag = if (targetTag.isNotEmpty()) targetTag else (diary.tag ?: "未分类")
                            )
                            diaryDao.insert(newDiary)
                            count++
                        }
                        callback(Result.success(count))
                    } else {
                        callback(Result.failure(Exception("无效的默认JSON格式")))
                    }
                } else {
                    // 其他JSON格式，将整个内容格式化放到content
                    val formattedJson = try {
                        val jsonElement = com.google.gson.JsonParser.parseString(jsonString)
                        gson.toJson(jsonElement)
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


