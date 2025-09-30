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
import com.github.bfollon.farmaciasdeguardiaensegovia.data.RegionCacheStatus
import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFCacheManager
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
    
    private val _cacheStatuses = MutableStateFlow<List<RegionCacheStatus>>(emptyList())
    val cacheStatuses: StateFlow<List<RegionCacheStatus>> = _cacheStatuses.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
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
}
