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

val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ai_chat_configs ADD COLUMN enableRagSearch INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN visibleToAiId INTEGER")
    }
}

val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN visibleToAiIds TEXT NOT NULL DEFAULT '[]'")
    }
}

val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chatId INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                date TEXT NOT NULL,
                photoUris TEXT NOT NULL,
                toolExecutions TEXT NOT NULL,
                aiId INTEGER,
                visibleToAiIds TEXT NOT NULL DEFAULT '[]',
                reasoningContent TEXT,
                FOREIGN KEY(chatId) REFERENCES chats(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO chat_messages_new (
                id, chatId, role, content, date, photoUris, toolExecutions, aiId, visibleToAiIds, reasoningContent
            )
            SELECT
                id,
                chatId,
                role,
                content,
                date,
                photoUris,
                toolExecutions,
                aiId,
                CASE
                    WHEN visibleToAiIds IS NOT NULL AND TRIM(visibleToAiIds) != '' THEN visibleToAiIds
                    WHEN visibleToAiId IS NOT NULL THEN '[' || visibleToAiId || ']'
                    ELSE '[]'
                END,
                reasoningContent
            FROM chat_messages
            """.trimIndent()
        )

        db.execSQL("DROP TABLE chat_messages")
        db.execSQL("ALTER TABLE chat_messages_new RENAME TO chat_messages")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_chatId ON chat_messages(chatId)")
    }
}

@Database(
    entities = [Diary::class, Chat::class,
        ChatMessage::class, AiChatConfig::class,
        UserChatConfig::class, GroupChatMember::class,
        DiaryTag::class, ChatTag::class, TagFolder::class,
        AlarmEntity::class, EmbeddingRecord::class],
    version = 46,
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
                .addMigrations(MIGRATION_45_46)
                .addMigrations(MIGRATION_44_45)
                .addMigrations(MIGRATION_43_44)
                .addMigrations(MIGRATION_42_43)
                .addMigrations(MIGRATION_40_41)
                .build()
                INSTANCE=instance
                instance
            }
        }
    }
}