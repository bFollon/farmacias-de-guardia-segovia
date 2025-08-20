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

package com.farmaciasdeGuardia.ui.state

import com.bfollon.farmaciasdeGuardia.data.model.Region
import com.farmaciasdeGuardia.services.ClosestPharmacyResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global application state management
 * Handles state that needs to persist across different screens/ViewModels
 */
@Singleton
class AppStateManager @Inject constructor() {
    
    // Current selected region
    private val _currentRegion = MutableStateFlow<Region?>(null)
    val currentRegion: StateFlow<Region?> = _currentRegion.asStateFlow()
    
    // Last found closest pharmacy result (for quick access)
    private val _lastClosestPharmacy = MutableStateFlow<ClosestPharmacyResult?>(null)
    val lastClosestPharmacy: StateFlow<ClosestPharmacyResult?> = _lastClosestPharmacy.asStateFlow()
    
    // App initialization state
    private val _isAppInitialized = MutableStateFlow(false)
    val isAppInitialized: StateFlow<Boolean> = _isAppInitialized.asStateFlow()
    
    // Global loading state (for splash screen, etc.)
    private val _isGloballyLoading = MutableStateFlow(false)
    val isGloballyLoading: StateFlow<Boolean> = _isGloballyLoading.asStateFlow()
    
    // Network connectivity state
    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()
    
    /**
     * Update current region
     */
    fun setCurrentRegion(region: Region?) {
        _currentRegion.value = region
    }
    
    /**
     * Update last closest pharmacy result
     */
    fun setLastClosestPharmacy(result: ClosestPharmacyResult?) {
        _lastClosestPharmacy.value = result
    }
    
    /**
     * Mark app as initialized
     */
    fun setAppInitialized(initialized: Boolean) {
        _isAppInitialized.value = initialized
    }
    
    /**
     * Set global loading state
     */
    fun setGloballyLoading(loading: Boolean) {
        _isGloballyLoading.value = loading
    }
    
    /**
     * Update network availability
     */
    fun setNetworkAvailable(available: Boolean) {
        _isNetworkAvailable.value = available
    }
    
    /**
     * Clear all state (for logout/reset scenarios)
     */
    fun clearAllState() {
        _currentRegion.value = null
        _lastClosestPharmacy.value = null
        _isAppInitialized.value = false
        _isGloballyLoading.value = false
        _isNetworkAvailable.value = true
    }
}

/**
 * UI state for common elements across the app
 */
data class CommonUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val showingError: Boolean = false,
    val isNetworkAvailable: Boolean = true
)

/**
 * Base ViewModel for common functionality
 */
abstract class BaseViewModel : androidx.lifecycle.ViewModel() {
    
    protected val _commonUiState = MutableStateFlow(CommonUiState())
    val commonUiState: StateFlow<CommonUiState> = _commonUiState.asStateFlow()
    
    /**
     * Set loading state
     */
    protected fun setLoading(loading: Boolean) {
        _commonUiState.value = _commonUiState.value.copy(isLoading = loading)
    }
    
    /**
     * Set error state
     */
    protected fun setError(error: String?) {
        _commonUiState.value = _commonUiState.value.copy(
            error = error,
            showingError = error != null
        )
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _commonUiState.value = _commonUiState.value.copy(
            error = null,
            showingError = false
        )
    }
    
    /**
     * Set network availability
     */
    protected fun setNetworkAvailable(available: Boolean) {
        _commonUiState.value = _commonUiState.value.copy(isNetworkAvailable = available)
    }
}

/**
 * Navigation state management
 */
sealed class NavigationDestination {
    object Home : NavigationDestination()
    data class Schedule(val region: Region) : NavigationDestination()
    data class ZBSSchedule(val region: Region) : NavigationDestination()
    object Settings : NavigationDestination()
    object About : NavigationDestination()
    object ClosestPharmacy : NavigationDestination()
}

/**
 * Navigation state manager
 */
@Singleton
class NavigationStateManager @Inject constructor() {
    
    private val _currentDestination = MutableStateFlow<NavigationDestination>(NavigationDestination.Home)
    val currentDestination: StateFlow<NavigationDestination> = _currentDestination.asStateFlow()
    
    private val _navigationHistory = MutableStateFlow<List<NavigationDestination>>(listOf(NavigationDestination.Home))
    val navigationHistory: StateFlow<List<NavigationDestination>> = _navigationHistory.asStateFlow()
    
    /**
     * Navigate to destination
     */
    fun navigateTo(destination: NavigationDestination) {
        val currentHistory = _navigationHistory.value.toMutableList()
        currentHistory.add(destination)
        
        _navigationHistory.value = currentHistory
        _currentDestination.value = destination
    }
    
    /**
     * Navigate back
     */
    fun navigateBack(): Boolean {
        val currentHistory = _navigationHistory.value.toMutableList()
        
        if (currentHistory.size > 1) {
            currentHistory.removeAt(currentHistory.size - 1)
            _navigationHistory.value = currentHistory
            _currentDestination.value = currentHistory.last()
            return true
        }
        
        return false // Can't go back further
    }
    
    /**
     * Clear navigation history and go to home
     */
    fun navigateToHome() {
        _navigationHistory.value = listOf(NavigationDestination.Home)
        _currentDestination.value = NavigationDestination.Home
    }
    
    /**
     * Replace current destination (without adding to history)
     */
    fun replaceCurrent(destination: NavigationDestination) {
        val currentHistory = _navigationHistory.value.toMutableList()
        if (currentHistory.isNotEmpty()) {
            currentHistory[currentHistory.size - 1] = destination
        } else {
            currentHistory.add(destination)
        }
        
        _navigationHistory.value = currentHistory
        _currentDestination.value = destination
    }
}
