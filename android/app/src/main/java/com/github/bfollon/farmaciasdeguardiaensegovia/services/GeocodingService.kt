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

package com.github.bfollon.farmaciasdeguardiaensegovia.services

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Pharmacy
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * GeocodingService handles address to coordinate conversion
 * Equivalent to iOS GeocodingService with CLGeocoder
 */
class GeocodingService(private val context: Context) {
    
    private val geocoder = Geocoder(context, Locale.getDefault())
    
    // Session cache for faster repeated lookups (same as iOS)
    private val sessionCache = mutableMapOf<String, Location>()
    
    /**
     * Get coordinates for an address with region context
     * Equivalent to iOS getCoordinates(for:region:)
     */
    suspend fun getCoordinates(address: String, region: String = "Segovia, España"): Location? {
        val cacheKey = "$address, $region"
        
        // Check session cache first (fastest)
        sessionCache[cacheKey]?.let { cachedLocation ->
            DebugConfig.debugPrint("📍 Using session cache for: $address")
            return cachedLocation
        }
        
        // Check persistent cache
        CoordinateCache.getCoordinates(cacheKey)?.let { persistentLocation ->
            DebugConfig.debugPrint("📍 Using persistent cache for: $address")
            sessionCache[cacheKey] = persistentLocation // Also cache in session
            return persistentLocation
        }
        
        val fullAddress = "$address, $region"
        
        return withContext(Dispatchers.IO) {
            try {
                DebugConfig.debugPrint("🔍 Geocoding address: $fullAddress")
                
                @Suppress("DEPRECATION")
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use new API for Android 13+
                    geocoder.getFromLocationName(fullAddress, 1)
                } else {
                    // Use deprecated API for older versions
                    geocoder.getFromLocationName(fullAddress, 1)
                }
                
                val address = addresses?.firstOrNull()
                if (address != null) {
                    val location = Location("geocoder").apply {
                        latitude = address.latitude
                        longitude = address.longitude
                    }
                    
                    // Cache in both session and persistent storage
                    sessionCache[cacheKey] = location
                    CoordinateCache.setCoordinates(location, cacheKey)
                    
                    DebugConfig.debugPrint("✅ Geocoded $address -> ${location.latitude}, ${location.longitude}")
                    location
                } else {
                    DebugConfig.debugPrint("❌ No coordinates found for: $fullAddress")
                    null
                }
            } catch (exception: Exception) {
                DebugConfig.debugPrint("❌ Geocoding failed for $fullAddress: ${exception.message}")
                null
            }
        }
    }
    
    /**
     * Enhanced geocoding for pharmacies that includes the pharmacy name for better accuracy
     * Equivalent to iOS getCoordinatesForPharmacy
     */
    suspend fun getCoordinatesForPharmacy(pharmacy: Pharmacy): Location? {
        // Start performance span
        val span = TelemetryService.startSpan("pharmacy.geocode", SpanKind.CLIENT)
        span.setAttribute("pharmacy_name", pharmacy.name)
        span.setAttribute("address", pharmacy.address)

        val enhancedQuery = "${pharmacy.name}, ${pharmacy.address}, Segovia, España"
        val cacheKey = enhancedQuery

        // Check session cache first (fastest)
        sessionCache[cacheKey]?.let { cachedLocation ->
            DebugConfig.debugPrint("📍 Using session cache for pharmacy: ${pharmacy.name}")
            span.setAttribute("source", "session_cache")
            span.setAttribute("cache_hit", true)
            span.setStatus(StatusCode.OK)
            span.end()
            return cachedLocation
        }

        // Check persistent cache
        CoordinateCache.getCoordinates(cacheKey)?.let { persistentLocation ->
            DebugConfig.debugPrint("📍 Using persistent cache for pharmacy: ${pharmacy.name}")
            sessionCache[cacheKey] = persistentLocation // Also cache in session
            span.setAttribute("source", "persistent_cache")
            span.setAttribute("cache_hit", true)
            span.setStatus(StatusCode.OK)
            span.end()
            return persistentLocation
        }

        return withContext(Dispatchers.IO) {
            try {
                DebugConfig.debugPrint("🔍 Geocoding pharmacy: $enhancedQuery")

                @Suppress("DEPRECATION")
                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocationName(enhancedQuery, 1)
                } else {
                    geocoder.getFromLocationName(enhancedQuery, 1)
                }

                val address = addresses?.firstOrNull()
                if (address != null) {
                    val location = Location("geocoder").apply {
                        latitude = address.latitude
                        longitude = address.longitude
                    }

                    // Cache in both session and persistent storage
                    sessionCache[cacheKey] = location
                    CoordinateCache.setCoordinates(location, cacheKey)

                    DebugConfig.debugPrint("✅ Geocoded pharmacy ${pharmacy.name} -> ${location.latitude}, ${location.longitude}")
                    span.setAttribute("source", "geocoder")
                    span.setAttribute("cache_hit", false)
                    span.setAttribute("used_fallback", false)
                    span.setStatus(StatusCode.OK)
                    span.end()
                    location
                } else {
                    DebugConfig.debugPrint("❌ No coordinates found for pharmacy: $enhancedQuery")

                    // Fallback to address-only geocoding
                    DebugConfig.debugPrint("🔄 Trying fallback geocoding with address only for: ${pharmacy.name}")
                    val fallbackResult = getCoordinates(pharmacy.address)
                    if (fallbackResult != null) {
                        DebugConfig.debugPrint("✅ Fallback geocoding succeeded for: ${pharmacy.name}")
                        span.setAttribute("source", "geocoder_fallback")
                        span.setAttribute("cache_hit", false)
                        span.setAttribute("used_fallback", true)
                        span.setStatus(StatusCode.OK)
                        span.end()
                    } else {
                        DebugConfig.debugPrint("❌ Fallback geocoding also failed for: ${pharmacy.name}")
                        span.setAttribute("error_message", "No coordinates found")
                        span.setAttribute("source", "failed")
                        span.setAttribute("cache_hit", false)
                        span.setAttribute("error.type", "not_found")
                        span.setStatus(StatusCode.ERROR, "No coordinates found")
                        span.end()
                    }
                    fallbackResult
                }
            } catch (exception: Exception) {
                DebugConfig.debugPrint("❌ Pharmacy geocoding failed for $enhancedQuery: ${exception.message}")

                // Fallback to address-only geocoding
                DebugConfig.debugPrint("🔄 Trying fallback geocoding with address only for: ${pharmacy.name}")
                val fallbackResult = getCoordinates(pharmacy.address)
                if (fallbackResult != null) {
                    DebugConfig.debugPrint("✅ Fallback geocoding succeeded for: ${pharmacy.name}")
                    span.setAttribute("source", "geocoder_fallback")
                    span.setAttribute("cache_hit", false)
                    span.setAttribute("used_fallback", true)
                    span.setAttribute("initial_error", exception.message ?: "Unknown error")
                    span.setStatus(StatusCode.OK)
                    span.end()
                } else {
                    DebugConfig.debugPrint("❌ Fallback geocoding also failed for: ${pharmacy.name}")
                    span.setAttribute("error_message", exception.message ?: "Unknown error")
                    span.setAttribute("source", "failed")
                    span.setAttribute("cache_hit", false)
                    span.setAttribute("error.type", "unavailable")
                    span.setStatus(StatusCode.ERROR, exception.message ?: "Unknown error")
                    span.end()
                }
                fallbackResult
            }
        }
    }
    
    /**
     * Clear session cache (useful for memory management)
     */
    fun clearSessionCache() {
        sessionCache.clear()
        DebugConfig.debugPrint("🧹 Cleared geocoding session cache")
    }
    
    /**
     * Get session cache statistics
     */
    fun getSessionCacheStats(): Pair<Int, String> {
        val count = sessionCache.size
        val sizeEstimate = count * 100 // Rough estimate: 100 bytes per entry
        val sizeString = when {
            sizeEstimate < 1024 -> "${sizeEstimate}B"
            sizeEstimate < 1024 * 1024 -> "${sizeEstimate / 1024}KB"
            else -> "${sizeEstimate / (1024 * 1024)}MB"
        }
        return count to sizeString
    }
}
