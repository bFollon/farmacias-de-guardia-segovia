package com.farmaciasdeGuardia.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.Date

/**
 * Room entity for caching geocoded coordinates
 * Equivalent to iOS CoordinateCache's UserDefaults storage
 */
@Entity(tableName = "cached_coordinates")
@Serializable
data class CachedCoordinate(
    @PrimaryKey
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Date,
    val cacheVersion: Int = CURRENT_CACHE_VERSION
) {
    companion object {
        const val CURRENT_CACHE_VERSION = 1
        private const val CACHE_EXPIRY_DAYS = 30L
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }
    
    /**
     * Check if this cached coordinate has expired
     * Cache expires after 30 days (same as iOS version)
     */
    val isExpired: Boolean
        get() {
            val now = Date()
            val ageInMillis = now.time - timestamp.time
            return ageInMillis > (CACHE_EXPIRY_DAYS * MILLIS_PER_DAY)
        }
    
    /**
     * Create a new cached coordinate entry
     */
    fun copy(
        newLatitude: Double = latitude,
        newLongitude: Double = longitude,
        newTimestamp: Date = Date()
    ): CachedCoordinate {
        return CachedCoordinate(
            address = address,
            latitude = newLatitude,
            longitude = newLongitude,
            timestamp = newTimestamp,
            cacheVersion = CURRENT_CACHE_VERSION
        )
    }
}
