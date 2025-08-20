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

package com.farmaciasdeGuardia.ui.viewmodels

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import com.farmaciasdeGuardia.services.ClosestPharmacyResult
import com.farmaciasdeGuardia.services.ClosestPharmacyService
import com.farmaciasdeGuardia.services.LocationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Search step enumeration for progress indication
 * Equivalent to iOS ClosestPharmacyView.SearchStep
 */
enum class SearchStep {
    IDLE,
    GETTING_LOCATION,
    FINDING_PHARMACIES,
    CALCULATING_DISTANCES,
    COMPLETED;
    
    val description: String
        get() = when (this) {
            IDLE -> ""
            GETTING_LOCATION -> "Obteniendo ubicación..."
            FINDING_PHARMACIES -> "Buscando farmacias abiertas..."
            CALCULATING_DISTANCES -> "Calculando distancias..."
            COMPLETED -> "¡Encontrada!"
        }
    
    val icon: String
        get() = when (this) {
            IDLE -> "location_on"
            GETTING_LOCATION -> "my_location"
            FINDING_PHARMACIES -> "local_pharmacy"
            CALCULATING_DISTANCES -> "straighten"
            COMPLETED -> "check_circle"
        }
}

/**
 * UI state for the closest pharmacy feature
 * Equivalent to iOS ClosestPharmacyView state management
 */
data class ClosestPharmacyUiState(
    val isSearching: Boolean = false,
    val searchStep: SearchStep = SearchStep.IDLE,
    val closestPharmacy: ClosestPharmacyResult? = null,
    val showingResult: Boolean = false,
    val errorMessage: String? = null,
    val userLocation: Location? = null,
    val locationPermissionGranted: Boolean = false
)

/**
 * ViewModel for closest pharmacy functionality
 * Equivalent to iOS ClosestPharmacyView with state management
 */
@HiltViewModel
class ClosestPharmacyViewModel @Inject constructor(
    private val locationService: LocationService,
    private val closestPharmacyService: ClosestPharmacyService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ClosestPharmacyUiState())
    val uiState: StateFlow<ClosestPharmacyUiState> = _uiState.asStateFlow()
    
    init {
        // Observe location service states
        viewModelScope.launch {
            locationService.userLocation.collect { location ->
                _uiState.value = _uiState.value.copy(userLocation = location)
            }
        }
        
        viewModelScope.launch {
            locationService.authorizationStatus.collect { status ->
                val isGranted = status == com.farmaciasdeGuardia.services.LocationAuthorizationStatus.AUTHORIZED
                _uiState.value = _uiState.value.copy(locationPermissionGranted = isGranted)
            }
        }
        
        viewModelScope.launch {
            locationService.error.collect { error ->
                if (error != null && _uiState.value.isSearching) {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchStep = SearchStep.IDLE,
                        errorMessage = error.message
                    )
                }
            }
        }
    }
    
    /**
     * Start search for closest pharmacy
     * Equivalent to iOS findClosestPharmacy()
     */
    fun findClosestPharmacy() {
        DebugConfig.debugPrint("🎯 ClosestPharmacyViewModel: Starting closest pharmacy search")
        
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            isSearching = true,
            searchStep = SearchStep.GETTING_LOCATION
        )
        
        viewModelScope.launch {
            try {
                // Step 1: Get location
                val locationResult = locationService.requestLocationOnce()
                
                if (locationResult.isFailure) {
                    val error = locationResult.exceptionOrNull()
                    DebugConfig.debugPrint("❌ ClosestPharmacyViewModel: Location error: ${error?.message}")
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchStep = SearchStep.IDLE,
                        errorMessage = error?.message ?: "No se pudo obtener tu ubicación"
                    )
                    return@launch
                }
                
                val userLocation = locationResult.getOrThrow()
                DebugConfig.debugPrint("✅ ClosestPharmacyViewModel: Location obtained: ${userLocation.latitude}, ${userLocation.longitude}")
                
                // Step 2: Finding pharmacies (with minimum delay for UX)
                _uiState.value = _uiState.value.copy(searchStep = SearchStep.FINDING_PHARMACIES)
                kotlinx.coroutines.delay(500) // Minimum delay to show step
                
                // Step 3: Calculating distances
                _uiState.value = _uiState.value.copy(searchStep = SearchStep.CALCULATING_DISTANCES)
                
                // Perform the actual search
                val searchResult = closestPharmacyService.findClosestOnDutyPharmacy(userLocation)
                
                if (searchResult.isFailure) {
                    val error = searchResult.exceptionOrNull()
                    DebugConfig.debugPrint("❌ ClosestPharmacyViewModel: Search error: ${error?.message}")
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchStep = SearchStep.IDLE,
                        errorMessage = error?.message ?: "No se pudo encontrar farmacias cercanas"
                    )
                    return@launch
                }
                
                val result = searchResult.getOrThrow()
                DebugConfig.debugPrint("✅ ClosestPharmacyViewModel: Found closest pharmacy: ${result.pharmacy.name}")
                
                // Step 4: Show completion briefly
                _uiState.value = _uiState.value.copy(searchStep = SearchStep.COMPLETED)
                kotlinx.coroutines.delay(300) // Brief completion display
                
                // Show result
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchStep = SearchStep.IDLE,
                    closestPharmacy = result,
                    showingResult = true
                )
                
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ ClosestPharmacyViewModel: Unexpected error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchStep = SearchStep.IDLE,
                    errorMessage = e.message ?: "Error inesperado durante la búsqueda"
                )
            }
        }
    }
    
    /**
     * Update location permission status
     */
    fun updateLocationPermissionStatus() {
        locationService.updateAuthorizationStatus()
    }
    
    /**
     * Show the result modal
     */
    fun showResult() {
        _uiState.value = _uiState.value.copy(showingResult = true)
    }
    
    /**
     * Dismiss the result modal
     */
    fun dismissResult() {
        _uiState.value = _uiState.value.copy(showingResult = false)
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * Reset search state
     */
    fun resetSearch() {
        _uiState.value = _uiState.value.copy(
            isSearching = false,
            searchStep = SearchStep.IDLE,
            closestPharmacy = null,
            showingResult = false,
            errorMessage = null
        )
    }
    
    /**
     * Get last known location (faster but potentially stale)
     */
    fun getLastKnownLocation() {
        viewModelScope.launch {
            val result = locationService.getLastKnownLocation()
            if (result.isSuccess) {
                val location = result.getOrThrow()
                DebugConfig.debugPrint("📍 ClosestPharmacyViewModel: Last known location: ${location.latitude}, ${location.longitude}")
            }
        }
    }
}
