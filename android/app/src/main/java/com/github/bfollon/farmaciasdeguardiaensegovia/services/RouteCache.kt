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

package com.github.bfollon.farmaciasdeguardiaensegovia.services

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Manages persistent route caching for driving/walking routes
 * Similar to CoordinateCache but for OSRM route results
 */
object RouteCache {
    
    private const val CACHE_KEY = "route_cache"
    private const val CACHE_VERSION_KEY = "route_cache_version"
    private const val CURRENT_CACHE_VERSION = 1
    private const val CACHE_EXPIRY_DAYS = 7L // 7 days expiry for routes
    
    private lateinit var sharedPreferences: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Initialize the cache with application context
     */
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences("route_cache", Context.MODE_PRIVATE)
        
        // Check cache version and clear if outdated
        val currentVersion = sharedPreferences.getInt(CACHE_VERSION_KEY, 0)
        if (currentVersion != CURRENT_CACHE_VERSION) {
            DebugConfig.debugPrint("🔄 Route cache version mismatch, clearing route cache")
            clearAllRoutes()
            sharedPreferences.edit()
                .putInt(CACHE_VERSION_KEY, CURRENT_CACHE_VERSION)
                .apply()
        }
    }
    
    /**
     * Get cached route for the given coordinates
     */
    fun getCachedRoute(from: Location, to: Location): RouteResult? {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ RouteCache not initialized")
            return null
        }
        
        val routeKey = createRouteKey(from, to)
        val cacheJson = sharedPreferences.getString(CACHE_KEY, null) ?: return null
        
        return try {
            val cache = json.decodeFromString<Map<String, CachedRoute>>(cacheJson)
            val cached = cache[routeKey]
            
            if (cached != null && !cached.isExpired) {
                DebugConfig.debugPrint("🚗 Using cached route: $routeKey")
                RouteResult(
                    distance = cached.distance,
                    travelTime = cached.travelTime,
                    walkingTime = cached.walkingTime,
                    isEstimated = cached.isEstimated
                )
            } else {
                if (cached?.isExpired == true) {
                    DebugConfig.debugPrint("⏰ Cached route expired: $routeKey")
                }
                null
            }
        } catch (exception: Exception) {
            DebugConfig.debugPrint("❌ Failed to decode route cache: ${exception.message}")
            null
        }
    }
    
    /**
     * Cache route result for the given coordinates
     */
    fun setCachedRoute(from: Location, to: Location, result: RouteResult) {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ RouteCache not initialized")
            return
        }
        
        val routeKey = createRouteKey(from, to)
        val cache = loadCache().toMutableMap()
        
        cache[routeKey] = CachedRoute(
            distance = result.distance,
            travelTime = result.travelTime,
            walkingTime = result.walkingTime,
            isEstimated = result.isEstimated,
            timestamp = System.currentTimeMillis()
        )
        
        saveCache(cache)
        DebugConfig.debugPrint("💾 Cached route: $routeKey (${result.formattedDistance})")
    }
    
    /**
     * Clear all cached routes (called when user location changes significantly)
     */
    fun clearAllRoutes() {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ RouteCache not initialized")
            return
        }
        
        val cache = loadCache()
        val routeCount = cache.size
        
        sharedPreferences.edit()
            .remove(CACHE_KEY)
            .apply()
            
        DebugConfig.debugPrint("🗑️ Cleared all cached routes ($routeCount routes)")
    }
    
    /**
     * Clean up expired route entries
     */
    fun cleanupExpiredEntries() {
        if (!::sharedPreferences.isInitialized) {
            DebugConfig.debugPrint("❌ RouteCache not initialized")
            return
        }
        
        val cache = loadCache().toMutableMap()
        val originalCount = cache.size
        
        cache.entries.removeAll { it.value.isExpired }
        
        if (cache.size != originalCount) {
            saveCache(cache)
            DebugConfig.debugPrint("🧹 Cleaned up ${originalCount - cache.size} expired route cache entries")
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): Pair<Int, String> {
        if (!::sharedPreferences.isInitialized) {
            return 0 to "0B"
        }
        
        val cache = loadCache()
        val count = cache.size
        val sizeEstimate = count * 200 // Rough estimate: 200 bytes per route entry
        val sizeString = when {
            sizeEstimate < 1024 -> "${sizeEstimate}B"
            sizeEstimate < 1024 * 1024 -> "${sizeEstimate / 1024}KB"
            else -> "${sizeEstimate / (1024 * 1024)}MB"
        }
        return count to sizeString
    }
    
    /**
     * Create a cache key from two locations using rounded coordinates
     * Rounds to ~100m precision (3 decimal places)
     */
    private fun createRouteKey(from: Location, to: Location): String {
        val fromLat = roundCoordinate(from.latitude)
        val fromLon = roundCoordinate(from.longitude)
        val toLat = roundCoordinate(to.latitude)
        val toLon = roundCoordinate(to.longitude)
        
        return "${fromLat},${fromLon}->${toLat},${toLon}"
    }
    
    /**
     * Round coordinate to ~100m precision (3 decimal places)
     */
    private fun roundCoordinate(coord: Double): Double {
        return (coord * 1000).roundToInt() / 1000.0
    }
    
    /**
     * Load cache from SharedPreferences
     */
    private fun loadCache(): Map<String, CachedRoute> {
        val cacheJson = sharedPreferences.getString(CACHE_KEY, null) ?: return emptyMap()
        
        return try {
            json.decodeFromString<Map<String, CachedRoute>>(cacheJson)
        } catch (exception: Exception) {
            DebugConfig.debugPrint("❌ Failed to load route cache: ${exception.message}")
            emptyMap()
        }
    }
    
    /**
     * Save cache to SharedPreferences
     */
    private fun saveCache(cache: Map<String, CachedRoute>) {
        try {
            val cacheJson = json.encodeToString(cache)
            sharedPreferences.edit()
                .putString(CACHE_KEY, cacheJson)
                .apply()
        } catch (exception: Exception) {
            DebugConfig.debugPrint("❌ Failed to save route cache: ${exception.message}")
        }
    }
}

/**
 * Represents a cached route with expiration
 */
@Serializable
data class CachedRoute(
    val distance: Double, // in meters
    val travelTime: Double, // in seconds
    val walkingTime: Double, // in seconds
    val isEstimated: Boolean,
    val timestamp: Long
) {
    /**
     * Check if this cached route has expired
     */
    val isExpired: Boolean
        get() {
            val expiryTime = timestamp + (CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000)
            return System.currentTimeMillis() > expiryTime
        }
    
    companion object {
        private const val CACHE_EXPIRY_DAYS = 7L // 7 days expiry
    }
}
