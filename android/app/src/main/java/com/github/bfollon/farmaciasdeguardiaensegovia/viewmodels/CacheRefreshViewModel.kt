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
import com.github.bfollon.farmaciasdeguardiaensegovia.data.UpdateProgressState
import com.github.bfollon.farmaciasdeguardiaensegovia.services.NetworkMonitor
import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for CacheRefreshScreen
 * Manages refresh progress and state for all regions
 */
class CacheRefreshViewModel(application: Application) : AndroidViewModel(application) {
    
    private val pdfCacheManager = PDFCacheManager.getInstance(application)
    
    private val _refreshStates = MutableStateFlow<Map<String, UpdateProgressState>>(emptyMap())
    val refreshStates: StateFlow<Map<String, UpdateProgressState>> = _refreshStates.asStateFlow()
    
    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()
    
    private val _wasOffline = MutableStateFlow(false)
    val wasOffline: StateFlow<Boolean> = _wasOffline.asStateFlow()
    
    private val regions = listOf(
        Region.segoviaCapital,
        Region.cuellar,
        Region.elEspinar,
        Region.segoviaRural
    )
    
    init {
        startRefresh()
    }
    
    /**
     * Start the refresh process for all regions
     */
    private fun startRefresh() {
        viewModelScope.launch {
            // Check network status first
            val isOnline = NetworkMonitor.isOnline()
            
            if (!isOnline) {
                // Offline: Set all regions to error state immediately
                val offlineStates = regions.associate { 
                    it.id to UpdateProgressState.Error("Sin conexión a Internet")
                }
                _refreshStates.value = offlineStates
                _wasOffline.value = true
                _isCompleted.value = true
                return@launch
            }
            
            // Online: Initialize all regions as pending
            val pendingStates = regions.associate { it.id to UpdateProgressState.Checking }
            _refreshStates.value = pendingStates
            
            // Use PDFCacheManager's progress-based update check
            pdfCacheManager.forceCheckForUpdatesWithProgress { region, state ->
                // Update the state for this region
                val currentStates = _refreshStates.value.toMutableMap()
                currentStates[region.id] = state
                _refreshStates.value = currentStates
            }
            
            // Mark as completed
            _isCompleted.value = true
        }
    }
    
    /**
     * Get the refresh state for a specific region
     */
    fun getStateForRegion(regionId: String): UpdateProgressState {
        return _refreshStates.value[regionId] ?: UpdateProgressState.Checking
    }
}
