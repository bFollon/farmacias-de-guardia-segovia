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
import android.location.Location
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Pharmacy
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.data.ZBS
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.*
import kotlin.collections.map

/**
 * Result of finding the closest pharmacy
 * Equivalent to iOS ClosestPharmacyResult
 */
data class ClosestPharmacyResult(
    val pharmacy: Pharmacy,
    val distance: Double, // in meters
    val estimatedTravelTime: Double?, // in seconds
    val estimatedWalkingTime: Double?, // in seconds
    val region: Region,
    val zbs: ZBS?,
    val timeSpan: DutyTimeSpan
) {
    val formattedDistance: String
        get() = if (distance < 1000) {
            "${distance.toInt()} m"
        } else {
            String.format("%.1f km", distance / 1000)
        }
    
    val formattedTravelTime: String
        get() {
            val travelTime = estimatedTravelTime ?: return ""
            val minutes = (travelTime / 60).toInt()
            return if (minutes < 1) {
                "< 1 min"
            } else {
                "$minutes min"
            }
        }
    
    val formattedWalkingTime: String
        get() {
            val walkingTime = estimatedWalkingTime ?: return ""
            val minutes = (walkingTime / 60).toInt()
            return if (minutes < 60) {
                "$minutes min"
            } else {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes == 0) {
                    "${hours}h"
                } else {
                    "${hours}h ${remainingMinutes}m"
                }
            }
        }
    
    val regionDisplayName: String
        get() = if (zbs != null) {
            "${region.name} - ${zbs.name}"
        } else {
            region.name
        }
}

/**
 * Service for finding the closest on-duty pharmacy
 * Equivalent to iOS ClosestPharmacyService
 */
class ClosestPharmacyService(private val context: Context) {
    
    // Location-based caching (same as iOS)
    private var cachedResult: ClosestPharmacyResult? = null
    private var cachedLocation: Location? = null
    private var cachedDate: Date? = null
    
    companion object {
        private const val CACHE_DISTANCE_THRESHOLD = 500.0 // 500 meters (same as iOS)
        private const val CACHE_TIME_THRESHOLD = 30 * 60 * 1000L // 30 minutes in milliseconds (same as iOS)
    }
    
    sealed class ClosestPharmacyError : Exception() {
        object NoPharmaciesOnDuty : ClosestPharmacyError() {
            override val message: String = "No hay farmacias de guardia disponibles en este momento"
        }
        
        object GeocodingFailed : ClosestPharmacyError() {
            override val message: String = "No se pudo determinar la ubicación de las farmacias"
        }
        
        object NoLocationPermission : ClosestPharmacyError() {
            override val message: String = "Se necesita acceso a la ubicación para encontrar la farmacia más cercana"
        }
    }
    
