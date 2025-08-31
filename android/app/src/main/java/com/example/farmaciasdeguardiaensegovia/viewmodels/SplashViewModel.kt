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

package com.example.farmaciasdeguardiaensegovia.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.example.farmaciasdeguardiaensegovia.data.Region
import com.example.farmaciasdeguardiaensegovia.repositories.PharmacyScheduleRepository
import com.example.farmaciasdeguardiaensegovia.services.DebugConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing splash screen state and background PDF loading
 * Handles preloading of Segovia Capital PDF during splash screen animations
 */
class SplashViewModel(private val context: Context) : ViewModel() {
    
    private val repository by lazy { PharmacyScheduleRepository.getInstance(context) }
    
    // Loading states for each region (in sequential order)
    private val _regionLoadingStates = MutableStateFlow(mapOf(
        "Segovia Capital" to false,
        "Cuéllar" to false,
        "El Espinar" to false,
        "Segovia Rural" to false
    ))
    val regionLoadingStates: StateFlow<Map<String, Boolean>> = _regionLoadingStates.asStateFlow()
    
    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _currentLoadingRegion = MutableStateFlow<String?>(null)
    val currentLoadingRegion: StateFlow<String?> = _currentLoadingRegion.asStateFlow()
    
    // Sequential regions to load (in order)
    private val regionsToLoad = listOf(
        Region.segoviaCapital,
        Region.cuellar,
        Region.elEspinar,
        Region.segoviaRural
    )
    
    /**
     * Start sequential background loading of all regions
     * This will load PDFs one by one to avoid memory issues
     */
    fun startBackgroundLoading() {
        if (_isLoading.value) {
            DebugConfig.debugPrint("SplashViewModel: Loading already in progress, ignoring request")
            return
        }
        
        DebugConfig.debugPrint("SplashViewModel: Starting sequential PDF preloading")
        _isLoading.value = true
        _loadingProgress.value = 0f
        
        viewModelScope.launch {
            try {
                // Move all repository work to IO dispatcher
                withContext(Dispatchers.IO) {
                    loadRegionsSequentially()
                }
            } catch (e: Exception) {
                // Only log if not a cancellation exception (which is expected during navigation)
                if (e !is kotlinx.coroutines.CancellationException) {
                    DebugConfig.debugError("SplashViewModel: Error during sequential loading", e)
                }
                // Still mark as complete to not block the UI
                withContext(Dispatchers.Main) {
                    _loadingProgress.value = 1f
                    _isLoading.value = false
                }
            }
        }
    }
    
    /**
     * Load regions sequentially, updating progress and states as we go
     */
    private suspend fun loadRegionsSequentially() {
        val totalRegions = regionsToLoad.size
        
        regionsToLoad.forEachIndexed { index, region ->
            val regionName = region.name
            val progressStart = index.toFloat() / totalRegions
            val progressEnd = (index + 1).toFloat() / totalRegions
            
            DebugConfig.debugPrint("SplashViewModel: Loading region ${index + 1}/$totalRegions: $regionName")
            
            withContext(Dispatchers.Main) {
                _currentLoadingRegion.value = regionName
                _loadingProgress.value = progressStart
            }
            
            // Check if already loaded (but force refresh El Espinar for testing)
            val shouldForceRefreshElEspinar = region.id == "el-espinar"
            if (repository.isLoaded(region) && !shouldForceRefreshElEspinar) {
                DebugConfig.debugPrint("SplashViewModel: $regionName already loaded, skipping actual loading")
                // No artificial delay needed - let cached data load at natural speed
            } else {
                // Load the region (actual loading for implemented regions, placeholder for others)
                val success = when (region.id) {
                    "segovia-capital" -> loadRegionPDF(region, "Segovia Capital", forceRefresh = false)
                    "cuellar" -> loadRegionPDF(region, "Cuéllar", forceRefresh = false)
                    "el-espinar" -> loadRegionPDF(region, "El Espinar", forceRefresh = true) // Force refresh to test new parsing logic
                    else -> loadPlaceholderPDF(region) // Only Segovia Rural not implemented yet
                }
                
                if (!success) {
                    DebugConfig.debugWarn("SplashViewModel: Failed to load $regionName")
                }
            }
            
            // Mark region as completed and update progress
            withContext(Dispatchers.Main) {
                val updatedStates = _regionLoadingStates.value.toMutableMap()
                updatedStates[regionName] = true
                _regionLoadingStates.value = updatedStates
                
                _loadingProgress.value = progressEnd
                DebugConfig.debugPrint("SplashViewModel: Completed $regionName (${((progressEnd * 100).toInt())}%)")
            }
            
            // Small delay between regions for visual effect
            delay(200)
        }
        
        withContext(Dispatchers.Main) {
            _currentLoadingRegion.value = null
            _isLoading.value = false
            DebugConfig.debugPrint("SplashViewModel: All regions loaded sequentially!")
        }
    }
    
