/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published b    suspend fun clearAllCaches() {
        loadingMutex.withLock {
            schedulesCache.clear()
            pdfProcessingService.clearAllCaches()
            pdfDownloadService.clearCache()
            cacheService.clearAllCache()
            DebugConfig.debugPrint("PharmacyScheduleRepository: All caches cleared (including persistent cache)")
        }
    }
    
    /**
     * Clear cache for a specific region
     */
    suspend fun clearCacheForRegion(region: Region) {
        loadingMutex.withLock {
            schedulesCache.remove(region.id)
            pdfProcessingService.clearCacheForRegion(region)
            cacheService.clearRegionCache(region)
            DebugConfig.debugPrint("PharmacyScheduleRepository: Cleared cache for ${region.name} (including persistent cache)")
        }
    }are Foundation, either version 3 of the License, or
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

package com.example.farmaciasdeguardiaensegovia.repositories

import android.content.Context
import com.example.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.example.farmaciasdeguardiaensegovia.data.Region
import com.example.farmaciasdeguardiaensegovia.services.DebugConfig
import com.example.farmaciasdeguardiaensegovia.services.PDFDownloadService
import com.example.farmaciasdeguardiaensegovia.services.PDFProcessingService
import com.example.farmaciasdeguardiaensegovia.services.ScheduleCacheService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository for managing pharmacy schedules across the application
 * Provides centralized caching and loading of PDF data
 * Shared between SplashViewModel and ScheduleService
 */
class PharmacyScheduleRepository private constructor(private val context: Context) {
    
    private val pdfDownloadService = PDFDownloadService(context)
    private val pdfProcessingService = PDFProcessingService()
    private val cacheService = ScheduleCacheService(context)
    
    // Cache for loaded schedules - keyed by region ID
    private val schedulesCache = mutableMapOf<String, ScheduleCacheEntry>()
    
    // Mutex to prevent concurrent loading of the same region
    private val loadingMutex = Mutex()
    
