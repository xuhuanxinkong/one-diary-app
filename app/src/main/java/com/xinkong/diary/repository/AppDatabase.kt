package com.xinkong.diary.repository

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xinkong.diary.data.AlarmEntity

val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tag_folders ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [Diary::class, Chat::class,
        ChatMessage::class, AiChatConfig::class,
        UserChatConfig::class, GroupChatMember::class,
        DiaryTag::class, ChatTag::class, TagFolder::class,
        AlarmEntity::class, EmbeddingRecord::class],
    version = 42,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase: RoomDatabase(){
    abstract fun diaryDao(): DiaryDao
    abstract fun chatDao(): ChatDao
    abstract fun tagDao(): TagDao
    abstract fun alarmDao(): AlarmDao
    abstract fun embeddingDao(): EmbeddingDao
    companion object{
        private var INSTANCE: AppDatabase?=null
        fun getDatabase(context: Context): AppDatabase{
            return INSTANCE?:synchronized(this){
                val instance=Room.databaseBuilder(context,
                    AppDatabase::class.java,
                    "diary_database"
                )
                .addMigrations(MIGRATION_40_41)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE=instance
                instance
            }
        }
    }
}