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

package com.farmaciasdeGuardia.services

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import com.bfollon.farmaciasdeGuardia.data.model.Pharmacy
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import com.farmaciasdeGuardia.database.dao.CachedCoordinateDao
import com.farmaciasdeGuardia.database.entities.CachedCoordinate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache statistics for geocoding operations
 */
data class GeocodingCacheStats(
    val sessionCacheCount: Int,
    val persistentCacheCount: Int,
    val persistentCacheSize: String
) {
    override fun toString(): String {
        return "Session: $sessionCacheCount entries, Persistent: $persistentCacheCount entries ($persistentCacheSize)"
    }
}

/**
 * Android equivalent of iOS GeocodingService
 * Provides geocoding functionality with session and persistent caching
 */
@Singleton
class GeocodingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cachedCoordinateDao: CachedCoordinateDao
) {
    
    // In-memory cache for the current session (equivalent to iOS sessionCache)
    private val sessionCache = mutableMapOf<String, Location>()
    
    private val geocoder by lazy {
        if (Geocoder.isPresent()) {
            Geocoder(context, Locale.getDefault())
        } else {
            null
        }
    }
    
    companion object {
        private const val MAX_RESULTS = 1
        private const val DEFAULT_REGION = "Segovia, España"
    }
    
    /**
     * Get coordinates for an address with region context
     * Equivalent to iOS getCoordinates(for:region:)
     */
    suspend fun getCoordinates(
        address: String,
        region: String = DEFAULT_REGION
    ): Location? {
        val cacheKey = "$address, $region"
        
        // Check session cache first (fastest)
        sessionCache[cacheKey]?.let { cachedLocation ->
            DebugConfig.debugPrint("📍 Using session cache for: $address")
            return cachedLocation
        }
        
        // Check persistent cache
        val persistentLocation = getCachedCoordinateFromDatabase(cacheKey)
        if (persistentLocation != null) {
            DebugConfig.debugPrint("📍 Using persistent cache for: $address")
            sessionCache[cacheKey] = persistentLocation // Also cache in session
            return persistentLocation
        }
        
        // Geocode using Android Geocoder
        return geocodeAddress(address, region, cacheKey)
    }
    
    /**
     * Enhanced geocoding for pharmacies that includes the pharmacy name for better accuracy
     * Equivalent to iOS getCoordinatesForPharmacy
     */
    suspend fun getCoordinatesForPharmacy(pharmacy: Pharmacy): Location? {
        // Use enhanced query with pharmacy name (like in iOS PharmacyView)
        val enhancedQuery = "${pharmacy.name}, ${pharmacy.address}, Segovia, España"
        val cacheKey = enhancedQuery
        
        // Check session cache first (fastest)
        sessionCache[cacheKey]?.let { cachedLocation ->
            DebugConfig.debugPrint("📍 Using session cache for pharmacy: ${pharmacy.name}")
            return cachedLocation
        }
        
        // Check persistent cache
        val persistentLocation = getCachedCoordinateFromDatabase(cacheKey)
        if (persistentLocation != null) {
            DebugConfig.debugPrint("📍 Using persistent cache for pharmacy: ${pharmacy.name}")
            sessionCache[cacheKey] = persistentLocation // Also cache in session
            return persistentLocation
        }
        
        // Try geocoding with enhanced query
        DebugConfig.debugPrint("🔍 Geocoding pharmacy: $enhancedQuery")
        val location = geocodeWithGeocoder(enhancedQuery)
        
        if (location != null) {
            // Cache in both session and persistent storage
            sessionCache[cacheKey] = location
            saveCachedCoordinateToDatabase(cacheKey, location)
            
            DebugConfig.debugPrint("✅ Geocoded pharmacy ${pharmacy.name} -> ${location.latitude}, ${location.longitude}")
            return location
        } else {
            DebugConfig.debugPrint("❌ No coordinates found for pharmacy: $enhancedQuery")
            
            // Fallback to address-only geocoding
            DebugConfig.debugPrint("🔄 Trying fallback geocoding with address only for: ${pharmacy.name}")
            val fallbackResult = getCoordinates(pharmacy.address)
            if (fallbackResult != null) {
                DebugConfig.debugPrint("✅ Fallback geocoding succeeded for: ${pharmacy.name}")
            } else {
                DebugConfig.debugPrint("❌ Fallback geocoding also failed for: ${pharmacy.name}")
            }
            return fallbackResult
        }
    }
    
    /**
     * Internal method to geocode an address
     */
    private suspend fun geocodeAddress(
        address: String,
        region: String,
        cacheKey: String
    ): Location? {
        val fullAddress = "$address, $region"
        
        DebugConfig.debugPrint("🔍 Geocoding address: $fullAddress")
        val location = geocodeWithGeocoder(fullAddress)
        
        if (location != null) {
            // Cache in both session and persistent storage
            sessionCache[cacheKey] = location
            saveCachedCoordinateToDatabase(cacheKey, location)
            
            DebugConfig.debugPrint("✅ Geocoded $address -> ${location.latitude}, ${location.longitude}")
            return location
        } else {
            DebugConfig.debugPrint("❌ No coordinates found for: $fullAddress")
            return null
        }
    }
    
    /**
     * Perform actual geocoding using Android Geocoder
     */
    private suspend fun geocodeWithGeocoder(address: String): Location? = withContext(Dispatchers.IO) {
        val geocoder = geocoder ?: run {
            DebugConfig.debugPrint("❌ Geocoder not available on this device")
            return@withContext null
        }
        
        try {
            val addresses = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // Use the new API for Android 13+
                geocoder.getFromLocationName(address, MAX_RESULTS)
            } else {
                // Use the deprecated API for older versions
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(address, MAX_RESULTS)
            }
            
            val firstAddress = addresses?.firstOrNull()
            if (firstAddress != null && firstAddress.hasLatitude() && firstAddress.hasLongitude()) {
                val location = Location("geocoder").apply {
                    latitude = firstAddress.latitude
                    longitude = firstAddress.longitude
                }
                return@withContext location
            } else {
                return@withContext null
            }
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ Geocoding failed for $address: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Get cached coordinate from database
     */
    private suspend fun getCachedCoordinateFromDatabase(address: String): Location? {
        return try {
            cachedCoordinateDao.getCachedCoordinate(address)?.let { cached ->
                // Check if the cache entry is still valid (not expired)
                if (!cached.isExpired()) {
                    Location("cache").apply {
                        latitude = cached.latitude
                        longitude = cached.longitude
                    }
                } else {
                    // Remove expired entry
                    cachedCoordinateDao.deleteCachedCoordinate(cached)
                    null
                }
            }
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ Error retrieving cached coordinate for $address: ${e.message}")
            null
        }
    }
    
    /**
     * Save cached coordinate to database
     */
    private suspend fun saveCachedCoordinateToDatabase(address: String, location: Location) {
        try {
            val cachedCoordinate = CachedCoordinate.create(
                address = address,
                latitude = location.latitude,
                longitude = location.longitude
            )
            cachedCoordinateDao.insertOrUpdateCachedCoordinate(cachedCoordinate)
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ Error saving cached coordinate for $address: ${e.message}")
        }
    }
    
    /**
     * Clear session cache
     * Equivalent to iOS clearSessionCache
     */
    fun clearSessionCache() {
        sessionCache.clear()
        DebugConfig.debugPrint("🗑️ Session geocoding cache cleared")
    }
    
    /**
     * Clear all caches (session + persistent)
     * Equivalent to iOS clearAllCaches
     */
    suspend fun clearAllCaches() {
        sessionCache.clear()
        cachedCoordinateDao.clearAllCachedCoordinates()
        DebugConfig.debugPrint("🗑️ All geocoding caches cleared")
    }
    
    /**
     * Get cache statistics
     * Equivalent to iOS getCacheStats
     */
    suspend fun getCacheStats(): GeocodingCacheStats {
        val sessionCount = sessionCache.size
        val persistentStats = cachedCoordinateDao.getCacheStats()
        val persistentCount = persistentStats?.count ?: 0
        val persistentSize = persistentStats?.formattedSize ?: "0 B"
        
        return GeocodingCacheStats(
            sessionCacheCount = sessionCount,
            persistentCacheCount = persistentCount,
            persistentCacheSize = persistentSize
        )
    }
    
    /**
     * Cleanup expired entries on app startup
     * Equivalent to iOS performMaintenanceCleanup
     */
    suspend fun performMaintenanceCleanup() {
        try {
            val deletedCount = cachedCoordinateDao.cleanupExpiredEntries()
            if (deletedCount > 0) {
                DebugConfig.debugPrint("🧹 GeocodingService: Cleaned up $deletedCount expired cache entries")
            }
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ Error during geocoding cache cleanup: ${e.message}")
        }
    }
    
    /**
     * Convert Android Address to Location
     */
    private fun Address.toLocation(): Location? {
        return if (hasLatitude() && hasLongitude()) {
            Location("geocoder").apply {
                latitude = this@toLocation.latitude
                longitude = this@toLocation.longitude
            }
        } else null
    }
}
