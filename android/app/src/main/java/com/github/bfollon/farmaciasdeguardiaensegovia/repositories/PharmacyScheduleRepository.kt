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

package com.github.bfollon.farmaciasdeguardiaensegovia.repositories

import android.content.Context
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.data.ZBS
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFDownloadService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFProcessingService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ScheduleCacheService
import com.github.bfollon.farmaciasdeguardiaensegovia.utils.MapUtils.mergeWith
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.minus

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
    private var schedulesCache = mapOf<DutyLocation, List<PharmacySchedule>>()

    companion object {
        @Volatile
        private var INSTANCE: PharmacyScheduleRepository? = null

        /**
         * Get singleton instance of the repository
         */
        fun getInstance(context: Context): PharmacyScheduleRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PharmacyScheduleRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    /**
     * Load schedules for a specific region
     * @param region The region to load schedules for
     * @param forceRefresh Whether to bypass cache and reload
     * @return List of pharmacy schedules for the region
     */
    suspend fun loadSchedules(location: DutyLocation): List<PharmacySchedule> {
        // TODO decide how we want to manage this function, if region or location.
        val cachedEntry = schedulesCache[location]

        // Return cached data if it exists and is fresh (unless force refresh)
        if (!location.associatedRegion.forceRefresh && cachedEntry != null) {
            DebugConfig.debugPrint("PharmacyScheduleRepository: Returning in-memory cached schedules for ${location.name} (${cachedEntry.size} schedules)")
            return cachedEntry
        }

        // Try to load from persistent cache (serialized file cache)
        if (!location.associatedRegion.forceRefresh) {
            val cachedSchedules = cacheService.loadCachedSchedules(location)
            if (cachedSchedules != null && cachedSchedules.isNotEmpty() && cachedSchedules[location] != null) {
                // Store in memory cache as well
                schedulesCache = schedulesCache.mergeWith(cachedSchedules) { _, b -> b } // Replace the existing schedules with the new ones
                DebugConfig.debugPrint("PharmacyScheduleRepository: Loaded ${cachedSchedules.size} schedules from persistent cache for ${location.name}")
                return cachedSchedules[location]!!
            }
        }

        try {
            DebugConfig.debugPrint("PharmacyScheduleRepository: Loading schedules from PDF for ${location.name}")

            // Download the PDF file
            val fileName = "${location.associatedRegion.id}.pdf"
            val pdfFile = pdfDownloadService.downloadPDF(location.associatedRegion.pdfURL, fileName)

            if (pdfFile == null) {
                DebugConfig.debugError("PharmacyScheduleRepository: Failed to download PDF for ${location.name}. Associated region ${location.associatedRegion.name}")
                return emptyList()
            }

            // Process the PDF file
            val schedulesMap = pdfProcessingService.loadPharmacies(pdfFile, location.associatedRegion)

            if (schedulesMap.isNotEmpty()) {
                // Cache the results in memory
                schedulesCache = schedulesCache.mergeWith(schedulesMap) { _, b -> b } // Replace the existing schedules with the new ones

                // Save to persistent cache for next time
                cacheService.saveSchedulesToCache(location, schedulesMap)

                schedulesMap.forEach { (loadedLocation, schedules) ->
                    DebugConfig.debugPrint("PharmacyScheduleRepository: Successfully loaded and cached ${schedules.size} schedules for ${loadedLocation.name}")
                }

            } else {
                DebugConfig.debugWarn("PharmacyScheduleRepository: No schedules loaded from PDF for ${location.name}")
            }

            return schedulesMap[location] ?: emptyList()

        } catch (e: Exception) {
            DebugConfig.debugError(
                "PharmacyScheduleRepository: Error loading schedules for ${location.name}",
                e
            )
            return emptyList()
        }
    }

    /**
     * Check if schedules are already loaded and cached for a region
     * @param region The region to check
     * @return True if schedules are cached and fresh (either in-memory or persistent cache)
     */
    fun isLoaded(region: Region): Boolean {
        // First check in-memory cache

        val locationsToCheck = when(region.id) {
            Region.Companion.segoviaRural.id -> ZBS.Companion.availableZBS.map { DutyLocation.Companion.fromZBS(it) }
            else -> listOf(DutyLocation.Companion.fromRegion(region))
        }

        return locationsToCheck.all { location ->
            val cachedEntry = schedulesCache[location]
            if (cachedEntry != null) {
                true
            } else cacheService.isCacheValid(location)
        }
    }

    /**
     * Get cached schedules for a region (if available)
     * @param region The region to get schedules for
     * @return List of cached schedules, or null if not cached/expired
     */
    fun getCachedSchedules(location: DutyLocation): List<PharmacySchedule>? = schedulesCache[location]

    /**
     * Preload schedules in background (used by splash screen)
     * @param region The region to preload
     * @return True if loading was successful
     */
    suspend fun preloadSchedules(region: Region): Boolean {
        return try {
            region.toDutyLocationList().all {
                loadSchedules(it).isNotEmpty()
            }
        } catch (e: Exception) {
            DebugConfig.debugError(
                "PharmacyScheduleRepository: Error preloading schedules for ${region.name}",
                e
            )
            false
        }
    }

    /**
     * Clear all cached schedules
     */
    suspend fun clearAllCache() {
        schedulesCache = emptyMap()
        pdfDownloadService.clearCache()
        DebugConfig.debugPrint("PharmacyScheduleRepository: All caches cleared")
    }

    /**
     * Clear cache for a specific region
     */
    suspend fun clearCacheForRegion(region: Region) {
        schedulesCache = schedulesCache - DutyLocation.Companion.fromRegion(region)
        DebugConfig.debugPrint("PharmacyScheduleRepository: Cleared cache for ${region.name}")
    }

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String {
        val totalEntries = schedulesCache.size
        val totalSchedules = schedulesCache.values.sumOf { it.size }

        val persistentStats = cacheService.getCacheStats()
        val persistentCacheSize = persistentStats["totalCacheSize"] as? Long ?: 0L
        val persistentFileCount = persistentStats["cacheFileCount"] as? Int ?: 0

        return "In-Memory: $totalEntries entries, $totalSchedules schedules | " +
                "Persistent: $persistentFileCount cached regions, ${persistentCacheSize / 1024}KB total"
    }
}
