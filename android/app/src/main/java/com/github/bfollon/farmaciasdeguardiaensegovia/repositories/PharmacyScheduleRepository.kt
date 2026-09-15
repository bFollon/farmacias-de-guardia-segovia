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
import com.github.bfollon.farmaciasdeguardiaensegovia.services.AnalyticsService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ErrorReportingService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.LaLigaBlockingService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ScheduleCacheService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ScheduleSyncService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ScheduleSyncStatus
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.minus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for managing pharmacy schedules across the application
 * Provides centralized caching and loading of PDF data
 * Shared between SplashViewModel and ScheduleService
 */
class PharmacyScheduleRepository private constructor(private val context: Context) {

    private val cacheService = ScheduleCacheService(context)
    private val scheduleSyncService = ScheduleSyncService.getInstance(context)

    // Cache for loaded schedules - keyed by region ID
    private var schedulesCache = mapOf<DutyLocation, List<PharmacySchedule>>()

    // Regions externally refreshed but not yet reflected in an active ScheduleViewModel
    @Volatile
    private var dirtyRegionIds = setOf<String>()

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
    suspend fun loadSchedules(location: DutyLocation, forceRefresh: Boolean = false): List<PharmacySchedule> = withContext(Dispatchers.IO) {
        val cachedEntry = schedulesCache[location]

        // Return cached data if it exists and is fresh (unless force refresh)
        if (!forceRefresh && cachedEntry != null) {
            DebugConfig.debugPrint("PharmacyScheduleRepository: Returning in-memory cached schedules for ${location.name} (${cachedEntry.size} schedules)")
            return@withContext cachedEntry
        }

        // Best-effort sync: skipped entirely when offline, and never clears the existing disk
        // cache on failure - a failing sync just leaves whatever's already there untouched.
        val knownVersion = cacheService.cachedServerVersion(location)
        val knownVersions = knownVersion?.let { mapOf(location.id to it) } ?: emptyMap()

        val summary = scheduleSyncService.syncAll(listOf(location.id), knownVersions) { locationId, schedule ->
            cacheService.saveSchedulesToCache(location, schedule.schedules, schedule.version)
            AnalyticsService.track("schedules_synced", mapOf(
                "location_id" to locationId,
                "region" to location.associatedRegion.id,
                "schedules_count" to schedule.schedules.size,
                "version" to schedule.version
            ))
        }
        updateSyncStatus(summary, locationId = location.id)

        return@withContext loadFromDiskOrBundle(location)
    }

    /**
     * Sync every given location in a single batched request (one manifest fetch total, not one
     * per location) and load each from whatever ends up on disk/bundle.
     *
     * Exists specifically for preload paths: calling [loadSchedules] once per location used to
     * mean one manifest fetch - and one timeout, one Bugsink report - per location. For Segovia
     * Rural alone that's 8 ZBS locations behind a single PDF; across the whole app, 11. With an
     * unreachable server that meant a stack of sequential ~10s timeouts before the splash screen
     * would let go of the user, plus a near-duplicate error report per location for the same
     * root cause. Batching collapses all of that into a single attempt.
     */
    suspend fun preloadAll(locations: List<DutyLocation>): Map<DutyLocation, List<PharmacySchedule>> = withContext(Dispatchers.IO) {
        val locationsById = locations.associateBy { it.id }
        val knownVersions = locations.mapNotNull { location ->
            cacheService.cachedServerVersion(location)?.let { location.id to it }
        }.toMap()

        val summary = scheduleSyncService.syncAll(locations.map { it.id }, knownVersions) { locationId, schedule ->
            val location = locationsById[locationId] ?: return@syncAll
            cacheService.saveSchedulesToCache(location, schedule.schedules, schedule.version)
            AnalyticsService.track("schedules_synced", mapOf(
                "location_id" to locationId,
                "region" to location.associatedRegion.id,
                "schedules_count" to schedule.schedules.size,
                "version" to schedule.version
            ))
        }
        updateSyncStatus(summary, locationId = null)

        locations.associateWith { loadFromDiskOrBundle(it) }
    }

    /** Shared tail of the load flow: disk cache, then bundled day-zero JSON. Assumes any sync
     * attempt (or lack thereof) has already happened - this never touches the network. */
    private fun loadFromDiskOrBundle(location: DutyLocation): List<PharmacySchedule> {
        val cachedSchedules = cacheService.loadCachedSchedules(location)
        if (cachedSchedules != null && cachedSchedules.isNotEmpty()) {
            AnalyticsService.track("schedules_loaded_from_cache", mapOf(
                "region" to location.associatedRegion.id,
                "schedules_count" to cachedSchedules.size
            ))
            schedulesCache = schedulesCache + (location to cachedSchedules)
            DebugConfig.debugPrint("PharmacyScheduleRepository: Loaded ${cachedSchedules.size} schedules from persistent cache for ${location.name}")
            return cachedSchedules
        }

        // Bundled day-zero JSON - only reached if disk cache is empty/invalid
        val bundled = cacheService.loadBundledSchedules(location)
        if (bundled != null) {
            AnalyticsService.track("schedules_loaded_from_bundle", mapOf("location_id" to location.id))
            schedulesCache = schedulesCache + (location to bundled.schedules)
            DebugConfig.debugPrint("PharmacyScheduleRepository: Using bundled schedules for ${location.name}")
            return bundled.schedules
        }

        DebugConfig.debugPrint("⚠️ PharmacyScheduleRepository: No data available for ${location.name} (no cache, no sync, no bundle)")
        return emptyList()
    }

