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
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * High-performance cache service for parsed pharmacy schedules
 * Dramatically reduces app startup time by avoiding PDF re-parsing
 */
class ScheduleCacheService(private val context: Context) {

    /**
     * Current cache format version. Increment when cache structure changes.
     * Version 1: Initial cache implementation
     */
    private val CURRENT_CACHE_VERSION = 2

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
     * Check if cached schedules exist and are still valid for a region
     */
    fun isCacheValid(location: DutyLocation): Boolean {
        val cacheFile = getCacheFile(location)
        val metadataFile = getMetadataFile(location)
        
        if (!cacheFile.exists() || !metadataFile.exists()) {
            return false
        }
        
        try {
            val metadata = json.decodeFromString<CacheMetadata>(metadataFile.readText())

            // Check cache version first
            if (metadata.cacheVersion != CURRENT_CACHE_VERSION) {
                DebugConfig.debugPrint("❌ Cache version mismatch for ${location.name} (expected: $CURRENT_CACHE_VERSION, found: ${metadata.cacheVersion})")
                return false
            }

            val pdfFile = File(context.filesDir, "pdfs/${location.associatedRegion.id}.pdf")

            // Check if PDF file exists and hasn't been modified since cache was created
            if (!pdfFile.exists()) {
                DebugConfig.debugPrint("📂 PDF file not found for ${location.associatedRegion.name}, cache invalid")
                return false
            }
            
            val pdfLastModified = pdfFile.lastModified()
            val cacheIsValid = pdfLastModified <= metadata.pdfLastModified

            if (cacheIsValid) {
                DebugConfig.debugPrint("✅ Cache valid for ${location.name} (PDF: ${pdfLastModified}, Cache: ${metadata.pdfLastModified}, Version: ${metadata.cacheVersion})")
            } else {
                DebugConfig.debugPrint("❌ Cache invalid for ${location.name} - PDF newer than cache")
            }
            
            return cacheIsValid
            
        } catch (e: Exception) {
            DebugConfig.debugError("Error checking cache validity for ${location.name}", e)
            return false
        }
    }
    
    /**
     * Load cached schedules for a region (if valid)
     */
    fun loadCachedSchedules(location: DutyLocation): Map<DutyLocation, List<PharmacySchedule>>? {
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
            // If cache is corrupted, delete it
            deleteCacheFiles(location)
            return null
        }
    }
    
    /**
     * Save parsed schedules to cache
     */
    fun saveSchedulesToCache(location: DutyLocation, schedules: Map<DutyLocation, List<PharmacySchedule>>) {
        try {
            val startTime = System.currentTimeMillis()
            
            // Create cache data
            val cachedData = CachedSchedules(
                regionId = location.id,
                regionName = location.name,
                schedules = schedules,
                cacheTimestamp = System.currentTimeMillis()
            )
            
            // Save schedules to cache file
            val cacheFile = getCacheFile(location)
            cacheFile.writeText(json.encodeToString(cachedData))
            
            // Save metadata
            val pdfFile = File(context.filesDir, "pdfs/${location.associatedRegion.id}.pdf")
            val metadata = CacheMetadata(
                regionId = location.id,
                scheduleCount = schedules.size,
                cacheTimestamp = System.currentTimeMillis(),
                pdfLastModified = if (pdfFile.exists()) pdfFile.lastModified() else System.currentTimeMillis(),
                cacheVersion = CURRENT_CACHE_VERSION
            )
            
            val metadataFile = getMetadataFile(location)
            metadataFile.writeText(json.encodeToString(metadata))
            
            val saveTime = System.currentTimeMillis() - startTime
            val cacheSize = cacheFile.length() / 1024 // KB
            
            DebugConfig.debugPrint("💾 Cached ${schedules.size} schedules for ${location.name} in ${saveTime}ms (${cacheSize}KB)")
            
        } catch (e: Exception) {
            DebugConfig.debugError("Error saving schedules to cache for ${location.name}", e)
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
        val regionId: String,
        val regionName: String,
        val schedules: Map<DutyLocation, List<PharmacySchedule>>,
        val cacheTimestamp: Long
    )
    
    @Serializable
    private data class CacheMetadata(
        val regionId: String,
        val scheduleCount: Int,
        val cacheTimestamp: Long,
        val pdfLastModified: Long,
        val cacheVersion: Int = 1  // Default for backward compatibility with old caches
    )
}
