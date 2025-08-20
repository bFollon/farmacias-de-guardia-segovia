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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfollon.farmaciasdeGuardia.data.model.Region
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import com.farmaciasdeGuardia.services.PDFCacheService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the main content screen
 * Equivalent to iOS ContentView state management
 */
data class MainContentUiState(
    val selectedRegion: Region? = null,
    val showingZBSSelection: Boolean = false,
    val showingSettings: Boolean = false,
    val showingAbout: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Button data for region selection
 * Equivalent to iOS ButtonData
 */
data class RegionButtonData(
    val title: String,
    val emoji: String,
    val region: Region?
) {
    companion object {
        val allButtons = listOf(
            RegionButtonData("Segovia Capital", "🏙️", Region.SEGOVIA_CAPITAL),
            RegionButtonData("Cuéllar", "🌳", Region.CUELLAR),
            RegionButtonData("El espinar / San Rafael", "⛰️", Region.EL_ESPINAR),
            RegionButtonData("Segovia Rural", "🚜", null) // null indicates ZBS selection needed
        )
    }
}

/**
 * Main content ViewModel
 * Equivalent to iOS ContentView with state management
 */
@HiltViewModel
class MainContentViewModel @Inject constructor(
    private val pdfCacheService: PDFCacheService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainContentUiState())
    val uiState: StateFlow<MainContentUiState> = _uiState.asStateFlow()
    
    init {
        // Initialize PDF cache service on app start
        viewModelScope.launch {
            try {
                pdfCacheService.initialize()
                // Perform background update check
                pdfCacheService.checkForUpdatesIfNeeded()
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ MainContentViewModel: Error initializing PDF cache: ${e.message}")
            }
        }
    }
    
    /**
     * Handle region button selection
     */
    fun onRegionButtonClicked(buttonData: RegionButtonData) {
        DebugConfig.debugPrint("🎯 MainContentViewModel: Region button clicked: ${buttonData.title}")
        
        if (buttonData.region != null) {
            // Direct region selection
            _uiState.value = _uiState.value.copy(selectedRegion = buttonData.region)
        } else {
            // Rural region - show ZBS selection
            _uiState.value = _uiState.value.copy(showingZBSSelection = true)
        }
    }
    
    /**
     * Show settings screen
     */
    fun showSettings() {
        _uiState.value = _uiState.value.copy(showingSettings = true)
    }
    
    /**
     * Show about screen
     */
    fun showAbout() {
        _uiState.value = _uiState.value.copy(showingAbout = true)
    }
    
    /**
     * Dismiss all sheets/modals
     */
    fun dismissAll() {
        _uiState.value = _uiState.value.copy(
            selectedRegion = null,
            showingZBSSelection = false,
            showingSettings = false,
            showingAbout = false
        )
    }
    
    /**
     * Dismiss specific modal
     */
    fun dismissRegionSelection() {
        _uiState.value = _uiState.value.copy(selectedRegion = null)
    }
    
    fun dismissZBSSelection() {
        _uiState.value = _uiState.value.copy(showingZBSSelection = false)
    }
    
    fun dismissSettings() {
        _uiState.value = _uiState.value.copy(showingSettings = false)
    }
    
    fun dismissAbout() {
        _uiState.value = _uiState.value.copy(showingAbout = false)
    }
    
    /**
     * Handle ZBS region selection result
     */
    fun onZBSRegionSelected(region: Region?) {
        _uiState.value = _uiState.value.copy(
            showingZBSSelection = false,
            selectedRegion = region
        )
    }
    
    /**
     * Clear any error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Force refresh PDF cache (for settings/debug)
     */
    fun forceRefreshCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                pdfCacheService.forceCheckForUpdates()
                DebugConfig.debugPrint("✅ MainContentViewModel: Force cache refresh completed")
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ MainContentViewModel: Force cache refresh failed: ${e.message}")
                _uiState.value = _uiState.value.copy(error = "Error al actualizar caché: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
