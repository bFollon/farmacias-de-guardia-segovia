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
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.data.RegionCacheStatus
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFCacheManager
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

/**
 * ViewModel for CacheStatusScreen
 * Manages cache status data and loading state
 */
class CacheStatusViewModel(application: Application) : AndroidViewModel(application) {

    private val pdfCacheManager = PDFCacheManager.getInstance(application)
    private val scheduleService = ScheduleService(application)

    private val _cacheStatuses = MutableStateFlow<List<RegionCacheStatus>>(emptyList())
    val cacheStatuses: StateFlow<List<RegionCacheStatus>> = _cacheStatuses.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshStates = MutableStateFlow<Map<String, RegionRefreshState>>(emptyMap())
    val refreshStates: StateFlow<Map<String, RegionRefreshState>> = _refreshStates.asStateFlow()
    
    init {
        loadCacheStatus()
    }
    
    /**
     * Load cache status for all regions
     */
    fun loadCacheStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            
            val statuses = pdfCacheManager.getCacheStatus()
            
            _cacheStatuses.value = statuses
            _isLoading.value = false
        }
    }
    
    /**
     * Refresh cache status
     */
    fun refresh() {
        loadCacheStatus()
    }

    /**
     * Force refresh all PDF caches
     * Clears and reloads all region schedules
     */
    fun refreshAllCaches() {
        viewModelScope.launch {
            val allRegions = Region.allRegions

            _isRefreshing.value = true
            _refreshStates.value = allRegions.associate { it.id to RegionRefreshState.Pending }

            try {
                for (region in allRegions) {
                    DebugConfig.debugPrint("CacheStatusViewModel: Force refreshing cache for ${region.name}")

                    _refreshStates.value = _refreshStates.value + (region.id to RegionRefreshState.Refreshing)

                    scheduleService.clearCacheForRegion(region)
                    scheduleService.markRegionDirty(region)

                    val locations = region.toDutyLocationList()
                    for (location in locations) {
                        scheduleService.loadSchedules(location, forceRefresh = false)
                    }

                    _refreshStates.value = _refreshStates.value + (region.id to RegionRefreshState.Completed)
                    DebugConfig.debugPrint("CacheStatusViewModel: ✅ Force refreshed cache for ${region.name}")
                }

                // Update statuses in-place to avoid triggering the full-screen loading state
                _cacheStatuses.value = pdfCacheManager.getCacheStatus()

            } catch (e: Exception) {
                DebugConfig.debugError("CacheStatusViewModel: Error refreshing caches", e)
            } finally {
                _isRefreshing.value = false
                _refreshStates.value = emptyMap()
            }
        }
    }
}
