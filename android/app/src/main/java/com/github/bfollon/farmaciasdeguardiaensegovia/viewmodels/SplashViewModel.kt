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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.repositories.PDFURLRepository
import com.github.bfollon.farmaciasdeguardiaensegovia.repositories.PharmacyScheduleRepository
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.services.NetworkMonitor
import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFURLScrapingDemo
import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFURLScrapingService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFURLScrapingTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for managing splash screen state and background PDF loading
 * Handles preloading of Segovia Capital PDF during splash screen animations
 */
class SplashViewModel(private val context: Context) : ViewModel() {

    private val repository by lazy { PharmacyScheduleRepository.Companion.getInstance(context) }
    private val urlRepository by lazy { PDFURLRepository.getInstance(context) }

    // Loading states for each region (in sequential order)
    private val _regionLoadingStates = MutableStateFlow(
        mapOf(
            "Segovia Capital" to false,
            "Cuéllar" to false,
            "El Espinar" to false,
            "Segovia Rural" to false
        )
    )
    val regionLoadingStates: StateFlow<Map<String, Boolean>> = _regionLoadingStates.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentLoadingRegion = MutableStateFlow<String?>(null)
    val currentLoadingRegion: StateFlow<String?> = _currentLoadingRegion.asStateFlow()

    private val _scrapedPDFURLs =
        MutableStateFlow<List<PDFURLScrapingService.ScrapedPDFData>>(emptyList())
    val scrapedPDFURLs: StateFlow<List<PDFURLScrapingService.ScrapedPDFData>> =
        _scrapedPDFURLs.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    // Sequential regions to load (in order) - will be populated after scraping
    private var regionsToLoad = emptyList<Region>()

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
                    // Check network status first
                    val isOnline = NetworkMonitor.isOnline()
                    val networkState = NetworkMonitor.getNetworkStateDescription()
                    
                    if (!isOnline) {
                        DebugConfig.debugPrint("SplashViewModel: ⚠️ Device is offline ($networkState) - skipping network operations")
                        withContext(Dispatchers.Main) {
                            _isOffline.value = true
                        }
                    } else {
                        DebugConfig.debugPrint("SplashViewModel: ✅ Device is online ($networkState) - proceeding with updates")
                        withContext(Dispatchers.Main) {
                            _isOffline.value = false
                        }
                        
                        // First, initialize PDF URL repository (scrape and validate URLs)
                        initializeURLRepository()
                    }

                    // Set up the URL provider for Region objects
                    Region.setURLProvider { regionName ->
                        urlRepository.getURL(regionName)
                    }

                    // Now populate regions with validated URLs
                    regionsToLoad = listOf(
                        Region.Companion.segoviaCapital,
                        Region.Companion.cuellar,
                        Region.Companion.elEspinar,
                        Region.Companion.segoviaRural
                    )

