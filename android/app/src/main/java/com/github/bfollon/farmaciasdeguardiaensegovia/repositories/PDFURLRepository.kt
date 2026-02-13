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

package com.github.bfollon.farmaciasdeguardiaensegovia.repositories

import android.content.Context
import android.content.SharedPreferences
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.services.NetworkMonitor
import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFURLScrapingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Repository for managing PDF URLs with scraping, persistence, and validation
 * 
 * Responsibilities:
 * - Scrape PDF URLs from website
 * - Persist scraped URLs to SharedPreferences
 * - Validate URLs with HEAD requests
 * - Provide self-healing URL resolution
 */
class PDFURLRepository private constructor(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    
    // OkHttp client for HEAD requests
    private val headClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    // In-memory cache for HEAD request results (until app restart)
    private val validationCache = mutableMapOf<String, URLValidationResult>()
    
    companion object {
        private const val PREFS_NAME = "pdf_url_repository"
        private const val SCRAPED_URLS_KEY = "scraped_urls"
        private const val LAST_SCRAPE_KEY = "last_scrape_timestamp"
        
        // Hardcoded fallback URLs
        private val FALLBACK_URLS = mapOf(
            "Segovia Capital" to "https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf",
            "Cuéllar" to "https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf",
            "El Espinar" to "https://cofsegovia.com/wp-content/uploads/2025/01/Guardias-EL-ESPINAR_2025.pdf",
            "Segovia Rural" to "https://cofsegovia.com/wp-content/uploads/2025/06/SERVICIOS-DE-URGENCIA-RURALES-2025.pdf"
        )
        
        /**
         * Normalize region name to lookup key
         * Handles display names vs storage keys
         */
        private fun normalizeRegionName(regionName: String): String {
            return when {
                regionName.contains("Espinar") -> "El Espinar"
                regionName.contains("Capital") -> "Segovia Capital"
                regionName.contains("Cuéllar") || regionName.contains("Cuellar") -> "Cuéllar"
                regionName.contains("Rural") -> "Segovia Rural"
                else -> regionName
            }
        }
        
        @Volatile
        private var INSTANCE: PDFURLRepository? = null
        
        fun getInstance(context: Context): PDFURLRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PDFURLRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    /**
     * Data class for persisted scraped URLs
     */
    @Serializable
    data class ScrapedURLData(
        val urls: Map<String, String>,
        val timestamp: Long
    )
    
    /**
     * Result of URL validation
     */
    sealed class URLValidationResult {
        data class Valid(val url: String) : URLValidationResult()
        data class Invalid(val url: String, val statusCode: Int) : URLValidationResult()
        data class Error(val url: String, val message: String) : URLValidationResult()
    }
    
    /**
     * Result of URL resolution with self-healing
     */
    sealed class URLResolutionResult {
        data class Success(val url: String) : URLResolutionResult()
        data class Updated(val oldUrl: String, val newUrl: String) : URLResolutionResult()
        data class Failed(val message: String) : URLResolutionResult()
    }

    /**
     * Result of URL initialization with change detection
     * Region IDs are normalized to Region.ID_* constants (e.g., "segovia-capital")
     */
    data class URLChangeResult(
        val success: Boolean,
        val changedRegionIds: List<String> = emptyList(),  // Region IDs, not display names
        val urlChanges: Map<String, URLChange> = emptyMap()  // Key = region ID
    )

    data class URLChange(
        val regionId: String,
        val oldURL: String,
        val newURL: String
    )

    // MARK: - Persistence
    
    /**
     * Load persisted scraped URLs from SharedPreferences
     */
    private fun loadPersistedURLs(): Map<String, String> {
        val json = sharedPreferences.getString(SCRAPED_URLS_KEY, null) ?: return emptyMap()

        return try {
            val data = this.json.decodeFromString<ScrapedURLData>(json)
            val stackTrace = Thread.currentThread().stackTrace
            val caller = stackTrace.getOrNull(3)?.let { "${it.className}.${it.methodName}:${it.lineNumber}" } ?: "unknown"
            DebugConfig.debugPrint("📂 PDFURLRepository: Loaded ${data.urls.size} persisted URLs (called from: $caller)")
            data.urls
        } catch (e: Exception) {
            DebugConfig.debugError("Failed to load persisted URLs", e)
            emptyMap()
        }
    }
    
    /**
     * Persist scraped URLs to SharedPreferences
     */
    private fun persistURLs(urls: Map<String, String>) {
        try {
            val data = ScrapedURLData(
                urls = urls,
                timestamp = System.currentTimeMillis()
            )
            val jsonString = json.encodeToString(data)
            
            sharedPreferences.edit()
                .putString(SCRAPED_URLS_KEY, jsonString)
                .putLong(LAST_SCRAPE_KEY, System.currentTimeMillis())
                .apply()
            
            DebugConfig.debugPrint("💾 PDFURLRepository: Persisted ${urls.size} URLs to storage")
        } catch (e: Exception) {
            DebugConfig.debugError("Failed to persist URLs", e)
        }
    }
    
    // MARK: - URL Validation
    
    /**
     * Validate a URL with a HEAD request
     * Returns validation result and caches it in memory
     */
    suspend fun validateURL(url: String, useCache: Boolean = true): URLValidationResult = withContext(Dispatchers.IO) {
        // Check in-memory cache first
        if (useCache && validationCache.containsKey(url)) {
            val cached = validationCache[url]!!
            DebugConfig.debugPrint("✅ PDFURLRepository: Using cached validation for $url")
            return@withContext cached
        }
        
        try {
            DebugConfig.debugPrint("🔍 PDFURLRepository: Validating URL with HEAD request: $url")
            
            val request = Request.Builder()
                .url(url)
                .head()
                .addHeader("User-Agent", "FarmaciasDeGuardia-Android/1.0")
                .build()
            
            val response = headClient.newCall(request).execute()
            
            val result = if (response.isSuccessful) {
                DebugConfig.debugPrint("✅ PDFURLRepository: URL is valid (${response.code})")
                URLValidationResult.Valid(url)
            } else {
                DebugConfig.debugWarn("❌ PDFURLRepository: URL returned ${response.code}")
                URLValidationResult.Invalid(url, response.code)
            }
            
            // Cache the result
            validationCache[url] = result
            result
            
        } catch (e: Exception) {
            DebugConfig.debugError("PDFURLRepository: Error validating URL", e)
            val result = URLValidationResult.Error(url, e.message ?: "Unknown error")
            validationCache[url] = result
            result
        }
    }
    
    // MARK: - Scraping
    
    /**
     * Scrape fresh URLs from website
     */
    suspend fun scrapeURLs(): Map<String, String> = withContext(Dispatchers.IO) {
        DebugConfig.debugPrint("🌐 PDFURLRepository: Starting fresh URL scraping...")
        
        val scrapedData = PDFURLScrapingService.scrapePDFURLs()
        
        if (scrapedData.isEmpty()) {
            DebugConfig.debugWarn("PDFURLRepository: Scraping returned no results")
            return@withContext emptyMap()
        }
        
        val urlMap = scrapedData.associate { it.regionName to it.pdfUrl }
        
        // Persist the scraped URLs
        persistURLs(urlMap)
        
        DebugConfig.debugPrint("✅ PDFURLRepository: Scraped and persisted ${urlMap.size} URLs")
        urlMap
    }
    
    /**
     * Validate all persisted URLs with HEAD requests
     * Returns map of valid URLs
     */
    suspend fun validatePersistedURLs(): Map<String, String> = withContext(Dispatchers.IO) {
        val persistedURLs = loadPersistedURLs()
        
        if (persistedURLs.isEmpty()) {
            DebugConfig.debugPrint("📭 PDFURLRepository: No persisted URLs to validate")
            return@withContext emptyMap()
        }
        
        DebugConfig.debugPrint("🔍 PDFURLRepository: Validating ${persistedURLs.size} persisted URLs...")
        
        val validURLs = mutableMapOf<String, String>()
        
        for ((regionName, url) in persistedURLs) {
            when (val result = validateURL(url, useCache = false)) {
                is URLValidationResult.Valid -> {
                    validURLs[regionName] = url
                    DebugConfig.debugPrint("✅ $regionName: Valid")
                }
                is URLValidationResult.Invalid -> {
                    DebugConfig.debugWarn("❌ $regionName: Invalid (${result.statusCode})")
                }
                is URLValidationResult.Error -> {
                    DebugConfig.debugWarn("⚠️ $regionName: Error (${result.message})")
                }
            }
        }
        
        DebugConfig.debugPrint("✅ PDFURLRepository: ${validURLs.size}/${persistedURLs.size} URLs are valid")
        validURLs
    }
    
    // MARK: - URL Resolution
    
    /**
     * Get the best URL for a region (persisted > fallback)
     */
    fun getURL(regionName: String): String {
        val normalizedName = normalizeRegionName(regionName)
        val persistedURLs = loadPersistedURLs()
        val url = persistedURLs[normalizedName] ?: FALLBACK_URLS[normalizedName]
        
        if (url == null) {
            DebugConfig.debugError("No URL found for region: $regionName (normalized: $normalizedName)", null)
            return ""
        }
        
        return url
    }
    
    /**
     * Resolve URL with self-healing:
     * 1. Check persisted URL is valid (HEAD request)
     * 2. If invalid (404), scrape fresh URLs
     * 3. Return new URL or fail
     */
    suspend fun resolveURLWithHealing(regionName: String): URLResolutionResult = withContext(Dispatchers.IO) {
        val normalizedName = normalizeRegionName(regionName)
        
        // Check if we're online
        if (!NetworkMonitor.isOnline()) {
            val url = getURL(normalizedName)
            DebugConfig.debugPrint("📡 PDFURLRepository: Offline, using stored URL for $normalizedName")
            return@withContext URLResolutionResult.Success(url)
        }
        
        val currentURL = getURL(normalizedName)
        
        DebugConfig.debugPrint("🔄 PDFURLRepository: Resolving URL for $normalizedName with self-healing")
        
        // Validate current URL
        when (val validation = validateURL(currentURL)) {
            is URLValidationResult.Valid -> {
                DebugConfig.debugPrint("✅ PDFURLRepository: Current URL is valid")
                return@withContext URLResolutionResult.Success(currentURL)
            }
            is URLValidationResult.Invalid -> {
                if (validation.statusCode == 404) {
                    DebugConfig.debugWarn("🔄 PDFURLRepository: URL returned 404, attempting self-healing...")
                    
                    // Scrape fresh URLs
                    val freshURLs = scrapeURLs()
                    val newURL = freshURLs[normalizedName]
                    
                    if (newURL != null && newURL != currentURL) {
                        DebugConfig.debugPrint("✅ PDFURLRepository: Found new URL: $newURL")
                        return@withContext URLResolutionResult.Updated(currentURL, newURL)
                    } else if (newURL != null) {
                        DebugConfig.debugWarn("⚠️ PDFURLRepository: Scraped URL is same as old URL")
                        return@withContext URLResolutionResult.Failed("No se puede acceder al PDF en este momento. Inténtalo más tarde.")
                    } else {
                        DebugConfig.debugError("PDFURLRepository: Could not find new URL for $normalizedName", null)
                        return@withContext URLResolutionResult.Failed("No se puede acceder al PDF en este momento. Inténtalo más tarde.")
                    }
                } else {
                    DebugConfig.debugWarn("PDFURLRepository: URL returned ${validation.statusCode}")
                    return@withContext URLResolutionResult.Failed("No se puede acceder al PDF (Error ${validation.statusCode})")
                }
            }
            is URLValidationResult.Error -> {
                DebugConfig.debugError("PDFURLRepository: Error validating URL", null)
                return@withContext URLResolutionResult.Failed("Error de red. Inténtalo más tarde.")
            }
        }
    }
    
    // MARK: - Initialization

    /**
     * Initialize repository - always scrape for fresh URLs when online
     * Detects URL changes and returns information about which regions changed
     * Called during splash screen
     */
    suspend fun initializeURLs(): URLChangeResult = withContext(Dispatchers.IO) {
        DebugConfig.debugPrint("🚀 PDFURLRepository: Initializing URLs...")

        // Check if we're online
        if (!NetworkMonitor.isOnline()) {
            DebugConfig.debugPrint("📡 PDFURLRepository: Offline, using persisted/fallback URLs")
            return@withContext URLChangeResult(success = true) // Success (will use persisted/fallback URLs)
        }

        // Load previously persisted URLs before scraping
        val oldURLs = loadPersistedURLs()
        DebugConfig.debugPrint("📂 PDFURLRepository: Loaded ${oldURLs.size} old URLs for comparison")

        // Always scrape fresh URLs when online to ensure we have the latest PDFs
        DebugConfig.debugPrint("🌐 PDFURLRepository: Scraping fresh URLs from website...")
        val scrapedURLs = scrapeURLs()

        if (scrapedURLs.isEmpty()) {
            DebugConfig.debugWarn("⚠️ PDFURLRepository: Scraping failed, falling back to persisted/cached URLs")
            return@withContext URLChangeResult(success = true) // Still return true so app continues with cached URLs
        }

        // Detect URL changes and translate to region IDs
        val changedRegionIds = mutableListOf<String>()
        val urlChanges = mutableMapOf<String, URLChange>()

        for ((displayName, newURL) in scrapedURLs) {
            val oldURL = oldURLs[displayName]

            if (oldURL != null && oldURL != newURL) {
                // URL changed - translate display name to region ID
                val regionId = Region.repositoryNameToId(displayName)

                if (regionId != null) {
                    DebugConfig.debugPrint("🔄 PDFURLRepository: URL changed for $displayName (regionId: $regionId)")
                    DebugConfig.debugPrint("   📄 Old: ${oldURL.substringAfterLast("/")}")
                    DebugConfig.debugPrint("   📄 New: ${newURL.substringAfterLast("/")}")

                    changedRegionIds.add(regionId)
                    urlChanges[regionId] = URLChange(
                        regionId = regionId,
                        oldURL = oldURL,
                        newURL = newURL
                    )
                } else {
                    DebugConfig.debugWarn("⚠️ PDFURLRepository: Could not map display name '$displayName' to region ID")
                }
            }
        }

        if (changedRegionIds.isNotEmpty()) {
            DebugConfig.debugPrint("✅ PDFURLRepository: Detected ${changedRegionIds.size} URL changes: $changedRegionIds")
        } else {
            DebugConfig.debugPrint("✅ PDFURLRepository: No URL changes detected")
        }

        return@withContext URLChangeResult(
            success = true,
            changedRegionIds = changedRegionIds,
            urlChanges = urlChanges
        )
    }
    
    // MARK: - Utilities
    
    /**
     * Clear all cached data (for debugging)
     */
    fun clearCache() {
        sharedPreferences.edit().clear().apply()
        validationCache.clear()
        DebugConfig.debugPrint("🗑️ PDFURLRepository: Cleared all cached data")
    }
    
    /**
     * Get repository status for debugging
     */
    fun getStatus(): String {
        val persistedURLs = loadPersistedURLs()
        val lastScrape = sharedPreferences.getLong(LAST_SCRAPE_KEY, 0L)
        val lastScrapeDate = if (lastScrape > 0) {
            java.text.SimpleDateFormat("dd/MM/yy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(lastScrape))
        } else {
            "Never"
        }
        
        return buildString {
            appendLine("PDFURLRepository Status:")
            appendLine("Persisted URLs: ${persistedURLs.size}")
            appendLine("Last scrape: $lastScrapeDate")
            appendLine("Validation cache: ${validationCache.size} entries")
            appendLine()
            persistedURLs.forEach { (region, url) ->
                appendLine("$region: ${url.substringAfterLast("/")}")
            }
        }
    }
}

