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
import com.farmaciasdeGuardia.services.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for settings screen
 * Equivalent to iOS SettingsView and CacheStatusView state
 */
data class SettingsUiState(
    val cacheStatus: List<RegionCacheStatus> = emptyList(),
    val isRefreshing: Boolean = false,
    val showingCacheDetails: Boolean = false,
    val refreshProgress: Map<Region, UpdateProgressState> = emptyMap(),
    val error: String? = null,
    val geocodingCacheStats: GeocodingCacheStats? = null,
    val showingAbout: Boolean = false
)

/**
 * ViewModel for settings and cache management
 * Equivalent to iOS SettingsView with CacheStatusView functionality
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pdfCacheService: PDFCacheService,
    private val geocodingService: GeocodingService,
    private val scheduleService: ScheduleService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        // Load initial cache status
        loadCacheStatus()
    }
    
    /**
     * Load current cache status for all regions
     * Equivalent to iOS PDFCacheManager.getCacheStatus
     */
    fun loadCacheStatus() {
        DebugConfig.debugPrint("🔍 SettingsViewModel: Loading cache status")
        
        viewModelScope.launch {
            try {
                val cacheStatus = pdfCacheService.getCacheStatus()
                val geocodingStats = geocodingService.getCacheStats()
                
                DebugConfig.debugPrint("✅ SettingsViewModel: Cache status loaded - ${cacheStatus.size} regions")
                
                _uiState.value = _uiState.value.copy(
                    cacheStatus = cacheStatus,
                    geocodingCacheStats = geocodingStats,
                    error = null
                )
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ SettingsViewModel: Error loading cache status: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = "Error al cargar estado de caché: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Force refresh all PDF caches with progress tracking
     * Equivalent to iOS PDFCacheManager.forceCheckForUpdatesWithProgress
     */
    fun forceRefreshAllCaches() {
        DebugConfig.debugPrint("🔄 SettingsViewModel: Force refreshing all caches")
        
        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            refreshProgress = emptyMap(),
            error = null
        )
        
        viewModelScope.launch {
            try {
                pdfCacheService.forceCheckForUpdatesWithProgress().collect { (region, progress) ->
                    DebugConfig.debugPrint("📥 SettingsViewModel: Update progress for ${region.name}: $progress")
                    
                    val currentProgress = _uiState.value.refreshProgress.toMutableMap()
                    currentProgress[region] = progress
                    
                    _uiState.value = _uiState.value.copy(refreshProgress = currentProgress)
                }
                
                // Refresh complete - reload cache status
                loadCacheStatus()
                
                DebugConfig.debugPrint("✅ SettingsViewModel: Force refresh completed")
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    refreshProgress = emptyMap()
                )
                
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ SettingsViewModel: Force refresh failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    refreshProgress = emptyMap(),
                    error = "Error al actualizar caché: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Clear PDF cache for a specific region
     * Equivalent to iOS PDFCacheManager.clearCache(for:)
     */
    fun clearCacheForRegion(region: Region) {
        DebugConfig.debugPrint("🗑️ SettingsViewModel: Clearing cache for ${region.name}")
        
        viewModelScope.launch {
            try {
                pdfCacheService.clearCache(for = region)
                scheduleService.clearCache(region)
                
                // Reload cache status
                loadCacheStatus()
                
                DebugConfig.debugPrint("✅ SettingsViewModel: Cache cleared for ${region.name}")
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ SettingsViewModel: Error clearing cache for ${region.name}: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = "Error al limpiar caché para ${region.name}: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Clear all PDF caches
     * Equivalent to iOS PDFCacheManager.clearCache()
     */
    fun clearAllCaches() {
        DebugConfig.debugPrint("🗑️ SettingsViewModel: Clearing all caches")
        
        viewModelScope.launch {
            try {
                pdfCacheService.clearCache()
                scheduleService.clearCache()
                geocodingService.clearSessionCache()
                
                // Reload cache status
                loadCacheStatus()
                
                DebugConfig.debugPrint("✅ SettingsViewModel: All caches cleared")
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ SettingsViewModel: Error clearing all caches: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = "Error al limpiar todos los cachés: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Clear geocoding caches (session + persistent)
     * Equivalent to iOS GeocodingService.clearAllCaches
     */
    fun clearGeocodingCaches() {
        DebugConfig.debugPrint("🗑️ SettingsViewModel: Clearing geocoding caches")
        
        viewModelScope.launch {
            try {
                geocodingService.clearAllCaches()
                
                // Reload stats
                val geocodingStats = geocodingService.getCacheStats()
                _uiState.value = _uiState.value.copy(geocodingCacheStats = geocodingStats)
                
                DebugConfig.debugPrint("✅ SettingsViewModel: Geocoding caches cleared")
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ SettingsViewModel: Error clearing geocoding caches: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = "Error al limpiar caché de geocodificación: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Force download for a specific region (bypass cache check)
     * Equivalent to iOS PDFCacheManager.forceDownload(for:)
     */
    fun forceDownloadForRegion(region: Region) {
        DebugConfig.debugPrint("📥 SettingsViewModel: Force downloading for ${region.name}")
        
        viewModelScope.launch {
            try {
                val result = pdfCacheService.forceDownload(for = region)
                if (result.isSuccess) {
                    DebugConfig.debugPrint("✅ SettingsViewModel: Force download completed for ${region.name}")
                    
                    // Clear related caches and reload status
                    scheduleService.clearCache(region)
                    loadCacheStatus()
                } else {
                    val error = result.exceptionOrNull()
                    DebugConfig.debugPrint("❌ SettingsViewModel: Force download failed for ${region.name}: ${error?.message}")
                    _uiState.value = _uiState.value.copy(
                        error = "Error al descargar ${region.name}: ${error?.message}"
                    )
                }
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ SettingsViewModel: Force download error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = "Error al forzar descarga: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Show cache details view
     */
    fun showCacheDetails() {
        _uiState.value = _uiState.value.copy(showingCacheDetails = true)
    }
    
    /**
     * Hide cache details view
     */
    fun hideCacheDetails() {
        _uiState.value = _uiState.value.copy(showingCacheDetails = false)
    }
    
    /**
     * Show about dialog
     */
    fun showAbout() {
        _uiState.value = _uiState.value.copy(showingAbout = true)
    }
    
    /**
     * Hide about dialog
     */
    fun hideAbout() {
        _uiState.value = _uiState.value.copy(showingAbout = false)
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Get cache debug information
     * Equivalent to iOS PDFCacheManager.getCacheInfo
     */
    fun getCacheDebugInfo(): String {
        val cacheStatus = _uiState.value.cacheStatus
        val geocodingStats = _uiState.value.geocodingCacheStats
        
        return buildString {
            appendLine("PDFCache Status:")
            cacheStatus.forEach { status ->
                appendLine("📄 ${status.region.name}: ${if (status.isCached) "✅" else "❌"}")
                if (status.isCached) {
                    appendLine("   Downloaded: ${status.formattedDownloadDate}")
                    appendLine("   Size: ${status.formattedFileSize}")
                    if (status.needsUpdate) {
                        appendLine("   ⚠️ Update available")
                    }
                }
                appendLine()
            }
            
            geocodingStats?.let { stats ->
                appendLine("Geocoding Cache:")
                appendLine("Session: ${stats.sessionCacheCount} entries")
                appendLine("Persistent: ${stats.persistentCacheCount} entries (${stats.persistentCacheSize})")
            }
        }
    }
    
    /**
     * Perform maintenance cleanup
     * Equivalent to iOS GeocodingService.performMaintenanceCleanup
     */
    fun performMaintenanceCleanup() {
        DebugConfig.debugPrint("🧹 SettingsViewModel: Performing maintenance cleanup")
        
        viewModelScope.launch {
            try {
                geocodingService.performMaintenanceCleanup()
                loadCacheStatus() // Reload to show updated stats
                
                DebugConfig.debugPrint("✅ SettingsViewModel: Maintenance cleanup completed")
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ SettingsViewModel: Maintenance cleanup failed: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    error = "Error durante limpieza de mantenimiento: ${e.message}"
                )
            }
        }
    }
}