    companion object {
        @Volatile
        private var INSTANCE: PharmacyScheduleRepository? = null
        
        /**
         * Get singleton instance of the repository
         */
        fun getInstance(context: Context): PharmacyScheduleRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PharmacyScheduleRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Cache entry containing schedules and metadata
     */
    private data class ScheduleCacheEntry(
        val schedules: List<PharmacySchedule>,
        val loadedAt: Long = System.currentTimeMillis(),
        val region: Region
    ) {
        /**
         * Check if cache entry is still fresh (less than 1 hour old)
         */
        fun isFresh(): Boolean {
            return (System.currentTimeMillis() - loadedAt) < 3600000L // 1 hour
        }
    }
    
    /**
     * Load schedules for a specific region
     * @param region The region to load schedules for
     * @param forceRefresh Whether to bypass cache and reload
     * @return List of pharmacy schedules for the region
     */
    suspend fun loadSchedules(region: Region): List<PharmacySchedule> {
        return loadingMutex.withLock {
            val cacheKey = region.id
            val cachedEntry = schedulesCache[cacheKey]
            
            // Return cached data if it exists and is fresh (unless force refresh)
            if (!region.forceRefresh && cachedEntry != null && cachedEntry.isFresh()) {
                DebugConfig.debugPrint("PharmacyScheduleRepository: Returning in-memory cached schedules for ${region.name} (${cachedEntry.schedules.size} schedules)")
                return@withLock cachedEntry.schedules
            }
            
            // Try to load from persistent cache (serialized file cache)
            if (!region.forceRefresh) {
                val cachedSchedules = cacheService.loadCachedSchedules(region)
                if (cachedSchedules != null && cachedSchedules.isNotEmpty()) {
                    // Store in memory cache as well
                    schedulesCache[cacheKey] = ScheduleCacheEntry(cachedSchedules, region = region)
                    DebugConfig.debugPrint("PharmacyScheduleRepository: Loaded ${cachedSchedules.size} schedules from persistent cache for ${region.name}")
                    return@withLock cachedSchedules
                }
            }
            
            try {
                DebugConfig.debugPrint("PharmacyScheduleRepository: Loading schedules from PDF for ${region.name}")
                
                // Download the PDF file
                val fileName = "${region.id}.pdf"
                val pdfFile = pdfDownloadService.downloadPDF(region.pdfURL, fileName)
                
                if (pdfFile == null) {
                    DebugConfig.debugError("PharmacyScheduleRepository: Failed to download PDF for ${region.name}")
                    return@withLock emptyList()
                }
                
                // Process the PDF file
                val schedules = pdfProcessingService.loadPharmacies(pdfFile, region)
                
                if (schedules.isNotEmpty()) {
                    // Cache the results in memory
                    schedulesCache[cacheKey] = ScheduleCacheEntry(schedules, region = region)
                    
                    // Save to persistent cache for next time
                    cacheService.saveSchedulesToCache(region, schedules)
                    
                    DebugConfig.debugPrint("PharmacyScheduleRepository: Successfully loaded and cached ${schedules.size} schedules for ${region.name}")
                    
                    // Log sample data for debugging
                    val sampleSchedule = schedules.first()
                    DebugConfig.debugPrint("Sample schedule: ${sampleSchedule.date.day} ${sampleSchedule.date.month}")
                    DebugConfig.debugPrint("Day shift pharmacies: ${sampleSchedule.dayShiftPharmacies.map { it.name }}")
                    DebugConfig.debugPrint("Night shift pharmacies: ${sampleSchedule.nightShiftPharmacies.map { it.name }}")
                } else {
                    DebugConfig.debugWarn("PharmacyScheduleRepository: No schedules loaded from PDF for ${region.name}")
                }
                
                return@withLock schedules
                
            } catch (e: Exception) {
                DebugConfig.debugError("PharmacyScheduleRepository: Error loading schedules for ${region.name}", e)
                return@withLock emptyList()
            }
        }
    }
    
    /**
     * Check if schedules are already loaded and cached for a region
     * @param region The region to check
     * @return True if schedules are cached and fresh (either in-memory or persistent cache)
     */
    fun isLoaded(region: Region): Boolean {
        // First check in-memory cache
        val cachedEntry = schedulesCache[region.id]
        if (cachedEntry != null && cachedEntry.isFresh()) {
            return true
        }
        
        // Then check persistent cache
        return cacheService.isCacheValid(region)
    }
    
    /**
     * Get cached schedules for a region (if available)
     * @param region The region to get schedules for
     * @return List of cached schedules, or null if not cached/expired
     */
    fun getCachedSchedules(region: Region): List<PharmacySchedule>? {
        val cachedEntry = schedulesCache[region.id]
        return if (cachedEntry != null && cachedEntry.isFresh()) {
            DebugConfig.debugPrint("PharmacyScheduleRepository: Retrieved cached schedules for ${region.name}")
            cachedEntry.schedules
        } else {
            null
        }
    }
    
    /**
     * Preload schedules in background (used by splash screen)
     * @param region The region to preload
     * @return True if loading was successful
     */
    suspend fun preloadSchedules(region: Region): Boolean {
        return try {
            val schedules = loadSchedules(region)
            schedules.isNotEmpty()
        } catch (e: Exception) {
            DebugConfig.debugError("PharmacyScheduleRepository: Error preloading schedules for ${region.name}", e)
            false
        }
    }
    
    /**
     * Clear all cached schedules
     */
    suspend fun clearAllCache() {
        loadingMutex.withLock {
            schedulesCache.clear()
            pdfProcessingService.clearCache()
            pdfDownloadService.clearCache()
            DebugConfig.debugPrint("PharmacyScheduleRepository: All caches cleared")
        }
    }
    
    /**
     * Clear cache for a specific region
     */
    suspend fun clearCacheForRegion(region: Region) {
        loadingMutex.withLock {
            schedulesCache.remove(region.id)
            pdfProcessingService.clearCacheForRegion(region)
            DebugConfig.debugPrint("PharmacyScheduleRepository: Cleared cache for ${region.name}")
        }
    }
    
    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String {
        val totalEntries = schedulesCache.size
        val freshEntries = schedulesCache.values.count { it.isFresh() }
        val totalSchedules = schedulesCache.values.sumOf { it.schedules.size }
        
        val persistentStats = cacheService.getCacheStats()
        val persistentCacheSize = persistentStats["totalCacheSize"] as? Long ?: 0L
        val persistentFileCount = persistentStats["cacheFileCount"] as? Int ?: 0
        
        return "In-Memory: $freshEntries/$totalEntries fresh entries, $totalSchedules schedules | " +
                "Persistent: $persistentFileCount cached regions, ${persistentCacheSize / 1024}KB total"
    }
}
