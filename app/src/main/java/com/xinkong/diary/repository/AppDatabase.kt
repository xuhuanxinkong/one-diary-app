package com.xinkong.diary.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [Diary::class, Chat::class,
        ChatMessage::class, AiChatConfig::class, UserChatConfig::class,
        DiaryTag::class, ChatTag::class, TagFolder::class],
    version = 21,
    exportSchema = false
)
abstract class AppDatabase: RoomDatabase(){
    abstract fun diaryDao(): DiaryDao
    abstract fun chatDao(): ChatDao
    abstract fun tagDao(): TagDao
    companion object{
        private var INSTANCE: AppDatabase?=null
        fun getDatabase(context: Context): AppDatabase{
            return INSTANCE?:synchronized(this){
                val instance=Room.databaseBuilder(context,
                    AppDatabase::class.java,
                    "diary_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE=instance
                instance
            }
        }
    }
}