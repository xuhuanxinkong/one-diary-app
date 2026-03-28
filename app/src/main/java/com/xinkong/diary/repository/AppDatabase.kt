package com.xinkong.diary.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

import androidx.room.TypeConverters
import com.xinkong.diary.data.AlarmEntity

@Database(
    entities = [Diary::class, Chat::class,
        ChatMessage::class, AiChatConfig::class, UserChatConfig::class,
        DiaryTag::class, ChatTag::class, TagFolder::class, AlarmEntity::class],
    version = 28,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase(){
    abstract fun diaryDao(): DiaryDao
    abstract fun chatDao(): ChatDao
    abstract fun tagDao(): TagDao
    abstract fun alarmDao(): AlarmDao
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