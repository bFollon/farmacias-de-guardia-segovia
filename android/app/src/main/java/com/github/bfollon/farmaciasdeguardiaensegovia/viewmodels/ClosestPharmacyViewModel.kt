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

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ClosestPharmacyResult
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ClosestPharmacyService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.services.LocationManager
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Search steps matching iOS implementation
 */
enum class SearchStep {
    Idle,
    GettingLocation,
    FindingPharmacies,
    CalculatingDistances,
    Completed
}

/**
 * UI state for the closest pharmacy search
 */
data class ClosestPharmacyUiState(
    val isSearching: Boolean = false,
    val searchStep: SearchStep = SearchStep.Idle,
    val result: ClosestPharmacyResult? = null,
    val errorMessage: String? = null,
    val showingResult: Boolean = false,
    val retryCount: Int = 0
)

/**
 * ViewModel for managing closest pharmacy search state
 * Equivalent to iOS ClosestPharmacyView state management
 */
class ClosestPharmacyViewModel(private val context: Context) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ClosestPharmacyUiState())
    val uiState: StateFlow<ClosestPharmacyUiState> = _uiState.asStateFlow()
    
    private val locationManager = LocationManager(context)
    private val closestPharmacyService = ClosestPharmacyService(context)
    
    private val maxRetries = 3
    
    /**
     * Start the search for the closest pharmacy
     * Equivalent to iOS findClosestPharmacy()
     */
    fun findClosestPharmacy() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            isSearching = true,
            searchStep = SearchStep.GettingLocation,
            retryCount = 0,
            showingResult = false
        )
        
        // Request location - equivalent to iOS locationManager.requestLocationOnce()
        requestLocation()
    }
    
    /**
     * Request user location
     */
    private fun requestLocation() {
        viewModelScope.launch {
            try {
                DebugConfig.debugPrint("📍 Requesting user location")
                val location = locationManager.requestLocationOnce()
                DebugConfig.debugPrint("📍 Location received, starting search")
                searchForClosestPharmacy(location)
            } catch (error: LocationManager.LocationError) {
                DebugConfig.debugPrint("❌ Location error received: ${error.message}")
                handleLocationError(error)
            }
        }
    }
    
    /**
     * Search for closest pharmacy with the obtained location
     * Equivalent to iOS searchForClosestPharmacy(location:)
     */
    private suspend fun searchForClosestPharmacy(location: Location) {
        _uiState.value = _uiState.value.copy(searchStep = SearchStep.FindingPharmacies)
        
        try {
            // Add minimum delay to show the "finding pharmacies" step (same as iOS)
            val searchDeferred = viewModelScope.async {
                closestPharmacyService.findClosestOnDutyPharmacy(location)
            }
            
            // Ensure minimum 500ms for finding pharmacies step (same as iOS)
            delay(500)
            
            // Step 3: Calculating distances
            _uiState.value = _uiState.value.copy(searchStep = SearchStep.CalculatingDistances)
            
            // Wait for search to complete
            val searchResult = searchDeferred.await()
            
            // Add a brief delay to show calculating step (same as iOS)
            delay(300)
            
            // Step 4: Completed
            _uiState.value = _uiState.value.copy(searchStep = SearchStep.Completed)
            
            // Brief pause to show completion (same as iOS)
            delay(200)
            
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                searchStep = SearchStep.Idle,
                result = searchResult,
                showingResult = true
            )
            
        } catch (error: Exception) {
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                searchStep = SearchStep.Idle,
                errorMessage = error.message ?: "Error desconocido"
            )
        }
    }
    
    /**
     * Handle location errors with retry logic (same as iOS)
     */
    private fun handleLocationError(error: LocationManager.LocationError) {
        val currentState = _uiState.value
        
        // Check if we should retry
        if (currentState.retryCount < maxRetries) {
            val newRetryCount = currentState.retryCount + 1
            val delay = newRetryCount * 1000L // Exponential backoff: 1s, 2s, 3s
            
            DebugConfig.debugPrint("🔄 Location error, retrying in ${delay/1000}s (attempt $newRetryCount/$maxRetries): ${error.message}")
            
            _uiState.value = currentState.copy(retryCount = newRetryCount)
            
            viewModelScope.launch {
                delay(delay)
                if (_uiState.value.isSearching) { // Only retry if still searching
                    requestLocation()
                }
            }
        } else {
            // Max retries reached
            DebugConfig.debugPrint("❌ Max retries reached for location request")
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                searchStep = SearchStep.Idle,
                errorMessage = error.message
            )
        }
    }
    
    /**
     * Dismiss the result screen
     */
    fun dismissResult() {
        _uiState.value = _uiState.value.copy(
            showingResult = false,
            result = null
        )
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * Check if location permission is granted
     */
    fun hasLocationPermission(): Boolean {
        return locationManager.hasLocationPermission()
    }
    
    /**
     * Get search step display text (matching iOS)
     */
    fun getSearchStepText(step: SearchStep): String {
        return when (step) {
            SearchStep.Idle -> ""
            SearchStep.GettingLocation -> "Obteniendo ubicación..."
            SearchStep.FindingPharmacies -> "Buscando farmacias de guardia..."
            SearchStep.CalculatingDistances -> "Calculando distancias..."
            SearchStep.Completed -> "¡Completado!"
        }
    }
}
