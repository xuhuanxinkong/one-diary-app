package com.xinkong.diary.repository

import android.content.Context
import androidx.compose.runtime.saveable.Saver
import androidx.room.*
import kotlinx.serialization.Serializable


@Serializable
@Entity(tableName="diaries")
data class Diary(
    @PrimaryKey(autoGenerate=true)
    val id: Long=0,
    val title:String="",
    val text: String="",
    val content:String="",
    val date:String="",
    val tag:String="未分类",
    val tagFolder: String = "我的笔记",
    val type: String="Diary"
)

// 定义 Diary 的 Saver
val DiarySaver = Saver<Diary, Map<String, Any>>(
    save = { diary ->
        mapOf(
            "id" to diary.id,
            "title" to diary.title,
            "content" to diary.content,
            "date" to diary.date,
            "tag" to diary.tag,
            "tagFolder" to diary.tagFolder,
            "type" to diary.type
        )
    },
    restore = { map ->
        Diary(
            id = map["id"] as Long,
            title = map["title"] as String,
            content = map["content"] as String,
            date = map["date"] as String,
            tag = map["tag"] as String,
            tagFolder = map["tagFolder"] as? String ?: "我的笔记",
            type = map["type"] as String
        )
    }
)