    /**
     * Find the closest on-duty pharmacy to the user's location
     * Equivalent to iOS findClosestOnDutyPharmacy
     */
    suspend fun findClosestOnDutyPharmacy(
        userLocation: Location,
        date: Date = Date()
    ): ClosestPharmacyResult {
        
        // Check if we can use cached result first
        getCachedResultIfValid(userLocation, date)?.let { cachedResult ->
            DebugConfig.debugPrint("🚀 Using cached result - user hasn't moved significantly")
            return cachedResult
        }
        
        // Only log when we're actually doing calculations
        DebugConfig.debugPrint("🎯 OPTIMIZED search for closest on-duty pharmacy at $date")
        DebugConfig.debugPrint("📍 User location: ${userLocation.latitude}, ${userLocation.longitude}")
        
        val allOnDutyPharmacies = mutableListOf<PharmacyWithContext>()
        
        // Get all regions to search (same as iOS)
        val allRegions = listOf(Region.Companion.segoviaCapital, Region.Companion.cuellar, Region.Companion.elEspinar, Region.Companion.segoviaRural)
        
        for (region in allRegions) {
            DebugConfig.debugPrint("🔍 Searching region: ${region.name}")
            
            when (region) {
                Region.Companion.segoviaRural -> {
                    // Handle ZBS areas for Segovia Rural
                    for (zbs in ZBS.Companion.availableZBS) {
                        val dutyLocation = DutyLocation.Companion.fromZBS(zbs)
                        val onDutyPharmacies = getOnDutyPharmaciesForLocation(dutyLocation)
                        
                        for ((pharmacy, timeSpan) in onDutyPharmacies) {
                            allOnDutyPharmacies.add(
                                PharmacyWithContext(pharmacy, region, zbs, timeSpan)
                            )
                        }
                        
                        DebugConfig.debugPrint("   📋 ${zbs.name}: ${onDutyPharmacies.size} on-duty pharmacies")
                    }
                }
                else -> {
                    // Handle regular regions
                    val dutyLocation = DutyLocation.Companion.fromRegion(region)
                    val onDutyPharmacies = getOnDutyPharmaciesForLocation(dutyLocation)
                    
                    for ((pharmacy, timeSpan) in onDutyPharmacies) {
                        allOnDutyPharmacies.add(
                            PharmacyWithContext(pharmacy, region, null, timeSpan)
                        )
                    }
                    
                    DebugConfig.debugPrint("   📋 ${region.name}: ${onDutyPharmacies.size} on-duty pharmacies")
                }
            }
        }
        
        if (allOnDutyPharmacies.isEmpty()) {
            DebugConfig.debugPrint("❌ No on-duty pharmacies found across all regions")
            throw ClosestPharmacyError.NoPharmaciesOnDuty
        }
        
        DebugConfig.debugPrint("📊 Total on-duty pharmacies found: ${allOnDutyPharmacies.size}")
        
        // Geocode all pharmacy addresses concurrently
        val geocodingService = GeocodingService(context)
        val pharmacyCoordinates = mutableListOf<PharmacyWithCoordinates>()
        
        coroutineScope {
            val geocodingJobs = allOnDutyPharmacies.map { pharmacyContext ->
                async {
                    val coordinates = geocodingService.getCoordinatesForPharmacy(pharmacyContext.pharmacy)
                    if (coordinates != null) {
                        PharmacyWithCoordinates(pharmacyContext, coordinates)
                    } else {
                        null
                    }
                }
            }
            
            pharmacyCoordinates.addAll(geocodingJobs.awaitAll().filterNotNull())
        }
        
        if (pharmacyCoordinates.isEmpty()) {
            DebugConfig.debugPrint("❌ Could not geocode any pharmacy addresses")
            throw ClosestPharmacyError.GeocodingFailed
        }
        
        DebugConfig.debugPrint("🗺️ Successfully geocoded ${pharmacyCoordinates.size} pharmacies")
        
        // Calculate driving routes to all pharmacies
        val routeResults = mutableListOf<Pair<ClosestPharmacyResult, Double>>()
        
        for (pharmacyWithCoords in pharmacyCoordinates) {
            DebugConfig.debugPrint("🚗 Calculating route to: ${pharmacyWithCoords.context.pharmacy.name}")
            
            val routeResult = RoutingService.calculateDrivingRoute(userLocation, pharmacyWithCoords.coordinates)
            if (routeResult != null) {
                val result = ClosestPharmacyResult(
                    pharmacy = pharmacyWithCoords.context.pharmacy,
                    distance = routeResult.distance,
                    estimatedTravelTime = if (routeResult.travelTime > 0) routeResult.travelTime else null,
                    estimatedWalkingTime = if (routeResult.walkingTime > 0) routeResult.walkingTime else null,
                    region = pharmacyWithCoords.context.region,
                    zbs = pharmacyWithCoords.context.zbs,
                    timeSpan = pharmacyWithCoords.context.timeSpan
                )
                routeResults.add(result to routeResult.distance)
                DebugConfig.debugPrint("   🏆 ${pharmacyWithCoords.context.pharmacy.name}: ${result.formattedDistance}, 🚗${result.formattedTravelTime} 🚶${result.formattedWalkingTime} (${result.regionDisplayName})")
            } else {
                DebugConfig.debugPrint("   ❌ Could not calculate route to: ${pharmacyWithCoords.context.pharmacy.name}")
            }
        }
        
        // Sort by driving distance and log top candidates
        routeResults.sortBy { it.second }
        
        DebugConfig.debugPrint("🏆 Top 5 closest pharmacies by driving distance:")
        routeResults.take(5).forEachIndexed { index, (result, _) ->
            DebugConfig.debugPrint("   ${index + 1}. ${result.pharmacy.name}: ${result.formattedDistance}, ${result.formattedTravelTime} (${result.regionDisplayName})")
        }
        
        val closest = routeResults.firstOrNull()?.first
            ?: throw ClosestPharmacyError.GeocodingFailed
        
        DebugConfig.debugPrint("🎯 DRIVING WINNER: ${closest.pharmacy.name} at ${closest.formattedDistance}, ${closest.formattedTravelTime} from ${closest.regionDisplayName}")
        
        // Cache the result
        cacheResult(closest, userLocation, date)
        
        return closest
    }
    
