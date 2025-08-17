package com.farmaciasdeGuardia.database.converters

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room TypeConverters for converting complex types to/from database storage
 * Handles Date objects and other complex types used in entities
 */
class DatabaseConverters {
    
    /**
     * Convert Date to Long for database storage
     */
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }
    
    /**
     * Convert Long from database to Date
     */
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
}
