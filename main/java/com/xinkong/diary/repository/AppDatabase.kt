package com.xinkong.diary.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [Diary::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase: RoomDatabase(){
    abstract fun diaryDao(): DiaryDao
    companion object{
        private var INSTANCE: AppDatabase?=null
        fun getDatabase(context: Context): AppDatabase{
            return INSTANCE?:synchronized(this){
                val instance=Room.databaseBuilder(context,
                    AppDatabase::class.java,
                    "diary_database"
                ).build()
                INSTANCE=instance
                instance
            }
        }
    }
}