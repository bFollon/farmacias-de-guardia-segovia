/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
