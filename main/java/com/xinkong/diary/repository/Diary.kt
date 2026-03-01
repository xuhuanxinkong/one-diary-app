package com.xinkong.diary.repository

import android.content.Context
import androidx.room.*


@Entity(tableName="diaries")
data class Diary(
    @PrimaryKey(autoGenerate=true)
    val id:Int=0,
    val title:String="",
    val content:String="",
    val date:String="",
    val tag:String?=null
)







