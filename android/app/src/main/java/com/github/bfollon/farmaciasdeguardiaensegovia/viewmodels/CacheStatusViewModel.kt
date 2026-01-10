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
            _isRefreshing.value = true

            try {
                val allRegions = Region.allRegions

                // Force refresh each region by clearing cache first, then loading
                for (region in allRegions) {
                    DebugConfig.debugPrint("CacheStatusViewModel: Force refreshing cache for ${region.name}")

                    // Clear the cache for this region first
                    scheduleService.clearCacheForRegion(region)

                    // Load schedules which will trigger fresh PDF processing
                    val locations = region.toDutyLocationList()
                    for (location in locations) {
                        scheduleService.loadSchedules(location, forceRefresh = false)
                    }

                    DebugConfig.debugPrint("CacheStatusViewModel: ✅ Force refreshed cache for ${region.name}")
                }

                // Reload cache status to reflect updates
                loadCacheStatus()

            } catch (e: Exception) {
                DebugConfig.debugError("CacheStatusViewModel: Error refreshing caches", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
