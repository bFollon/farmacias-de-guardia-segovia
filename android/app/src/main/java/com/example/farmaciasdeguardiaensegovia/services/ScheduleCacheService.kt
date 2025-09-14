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

package com.example.farmaciasdeguardiaensegovia.services

import android.content.Context
import com.example.farmaciasdeguardiaensegovia.data.DutyLocation
import com.example.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.example.farmaciasdeguardiaensegovia.data.Region
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * High-performance cache service for parsed pharmacy schedules
 * Dramatically reduces app startup time by avoiding PDF re-parsing
 */
class ScheduleCacheService(private val context: Context) {
    
    private val cacheDir = File(context.cacheDir, "schedules")
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
    fun isCacheValid(region: Region): Boolean {
        val cacheFile = getCacheFile(region)
        val metadataFile = getMetadataFile(region)
        
        if (!cacheFile.exists() || !metadataFile.exists()) {
            return false
        }
        
        try {
            val metadata = json.decodeFromString<CacheMetadata>(metadataFile.readText())
            val pdfFile = File(context.cacheDir, "pdfs/${region.id}.pdf")
            
            // Check if PDF file exists and hasn't been modified since cache was created
            if (!pdfFile.exists()) {
                DebugConfig.debugPrint("📂 PDF file not found for ${region.name}, cache invalid")
                return false
            }
            
            val pdfLastModified = pdfFile.lastModified()
            val cacheIsValid = pdfLastModified <= metadata.pdfLastModified
            
            if (cacheIsValid) {
                DebugConfig.debugPrint("✅ Cache valid for ${region.name} (PDF: ${pdfLastModified}, Cache: ${metadata.pdfLastModified})")
            } else {
                DebugConfig.debugPrint("❌ Cache invalid for ${region.name} - PDF newer than cache")
            }
            
            return cacheIsValid
            
        } catch (e: Exception) {
            DebugConfig.debugError("Error checking cache validity for ${region.name}", e)
            return false
        }
    }
    
    /**
     * Load cached schedules for a region (if valid)
     */
    fun loadCachedSchedules(region: Region): Map<DutyLocation, List<PharmacySchedule>>? {
        if (!isCacheValid(region)) {
            return null
        }
        
        val cacheFile = getCacheFile(region)
        
        try {
            val startTime = System.currentTimeMillis()
            val cachedData = json.decodeFromString<CachedSchedules>(cacheFile.readText())
            val loadTime = System.currentTimeMillis() - startTime
            
            DebugConfig.debugPrint("⚡ Loaded ${cachedData.schedules.size} cached schedules for ${region.name} in ${loadTime}ms")
            return cachedData.schedules
            
        } catch (e: Exception) {
            DebugConfig.debugError("Error loading cached schedules for ${region.name}", e)
            // If cache is corrupted, delete it
            deleteCacheFiles(region)
            return null
        }
    }
    
    /**
     * Save parsed schedules to cache
     */
    fun saveSchedulesToCache(region: Region, schedules: Map<DutyLocation, List<PharmacySchedule>>) {
        try {
            val startTime = System.currentTimeMillis()
            
            // Create cache data
            val cachedData = CachedSchedules(
                regionId = region.id,
                regionName = region.name,
                schedules = schedules,
                cacheTimestamp = System.currentTimeMillis()
            )
            
            // Save schedules to cache file
            val cacheFile = getCacheFile(region)
            cacheFile.writeText(json.encodeToString(cachedData))
            
            // Save metadata
            val pdfFile = File(context.cacheDir, "pdfs/${region.id}.pdf")
            val metadata = CacheMetadata(
                regionId = region.id,
                scheduleCount = schedules.size,
                cacheTimestamp = System.currentTimeMillis(),
                pdfLastModified = if (pdfFile.exists()) pdfFile.lastModified() else System.currentTimeMillis()
            )
            
            val metadataFile = getMetadataFile(region)
            metadataFile.writeText(json.encodeToString(metadata))
            
            val saveTime = System.currentTimeMillis() - startTime
            val cacheSize = cacheFile.length() / 1024 // KB
            
            DebugConfig.debugPrint("💾 Cached ${schedules.size} schedules for ${region.name} in ${saveTime}ms (${cacheSize}KB)")
            
        } catch (e: Exception) {
            DebugConfig.debugError("Error saving schedules to cache for ${region.name}", e)
        }
    }
    
    /**
     * Clear cache for a specific region
     */
    fun clearRegionCache(region: Region) {
        deleteCacheFiles(region)
        DebugConfig.debugPrint("🗑️ Cleared cache for ${region.name}")
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
    
    private fun getCacheFile(region: Region): File {
        return File(cacheDir, "${region.id}.json")
    }
    
    private fun getMetadataFile(region: Region): File {
        return File(cacheDir, "${region.id}.meta.json")
    }
    
    private fun deleteCacheFiles(region: Region) {
        try {
            getCacheFile(region).delete()
            getMetadataFile(region).delete()
        } catch (e: Exception) {
            DebugConfig.debugError("Error deleting cache files for ${region.name}", e)
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
        val pdfLastModified: Long
    )
}
