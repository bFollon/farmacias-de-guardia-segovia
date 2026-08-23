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
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.LocationSchedule
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * High-performance cache service for synced pharmacy schedules.
 * Dramatically reduces app startup time by avoiding a network round trip on every launch.
 */
class ScheduleCacheService(private val context: Context) {

    /**
     * Current cache format version. Increment when cache structure changes.
     * Version 1: Initial cache implementation
     * Version 3: Switched from PDF-modification-date validity to server-version validity, and
     * from multi-location to single-location cache entries (client-offline-sync migration) -
     * old caches don't decode into the new Pharmacy/PharmacySchedule shapes anyway, so the
     * version bump forces them to be discarded.
     */
    private val CURRENT_CACHE_VERSION = 3

    private val cacheDir = File(context.filesDir, "schedules")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    
    init {
        // Ensure cache directory exists
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            DebugConfig.debugPrint("📂 Created schedule cache directory: ${cacheDir.absolutePath}")
        }
    }
    
    /**
     * Check if cached schedules exist and are in the current cache format.
     * Staleness relative to the server is handled separately, by ScheduleSyncService's
     * manifest-version pre-check before this cache is ever read - this only guards against
     * a format mismatch (e.g. a pre-migration cache that won't decode into current models).
     */
    fun isCacheValid(location: DutyLocation): Boolean {
        val cacheFile = getCacheFile(location)
        val metadataFile = getMetadataFile(location)

        if (!cacheFile.exists() || !metadataFile.exists()) {
            return false
        }

        try {
            val metadata = json.decodeFromString<CacheMetadata>(metadataFile.readText())

            if (metadata.cacheVersion != CURRENT_CACHE_VERSION) {
                DebugConfig.debugPrint("❌ Cache version mismatch for ${location.name} (expected: $CURRENT_CACHE_VERSION, found: ${metadata.cacheVersion})")
                return false
            }

            return true
        } catch (e: Exception) {
            DebugConfig.debugError("Error checking cache validity for ${location.name}", e)
            return false
        }
    }

    /**
     * The server `version` currently on disk for a location, or null if there's no valid cache.
     * Used by ScheduleSyncService to decide whether a location needs re-fetching, without it
     * needing to know anything about the on-disk cache format.
     */
    fun cachedServerVersion(location: DutyLocation): Int? {
        if (!isCacheValid(location)) return null
        return try {
            json.decodeFromString<CacheMetadata>(getMetadataFile(location).readText()).serverVersion
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The timestamp (epoch millis) the cache for a location was last saved, or null if there's
     * no cache. Used for "last synced" UI display.
     */
    fun getCacheTimestamp(location: DutyLocation): Long? {
        val cacheFile = getCacheFile(location)
        if (!cacheFile.exists()) return null
        return try {
            json.decodeFromString<CachedSchedules>(cacheFile.readText()).cacheTimestamp
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Load cached schedules for a location (if valid)
     */
    fun loadCachedSchedules(location: DutyLocation): List<PharmacySchedule>? {
        if (!isCacheValid(location)) {
            return null
        }

        val cacheFile = getCacheFile(location)

        try {
            val startTime = System.currentTimeMillis()
            val cachedData = json.decodeFromString<CachedSchedules>(cacheFile.readText())
            val loadTime = System.currentTimeMillis() - startTime

            DebugConfig.debugPrint("⚡ Loaded ${cachedData.schedules.size} cached schedules for ${location.name} in ${loadTime}ms")
            return cachedData.schedules

        } catch (e: Exception) {
            DebugConfig.debugError("Error loading cached schedules for ${location.name}", e)
            ErrorReportingService.captureError(e, mapOf("location" to location.name, "operation" to "loadCachedSchedules"))
            // If cache is corrupted, delete it
            deleteCacheFiles(location)
            return null
        }
    }

    /**
     * Save synced schedules to cache, tagged with the server `version` they came from.
     */
    fun saveSchedulesToCache(location: DutyLocation, schedules: List<PharmacySchedule>, version: Int) {
        try {
            val startTime = System.currentTimeMillis()

            val cachedData = CachedSchedules(
                locationId = location.id,
                locationName = location.name,
                schedules = schedules,
                cacheTimestamp = System.currentTimeMillis()
            )

            val cacheFile = getCacheFile(location)
            cacheFile.writeText(json.encodeToString(cachedData))

            val metadata = CacheMetadata(
                locationId = location.id,
                scheduleCount = schedules.size,
                cacheTimestamp = System.currentTimeMillis(),
                serverVersion = version,
                cacheVersion = CURRENT_CACHE_VERSION
            )

            val metadataFile = getMetadataFile(location)
            metadataFile.writeText(json.encodeToString(metadata))

            val saveTime = System.currentTimeMillis() - startTime
            val cacheSize = cacheFile.length() / 1024 // KB

            DebugConfig.debugPrint("💾 Cached ${schedules.size} schedules for ${location.name} (version $version) in ${saveTime}ms (${cacheSize}KB)")

        } catch (e: Exception) {
            DebugConfig.debugError("Error saving schedules to cache for ${location.name}", e)
            ErrorReportingService.captureError(e, mapOf("location" to location.name, "operation" to "saveSchedulesToCache"))
        }
    }

    /**
     * Loads the app-bundled schedule JSON for a location - the pre-first-sync fallback,
     * shipped in the app assets and never touched again after install. Disk cache (populated
     * by a successful sync) always takes precedence over this; it's only consulted when the
     * disk cache is empty/invalid. See Features/client-offline-sync.md.
     */
    fun loadBundledSchedules(location: DutyLocation): LocationSchedule? {
        return try {
            context.assets.open("bundled_schedules/${location.id}.json").use { stream ->
                val locationSchedule = json.decodeFromString<LocationSchedule>(stream.reader().readText())
                DebugConfig.debugPrint("📦 Loaded bundled schedule for ${location.name} (${locationSchedule.schedules.size} schedules)")
                locationSchedule
            }
        } catch (e: java.io.FileNotFoundException) {
            DebugConfig.debugPrint("📦 No bundled schedule found for ${location.name}")
            null
        } catch (e: Exception) {
            DebugConfig.debugError("Error decoding bundled schedule for ${location.name}", e)
            ErrorReportingService.captureError(e, mapOf("location" to location.name, "operation" to "loadBundledSchedules"))
            null
        }
    }
    
    /**
     * Clear cache for a specific region
     */
    fun clearRegionCache(location: DutyLocation) {
        deleteCacheFiles(location)
        DebugConfig.debugPrint("🗑️ Cleared cache for ${location.name}")
    }
    
    /**
     * Clear all cached schedules
     */
    fun clearAllCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            DebugConfig.debugPrint("🗑️ Cleared all schedule caches")
        } catch (e: Exception) {
            DebugConfig.debugError("Error clearing all caches", e)
        }
    }
    
    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        
        try {
            val files = cacheDir.listFiles() ?: emptyArray()
            val cacheFiles = files.filter { it.extension == "json" && !it.name.endsWith(".meta.json") }
            val metadataFiles = files.filter { it.name.endsWith(".meta.json") }
            
            stats["cacheDirectory"] = cacheDir.absolutePath
            stats["cacheFileCount"] = cacheFiles.size
            stats["metadataFileCount"] = metadataFiles.size
            stats["totalCacheSize"] = files.sumOf { it.length() }
            
            // Per-region stats
            cacheFiles.forEach { cacheFile ->
                val regionId = cacheFile.nameWithoutExtension
                try {
                    val cachedData = json.decodeFromString<CachedSchedules>(cacheFile.readText())
                    stats["${regionId}_scheduleCount"] = cachedData.schedules.size
                    stats["${regionId}_cacheSize"] = cacheFile.length()
                    stats["${regionId}_cacheAge"] = System.currentTimeMillis() - cachedData.cacheTimestamp
                } catch (e: Exception) {
                    stats["${regionId}_error"] = e.message ?: "Unknown error"
                }
            }
            
        } catch (e: Exception) {
            stats["error"] = e.message ?: "Unknown error"
        }
        
        return stats
    }
    
    // Private helper methods
    
    private fun getCacheFile(location: DutyLocation): File {
        return File(cacheDir, "${location.id}.json")
    }
    
    private fun getMetadataFile(location: DutyLocation): File {
        return File(cacheDir, "${location.id}.meta.json")
    }
    
    private fun deleteCacheFiles(location: DutyLocation) {
        try {
            getCacheFile(location).delete()
            getMetadataFile(location).delete()
        } catch (e: Exception) {
            DebugConfig.debugError("Error deleting cache files for ${location.name}", e)
        }
    }
    
    // Data classes for serialization
    
    @Serializable
    private data class CachedSchedules(
        val locationId: String,
        val locationName: String,
        val schedules: List<PharmacySchedule>,
        val cacheTimestamp: Long
    )

    @Serializable
    private data class CacheMetadata(
        val locationId: String,
        val scheduleCount: Int,
        val cacheTimestamp: Long,
        val serverVersion: Int,
        val cacheVersion: Int
    )
}