    /**
     * Get cached result if still valid
     */
    private fun getCachedResultIfValid(userLocation: Location, date: Date): ClosestPharmacyResult? {
        val cached = cachedResult ?: return null
        val cachedLoc = cachedLocation ?: return null
        val cachedDt = cachedDate ?: return null
        
        // Check if location has changed significantly
        val distance = userLocation.distanceTo(cachedLoc)
        if (distance > CACHE_DISTANCE_THRESHOLD) {
            DebugConfig.debugPrint("📍 User moved ${distance.toInt()}m, cache invalid")
            
            // Clear route cache when user moves significantly
            RouteCache.clearAllRoutes()
            
            return null
        }
        
        // Check if time has passed significantly
        val timeDiff = date.time - cachedDt.time
        if (timeDiff > CACHE_TIME_THRESHOLD) {
            DebugConfig.debugPrint("⏰ Cache expired (${timeDiff / 1000}s old)")
            return null
        }
        
        return cached
    }
    
    /**
     * Cache the result for future use
     */
    private fun cacheResult(result: ClosestPharmacyResult, location: Location, date: Date) {
        cachedResult = result
        cachedLocation = location
        cachedDate = date
        DebugConfig.debugPrint("💾 Cached result for ${result.pharmacy.name}")
    }
    
    /**
     * Get on-duty pharmacies for a specific location using existing ScheduleService
     */
    private suspend fun getOnDutyPharmaciesForLocation(
        dutyLocation: DutyLocation
    ): List<Pair<Pharmacy, DutyTimeSpan>> {
        val scheduleService = ScheduleService(context)
        
        return try {
            // Use existing ScheduleService to load schedules
            val schedules = scheduleService.loadSchedules(dutyLocation)
            
            // Use existing ScheduleService to find current active schedule
            val currentScheduleInfo = scheduleService.findCurrentSchedule(schedules)
            
            if (currentScheduleInfo != null) {
                val (schedule, activeTimeSpan) = currentScheduleInfo
                // Get pharmacies for the active time span
                val pharmacies = schedule.shifts[activeTimeSpan] ?: emptyList()
                
                // Return pharmacies paired with their time span
                pharmacies.map { pharmacy -> pharmacy to activeTimeSpan }
            } else {
                DebugConfig.debugPrint("📋 No current active schedule found for ${dutyLocation.name}")
                emptyList()
            }
        } catch (exception: Exception) {
            DebugConfig.debugPrint("❌ Failed to get schedules for ${dutyLocation.name}: ${exception.message}")
            emptyList()
        }
    }
}

/**
 * Helper data classes
 */
private data class PharmacyWithContext(
    val pharmacy: Pharmacy,
    val region: Region,
    val zbs: ZBS?,
    val timeSpan: DutyTimeSpan
)

private data class PharmacyWithCoordinates(
    val context: PharmacyWithContext,
    val coordinates: Location
)
