package com.plan.app.data.local.database

import androidx.room.TypeConverter
import com.plan.app.data.local.entity.ContentType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Type converters for Room database.
 */
class Converters {
    
    private val gson = Gson()
    
    @TypeConverter
    fun fromContentType(value: ContentType): String {
        return value.name
    }
    
    @TypeConverter
    fun toContentType(value: String): ContentType {
        return ContentType.valueOf(value)
    }
}
