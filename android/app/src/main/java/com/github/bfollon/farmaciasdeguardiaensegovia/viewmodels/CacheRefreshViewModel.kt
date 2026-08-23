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
import com.github.bfollon.farmaciasdeguardiaensegovia.data.UpdateProgressState
import com.github.bfollon.farmaciasdeguardiaensegovia.services.AnalyticsService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.NetworkMonitor
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ScheduleCacheService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ScheduleSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for CacheRefreshScreen.
 * Manages sync progress and state for all locations.
 */
class CacheRefreshViewModel(application: Application) : AndroidViewModel(application) {

    private val cacheService = ScheduleCacheService(application)
    private val syncService = ScheduleSyncService.getInstance(application)

    private val _refreshStates = MutableStateFlow<Map<String, UpdateProgressState>>(emptyMap())
    val refreshStates: StateFlow<Map<String, UpdateProgressState>> = _refreshStates.asStateFlow()

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    private val _wasOffline = MutableStateFlow(false)
    val wasOffline: StateFlow<Boolean> = _wasOffline.asStateFlow()

    val locations: List<DutyLocation> = Region.allRegions.flatMap { it.toDutyLocationList() }

    init {
        startRefresh()
    }

    /**
     * Start the sync process for all locations
     */
    private fun startRefresh() {
        AnalyticsService.track("cache_refresh_triggered", mapOf("location_count" to locations.size))

        viewModelScope.launch {
            if (!NetworkMonitor.isOnline()) {
                val offlineStates = locations.associate {
                    it.id to UpdateProgressState.Error("Sin conexión a Internet")
                }
                _refreshStates.value = offlineStates
                _wasOffline.value = true
                _isCompleted.value = true
                return@launch
            }

            _refreshStates.value = locations.associate { it.id to UpdateProgressState.Checking }

            // Empty knownVersions forces a real conditional GET for every location, bypassing
            // the manifest-version pre-check - this is an explicit "check everything now" action.
            val summary = syncService.syncAll(locations.map { it.id }, emptyMap()) { locationId, schedule ->
                val location = locations.first { it.id == locationId }
                cacheService.saveSchedulesToCache(location, schedule.schedules, schedule.version)
                _refreshStates.value = _refreshStates.value + (locationId to UpdateProgressState.Downloaded)
            }

            _refreshStates.value = _refreshStates.value + summary.unchanged.associateWith { UpdateProgressState.UpToDate }
            _refreshStates.value = _refreshStates.value + summary.failed.mapValues {
                UpdateProgressState.Error("Error de sincronización")
            }

            _isCompleted.value = true
        }
    }

    /**
     * Get the refresh state for a specific location
     */
    fun getStateForLocation(locationId: String): UpdateProgressState {
        return _refreshStates.value[locationId] ?: UpdateProgressState.Checking
    }
}
