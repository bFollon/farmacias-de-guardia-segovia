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

package com.github.bfollon.farmaciasdeguardiaensegovia.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ScheduleCacheService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ScheduleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RegionRefreshState {
    object Pending : RegionRefreshState()
    object Refreshing : RegionRefreshState()
    object Completed : RegionRefreshState()
    data class Error(val message: String) : RegionRefreshState()
}

data class LocationSyncStatus(
    val location: DutyLocation,
    val version: Int?,
    val lastSyncedAt: Long?
) {
    val isSynced: Boolean get() = version != null
}

/**
 * ViewModel for CacheStatusScreen.
 * Manages per-location sync status data and loading state.
 */
class CacheStatusViewModel(application: Application) : AndroidViewModel(application) {

    private val cacheService = ScheduleCacheService(application)
    private val scheduleService = ScheduleService(application)

    private val allLocations: List<DutyLocation> = Region.allRegions.flatMap { it.toDutyLocationList() }

    private val _statuses = MutableStateFlow<List<LocationSyncStatus>>(emptyList())
    val statuses: StateFlow<List<LocationSyncStatus>> = _statuses.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshStates = MutableStateFlow<Map<String, RegionRefreshState>>(emptyMap())
    val refreshStates: StateFlow<Map<String, RegionRefreshState>> = _refreshStates.asStateFlow()

    init {
        loadStatus()
    }

    /**
     * Load sync status for all locations
     */
    fun loadStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            _statuses.value = buildStatuses()
            _isLoading.value = false
        }
    }

    /**
     * Refresh sync status
     */
    fun refresh() {
        loadStatus()
    }

    private fun buildStatuses(): List<LocationSyncStatus> = allLocations.map { location ->
        LocationSyncStatus(
            location = location,
            version = cacheService.cachedServerVersion(location),
            lastSyncedAt = cacheService.getCacheTimestamp(location)
        )
    }

    /**
     * Force sync all locations against the server
     */
    fun refreshAllCaches() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshStates.value = allLocations.associate { it.id to RegionRefreshState.Pending }

            try {
                for (location in allLocations) {
                    DebugConfig.debugPrint("CacheStatusViewModel: Force syncing ${location.name}")

                    _refreshStates.value = _refreshStates.value + (location.id to RegionRefreshState.Refreshing)

                    scheduleService.markRegionDirty(location.associatedRegion)
                    val schedules = scheduleService.loadSchedules(location, forceRefresh = true)

                    _refreshStates.value = _refreshStates.value + (location.id to
                        if (schedules.isNotEmpty()) RegionRefreshState.Completed else RegionRefreshState.Error("Sin datos"))
                    DebugConfig.debugPrint("CacheStatusViewModel: ✅ Force synced ${location.name}")
                }

                // Update statuses in-place to avoid triggering the full-screen loading state
                _statuses.value = buildStatuses()

            } catch (e: Exception) {
                DebugConfig.debugError("CacheStatusViewModel: Error refreshing caches", e)
            } finally {
                _isRefreshing.value = false
                _refreshStates.value = emptyMap()
            }
        }
    }
}