    /**
     * Load a region's PDF (actual implementation)
     */
    private suspend fun loadRegionPDF(region: Region, regionName: String, forceRefresh: Boolean = false): Boolean {
        DebugConfig.debugPrint("SplashViewModel: Loading $regionName PDF (forceRefresh: $forceRefresh)...")
        
        // Clear cache if force refreshing
        if (forceRefresh) {
            DebugConfig.debugPrint("SplashViewModel: Clearing cache for $regionName...")
            repository.clearCacheForRegion(region)
        }
        
        // Show intermediate progress during region loading
        val regionIndex = regionsToLoad.indexOfFirst { it.id == region.id }
        val progressStart = regionIndex.toFloat() / regionsToLoad.size
        val progressMid = progressStart + (0.5f / regionsToLoad.size) // Halfway through this region
        
        withContext(Dispatchers.Main) {
            _loadingProgress.value = progressMid
        }
        
        return try {
            val schedules = if (forceRefresh) {
                DebugConfig.debugPrint("SplashViewModel: Force loading schedules for $regionName...")
                repository.loadSchedules(region, forceRefresh = true)
            } else {
                repository.preloadSchedules(region)
                repository.getCachedSchedules(region) ?: emptyList()
            }
            
            val success = schedules.isNotEmpty()
            if (success) {
                DebugConfig.debugPrint("SplashViewModel: Successfully loaded $regionName schedules (${schedules.size} schedules)")
            } else {
                DebugConfig.debugWarn("SplashViewModel: Failed to load $regionName schedules")
            }
            success
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                DebugConfig.debugError("SplashViewModel: Error loading $regionName PDF", e)
            }
            false
        }
    }
    
    /**
     * Placeholder for future PDF loading (instantly completes for now)
     */
    private suspend fun loadPlaceholderPDF(region: Region): Boolean {
        DebugConfig.debugPrint("SplashViewModel: Placeholder loading for ${region.name} (quick completion)")
        
        // Simulate a tiny bit of work to show the sequential nature
        delay(100) // Brief delay to show sequential loading visually
        
        // For now, just return true (successful placeholder)
        // In the future, this will be replaced with actual PDF loading
        return true
    }
    
    /**
     * Check if a specific region is loaded
     */
    fun isRegionLoaded(regionName: String): Boolean = _regionLoadingStates.value[regionName] == true
    
    /**
     * Check if Segovia Capital data is already loaded (for backward compatibility)
     */
    fun isSegoviaCapitalLoaded(): Boolean = isRegionLoaded("Segovia Capital")
    
    /**
     * Get the loaded Segovia Capital schedules (if any)
     */
    fun getSegoviaCapitalSchedules(): List<PharmacySchedule>? = repository.getCachedSchedules(Region.segoviaCapital)
    
    /**
     * Reset loading state (for testing or if user wants to refresh)
     */
    fun resetLoadingState() {
        _isLoading.value = false
        _loadingProgress.value = 0f
        _currentLoadingRegion.value = null
        _regionLoadingStates.value = mapOf(
            "Segovia Capital" to false,
            "Cuéllar" to false,
            "El Espinar" to false,
            "Segovia Rural" to false
        )
    }
    
    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String = repository.getCacheStats()
    
    /**
     * Clear all caches for debugging (force fresh parsing)
     */
    fun clearAllCaches() {
        DebugConfig.debugPrint("SplashViewModel: Manually clearing all caches...")
        viewModelScope.launch {
            repository.clearAllCache()
            DebugConfig.debugPrint("SplashViewModel: All caches cleared!")
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        DebugConfig.debugPrint("SplashViewModel: Cleared")
    }
}