    /**
     * Updates [ScheduleSyncStatus] and fires `schedule_sync_failed` from a sync attempt's
     * outcome. [locationId] is only used to tag the analytics event for a single-location sync;
     * pass `null` for a batched preload (a manifest failure there isn't any one location's
     * fault, so it's tagged "all" instead of picking one arbitrarily).
     */
    private suspend fun updateSyncStatus(summary: ScheduleSyncService.SyncSummary, locationId: String?) {
        val manifestFailure = summary.manifestFailure
        if (manifestFailure != null) {
            // Manifest fetch failed before any per-location sync was attempted (e.g. the
            // device is online but can't reach homeserver.local - off the LAN, server down).
            ScheduleSyncStatus.reportFailure()
            val laLigaSuspected = LaLigaBlockingService.onServerUnreachable()
            ScheduleSyncStatus.reportLaLigaBlocking(laLigaSuspected)
            AnalyticsService.track("schedule_sync_failed", mapOf(
                "location_id" to (locationId ?: "all"),
                "error" to syncErrorLabel(manifestFailure)
            ))
        } else if (summary.failed.isNotEmpty()) {
            ScheduleSyncStatus.reportFailure()
            summary.failed.forEach { (failedLocationId, syncError) ->
                AnalyticsService.track("schedule_sync_failed", mapOf(
                    "location_id" to failedLocationId,
                    "error" to syncErrorLabel(syncError)
                ))
            }
        } else if (!summary.skippedOffline) {
            // Every requested location either got fresh data or was already up to date -
            // either way the server was reachable, so clear any previously-reported failure.
            ScheduleSyncStatus.reportSuccess()
            LaLigaBlockingService.onServerReachable()
        }
    }

    private fun syncErrorLabel(error: ScheduleSyncService.SyncError): String = when (error) {
        is ScheduleSyncService.SyncError.Unauthorized -> "unauthorized"
        is ScheduleSyncService.SyncError.NotFound -> "not_found"
        is ScheduleSyncService.SyncError.ServerError -> "server_error"
        is ScheduleSyncService.SyncError.Network -> "network"
        is ScheduleSyncService.SyncError.DecodeError -> "decode_error"
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
            // One batched sync call for every location in this region (matters most for
            // Segovia Rural's 8 ZBS behind a single PDF) instead of one manifest fetch per
            // location - see preloadAll's doc comment.
            preloadAll(region.toDutyLocationList()).values.all { it.isNotEmpty() }
        } catch (e: Exception) {
            DebugConfig.debugError(
                "PharmacyScheduleRepository: Error preloading schedules for ${region.name}",
                e
            )
            ErrorReportingService.captureError(e, mapOf("region" to region.name, "url" to region.pdfURL, "operation" to "preloadSchedules"))
            false
        }
    }

    /**
     * Clear all cached schedules
     */
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        schedulesCache = emptyMap()
        cacheService.clearAllCache()
        DebugConfig.debugPrint("PharmacyScheduleRepository: All caches cleared")
    }

    /**
     * Clear cache for a specific region
     */
    suspend fun clearCacheForRegion(region: Region) = withContext(Dispatchers.IO) {
        // Get all locations for this region (includes all ZBS for Segovia Rural)
        val locationsToClear = region.toDutyLocationList()

        // Clear from in-memory cache
        locationsToClear.forEach { location ->
            schedulesCache = schedulesCache - location
        }

        // Clear persistent cache for all locations
        locationsToClear.forEach { location ->
            cacheService.clearRegionCache(location)
        }

        DebugConfig.debugPrint("PharmacyScheduleRepository: Cleared cache for ${region.name} (${locationsToClear.size} locations)")
    }

    /**
     * Mark a region as externally refreshed so open ScheduleViewModels can reload on resume
     */
    fun markRegionDirty(region: Region) {
        dirtyRegionIds = dirtyRegionIds + region.id
        DebugConfig.debugPrint("PharmacyScheduleRepository: Marked ${region.name} as dirty")
    }

    fun isRegionDirty(region: Region): Boolean = region.id in dirtyRegionIds

    fun clearRegionDirty(region: Region) {
        dirtyRegionIds = dirtyRegionIds - region.id
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