                    // Then load regions sequentially (will use cache if offline)
                    loadRegionsSequentially()
                }
            } catch (e: Exception) {
                // Only log if not a cancellation exception (which is expected during navigation)
                if (e !is CancellationException) {
                    DebugConfig.debugError("SplashViewModel: Error during sequential loading", e)
                }
            } finally {
                // ALWAYS mark as complete, even on error - this ensures awaitLoadingCompletion() never hangs
                withContext(Dispatchers.Main) {
                    _loadingProgress.value = 1f
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Suspends until loading is complete or timeout is reached
     * Returns immediately if loading is not started or already complete
     * 
     * @return true if loading completed normally, false if timed out
     */
    suspend fun awaitLoadingCompletion(): Boolean {
        // If loading hasn't started or is already complete, return immediately
        if (!_isLoading.value) {
            DebugConfig.debugPrint("SplashViewModel: Loading already complete or not started")
            return true
        }
        
        DebugConfig.debugPrint("SplashViewModel: Waiting for loading to complete...")
        
        // Wait for isLoading to become false, with 60 second safety timeout
        val completed = withTimeoutOrNull(60000L) {
            _isLoading.first { !it }
            true
        } ?: false
        
        if (completed) {
            DebugConfig.debugPrint("SplashViewModel: Loading completed successfully")
        } else {
            DebugConfig.debugWarn("SplashViewModel: Loading timeout after 60s - proceeding anyway")
        }
        
        return completed
    }

    /**
     * Initialize PDF URL repository - scrape, validate, and persist URLs
     * This runs at startup before loading regions
     */
    private suspend fun initializeURLRepository() {
        try {
            DebugConfig.debugPrint("SplashViewModel: Initializing PDF URL repository...")

            val success = urlRepository.initializeURLs()

            if (success) {
                DebugConfig.debugPrint("SplashViewModel: ✅ PDF URL repository initialized successfully")
            } else {
                DebugConfig.debugWarn("SplashViewModel: ⚠️ PDF URL repository initialization failed, using fallbacks")
            }

        } catch (e: Exception) {
            if (e !is CancellationException) {
                DebugConfig.debugError("SplashViewModel: Error during URL repository initialization", e)
            }
        }
    }
    
    /**
     * Scrape PDF URLs from the stable cofsegovia.com page (legacy method, kept for demo)
     * This runs at startup to check for URL updates
     */
    private suspend fun scrapePDFURLs() {
        try {
            DebugConfig.debugPrint("SplashViewModel: Starting PDF URL scraping...")

            val scrapedData = PDFURLScrapingService.scrapePDFURLs()

            withContext(Dispatchers.Main) {
                _scrapedPDFURLs.value = scrapedData
            }

            // Print the scraped data to console for debugging
            PDFURLScrapingService.printScrapedData(scrapedData)

            DebugConfig.debugPrint("SplashViewModel: PDF URL scraping completed")

        } catch (e: Exception) {
            if (e !is CancellationException) {
                DebugConfig.debugError("SplashViewModel: Error during PDF URL scraping", e)
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

            // Check if already loaded AND not forcing refresh
            if (repository.isLoaded(region) && !region.forceRefresh) {
                DebugConfig.debugPrint("SplashViewModel: $regionName already loaded in cache, skipping")
            } else {
                // Load the region (actual loading for implemented regions, placeholder for others)
                val success = when (region.id) {
                    "segovia-capital" -> loadRegionPDF(region, "Segovia Capital")
                    "cuellar" -> loadRegionPDF(region, "Cuéllar")
                    "el-espinar" -> loadRegionPDF(region, "El Espinar")
                    "segovia-rural" -> loadRegionPDF(region, "Segovia Rural")
                    else -> loadPlaceholderPDF(region)
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
    private suspend fun loadRegionPDF(region: Region, regionName: String): Boolean {
        DebugConfig.debugPrint("SplashViewModel: Loading $regionName PDF...")

        // Show intermediate progress during region loading
        val regionIndex = regionsToLoad.indexOfFirst { it.id == region.id }
        val progressStart = regionIndex.toFloat() / regionsToLoad.size
        val progressMid = progressStart + (0.5f / regionsToLoad.size) // Halfway through this region

        withContext(Dispatchers.Main) {
            _loadingProgress.value = progressMid
        }

        return try {
            val schedules = repository.preloadSchedules(region)
            val scheduleList = region.toDutyLocationList()
                .fold(emptyList<PharmacySchedule>()) { acc, location ->
                    repository.getCachedSchedules(location)?.let { acc + it } ?: acc
                }

            val success = schedules && scheduleList.isNotEmpty()
            if (success) {
                DebugConfig.debugPrint("SplashViewModel: Successfully loaded $regionName schedules (${scheduleList.size} schedules)")
            } else {
                DebugConfig.debugWarn("SplashViewModel: Failed to load $regionName schedules")
            }
            success
        } catch (e: Exception) {
            if (e !is CancellationException) {
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
    fun getSegoviaCapitalSchedules(): List<PharmacySchedule>? =
        repository.getCachedSchedules(DutyLocation.Companion.fromRegion(Region.Companion.segoviaCapital))

    /**
     * Get the scraped PDF URLs from the cofsegovia.com page
     */
    fun getScrapedPDFURLs(): List<PDFURLScrapingService.ScrapedPDFData> = _scrapedPDFURLs.value

    /**
     * Run the PDF URL scraping demo for testing purposes
     */
    fun runScrapingDemo() {
        DebugConfig.debugPrint("SplashViewModel: Running PDF URL scraping demo...")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                PDFURLScrapingDemo.runDemo()
            }
        }
    }

    /**
     * Run the PDF URL scraping test with detailed HTML output
     */
    fun runScrapingTest() {
        DebugConfig.debugPrint("SplashViewModel: Running PDF URL scraping test with HTML output...")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                PDFURLScrapingTest.runTest()
            }
        }
    }

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

    /**
     * Force refresh a specific region (for testing purposes)
     */
    fun forceRefreshRegion(region: Region) {
        DebugConfig.debugPrint("SplashViewModel: Force refreshing ${region.name}...")
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Clear cache for this region first
                    repository.clearCacheForRegion(region)

                    // Force load from PDF
                    val schedules = region.toDutyLocationList().fold(emptyList<PharmacySchedule>()) { acc, location -> repository.loadSchedules(location) }
                    val success = schedules.isNotEmpty()

                    if (success) {
                        DebugConfig.debugPrint("SplashViewModel: Successfully force refreshed ${region.name} (${schedules.size} schedules)")
                    } else {
                        DebugConfig.debugWarn("SplashViewModel: Failed to force refresh ${region.name}")
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    DebugConfig.debugError(
                        "SplashViewModel: Error force refreshing ${region.name}",
                        e
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        DebugConfig.debugPrint("SplashViewModel: Cleared")
    }
}
