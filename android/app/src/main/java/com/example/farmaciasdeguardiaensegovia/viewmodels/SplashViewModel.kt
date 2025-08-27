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
    
    // Loading states for each region
    private val _segoviaCapitalLoaded = MutableStateFlow(false)
    val segoviaCapitalLoaded: StateFlow<Boolean> = _segoviaCapitalLoaded.asStateFlow()
    
    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * Start background loading of Segovia Capital PDF
     * This will be called when splash screen starts
     */
    fun startBackgroundLoading() {
        if (_isLoading.value) {
            DebugConfig.debugPrint("SplashViewModel: Loading already in progress, ignoring request")
            return
        }
        
        DebugConfig.debugPrint("SplashViewModel: Starting background PDF preloading for Segovia Capital")
        _isLoading.value = true
        _loadingProgress.value = 0f
        
        viewModelScope.launch {
            try {
                // Move all repository work to IO dispatcher
                withContext(Dispatchers.IO) {
                    // Check if already loaded (in background thread)
                    if (repository.isLoaded(Region.segoviaCapital)) {
                        DebugConfig.debugPrint("SplashViewModel: Segovia Capital already loaded, skipping")
                        withContext(Dispatchers.Main) {
                            _segoviaCapitalLoaded.value = true
                            _loadingProgress.value = 1f
                            _isLoading.value = false
                        }
                        return@withContext
                    }
                    
                    preloadSegoviaCapitalPDF()
                }
            } catch (e: Exception) {
                DebugConfig.debugError("SplashViewModel: Error during background loading", e)
                // Still mark as complete to not block the UI
                _loadingProgress.value = 1f
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Preload the Segovia Capital PDF using the shared repository
     */
    private suspend fun preloadSegoviaCapitalPDF() = withContext(Dispatchers.IO) {
        DebugConfig.debugPrint("SplashViewModel: Preloading Segovia Capital PDF...")
        
        // Update progress to 10% - starting download
        withContext(Dispatchers.Main) { _loadingProgress.value = 0.1f }
        
        try {
            val region = Region.segoviaCapital
            
            // Start preloading via repository
            DebugConfig.debugPrint("SplashViewModel: Preloading via repository...")
            withContext(Dispatchers.Main) { _loadingProgress.value = 0.3f }
            
            val success = repository.preloadSchedules(region)
            
            withContext(Dispatchers.Main) {
                if (success) {
                    _segoviaCapitalLoaded.value = true
                    _loadingProgress.value = 1f
                    DebugConfig.debugPrint("SplashViewModel: Successfully preloaded Segovia Capital schedules")
                } else {
                    DebugConfig.debugWarn("SplashViewModel: Failed to preload Segovia Capital schedules")
                    _loadingProgress.value = 1f
                }
            }
            
        } catch (e: Exception) {
            DebugConfig.debugError("SplashViewModel: Error preloading Segovia Capital PDF", e)
            withContext(Dispatchers.Main) { _loadingProgress.value = 1f }
        } finally {
            withContext(Dispatchers.Main) { _isLoading.value = false }
        }
    }
    
    /**
     * Check if Segovia Capital data is already loaded
     * This is safe to call from main thread as it only checks in-memory cache
     */
    fun isSegoviaCapitalLoaded(): Boolean = try {
        repository.isLoaded(Region.segoviaCapital)
    } catch (e: Exception) {
        DebugConfig.debugError("Error checking if Segovia Capital is loaded", e)
        false
    }
    
    /**
     * Get the loaded Segovia Capital schedules (if any)
     * This can be used by other parts of the app to access pre-loaded data
     */
    fun getSegoviaCapitalSchedules(): List<PharmacySchedule>? = repository.getCachedSchedules(Region.segoviaCapital)
    
    /**
     * Reset loading state (for testing or if user wants to refresh)
     */
    fun resetLoadingState() {
        _isLoading.value = false
        _loadingProgress.value = 0f
        _segoviaCapitalLoaded.value = false
    }
    
    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String = repository.getCacheStats()
    
    override fun onCleared() {
        super.onCleared()
        DebugConfig.debugPrint("SplashViewModel: Cleared")
    }
}
