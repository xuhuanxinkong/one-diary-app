package com.xinkong.diary.repository

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromIntList(value: List<Int>?): String {
        return value?.let { Json.encodeToString(it) } ?: "[]"
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int> {
        return try {
            if (value.isNullOrBlank()) emptyList()
            else Json.decodeFromString<List<Int>>(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
