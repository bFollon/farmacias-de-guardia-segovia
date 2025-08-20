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

package com.farmaciasdeGuardia.services

import android.location.Location
import com.bfollon.farmaciasdeGuardia.data.model.*
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import java.text.DateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result data for closest pharmacy search
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
        get() = zbs?.let { "${region.name} - ${it.name}" } ?: region.name
}

/**
 * Exceptions for closest pharmacy operations
 * Equivalent to iOS ClosestPharmacyError
 */
sealed class ClosestPharmacyException(message: String) : Exception(message) {
    object NoPharmaciesOnDuty : ClosestPharmacyException("No hay farmacias de guardia disponibles en este momento")
    object GeocodingFailed : ClosestPharmacyException("No se pudo determinar la ubicación de las farmacias")
    object NoLocationPermission : ClosestPharmacyException("Se necesita acceso a la ubicación para encontrar la farmacia más cercana")
}

/**
 * Android equivalent of iOS ClosestPharmacyService
 * Finds the closest on-duty pharmacy to the user's location
 */
@Singleton
class ClosestPharmacyService @Inject constructor(
    private val scheduleService: ScheduleService,
    private val zbsScheduleService: ZBSScheduleService, // TODO: Will be implemented later
    private val geocodingService: GeocodingService,
    private val routingService: RoutingService // TODO: Will be implemented later
) {
    
    /**
     * Optimized version that leverages existing logic to find only currently on-duty pharmacies
     * Equivalent to iOS findClosestOnDutyPharmacy
     */
    suspend fun findClosestOnDutyPharmacy(
        userLocation: Location,
        date: Date = Date()
    ): Result<ClosestPharmacyResult> {
        
        DebugConfig.debugPrint("🎯 OPTIMIZED search for closest on-duty pharmacy at $date")
        DebugConfig.debugPrint("📍 User location: ${userLocation.latitude}, ${userLocation.longitude}")
        
        val allOnDutyPharmacies = mutableListOf<OnDutyPharmacyInfo>()
        
        // Get all regions to search (same as existing logic)
        val allRegions = listOf(
            Region.SEGOVIA_CAPITAL,
            Region.CUELLAR,
            Region.EL_ESPINAR,
            Region.SEGOVIA_RURAL
        )
        
        for (region in allRegions) {
            DebugConfig.debugPrint("🌍 Checking region: ${region.name}")
            
            if (region == Region.SEGOVIA_RURAL) {
                // Handle rural areas using ZBS logic
                DebugConfig.debugPrint("🏘️ Processing rural ZBS areas")
                
                // TODO: Implement ZBS schedule handling
                // This will be similar to iOS ZBSScheduleView logic
                DebugConfig.debugPrint("⚠️ ZBS schedule processing not yet implemented")
                DebugConfig.debugPrint("⚠️ This will be implemented with ZBSScheduleService")
            } else {
                // Handle regular regions using ScheduleService logic
                val schedules = scheduleService.loadSchedules(region)
                DebugConfig.debugPrint("📋 Loaded ${schedules.size} schedules for ${region.name}")
                
                // Use the existing ScheduleService.findCurrentSchedule logic
                val currentScheduleResult = scheduleService.findCurrentSchedule(schedules, region)
                if (currentScheduleResult != null) {
                    val (currentSchedule, timeSpan) = currentScheduleResult
                    val formatter = DateFormat.getDateInstance(DateFormat.SHORT)
                    val scheduleTimestamp = currentSchedule.date.toTimestamp()
                    val scheduleDate = if (scheduleTimestamp != null) {
                        Date((scheduleTimestamp * 1000).toLong())
                    } else Date()
                    
                    DebugConfig.debugPrint("⏰ Found current schedule: ${formatter.format(scheduleDate)}, timespan: $timeSpan")
                    
                    // OPTIMIZATION: Check if timespan is active BEFORE getting pharmacy list
                    if (timeSpan.contains(date)) {
                        DebugConfig.debugPrint("✅ Timespan $timeSpan is active now - getting pharmacies")
                        
                        // Get on-duty pharmacies for this timespan (only if active)
                        val onDutyPharmacies = currentSchedule.shifts[timeSpan]
                        if (onDutyPharmacies != null) {
                            DebugConfig.debugPrint("💊 Found ${onDutyPharmacies.size} pharmacies on duty")
                            
                            // Filter by actual operating hours before geocoding
                            var actuallyOpenPharmacies = 0
                            for (pharmacy in onDutyPharmacies) {
                                // For regular regions, the timespan already represents the operating hours
                                if (timeSpan.contains(date)) {
                                    allOnDutyPharmacies.add(
                                        OnDutyPharmacyInfo(pharmacy, region, null, timeSpan)
                                    )
                                    actuallyOpenPharmacies++
                                    DebugConfig.debugPrint("   ✅ ${pharmacy.name} - OPEN NOW (${timeSpan.displayName})")
                                } else {
                                    DebugConfig.debugPrint("   ❌ ${pharmacy.name} - CLOSED (${timeSpan.displayName})")
                                }
                            }
                            DebugConfig.debugPrint("   📊 $actuallyOpenPharmacies/${onDutyPharmacies.size} are actually open")
                        } else {
                            DebugConfig.debugPrint("❌ No pharmacies found for active timespan $timeSpan")
                        }
                    } else {
                        DebugConfig.debugPrint("❌ Timespan $timeSpan is NOT active now - skipping geocoding")
                    }
                } else {
                    DebugConfig.debugPrint("❌ No current schedule found for ${region.name}")
                }
            }
        }
        
        if (allOnDutyPharmacies.isEmpty()) {
            DebugConfig.debugPrint("❌ No pharmacies on duty found")
            return Result.failure(ClosestPharmacyException.NoPharmaciesOnDuty)
        }
        
        DebugConfig.debugPrint("✅ Found ${allOnDutyPharmacies.size} pharmacies that are actually OPEN right now")
        DebugConfig.debugPrint("🚀 REAL OPTIMIZATION: Only calculating routes for ${allOnDutyPharmacies.size} open pharmacies")
        
        // Get coordinates for all pharmacies first
        val pharmacyCoordinates = mutableListOf<PharmacyWithCoordinates>()
        
        for (info in allOnDutyPharmacies) {
            DebugConfig.debugPrint("📍 Getting coordinates for: ${info.pharmacy.name}")
            
            val coordinates = geocodingService.getCoordinatesForPharmacy(info.pharmacy)
            if (coordinates != null) {
                pharmacyCoordinates.add(
                    PharmacyWithCoordinates(info.pharmacy, info.region, info.zbs, info.timeSpan, coordinates)
                )
                DebugConfig.debugPrint("   ✅ Coordinates obtained for ${info.pharmacy.name}")
            } else {
                DebugConfig.debugPrint("   ❌ Could not get coordinates for: ${info.pharmacy.name}")
            }
        }
        
        DebugConfig.debugPrint("🗺️ Calculating driving routes to ${pharmacyCoordinates.size} pharmacies...")
        
        // Calculate driving routes to all pharmacies
        val routeResults = mutableListOf<Pair<ClosestPharmacyResult, Double>>()
        
        for (pharmacyInfo in pharmacyCoordinates) {
            DebugConfig.debugPrint("🚗 Calculating route to: ${pharmacyInfo.pharmacy.name}")
            
            // TODO: Implement actual routing service call
            // For now, use simple distance calculation as placeholder
            val distance = userLocation.distanceTo(pharmacyInfo.coordinates)
            val estimatedTravelTime = estimateCarTravelTime(distance)
            val estimatedWalkingTime = estimateWalkingTime(distance)
            
            val result = ClosestPharmacyResult(
                pharmacy = pharmacyInfo.pharmacy,
                distance = distance.toDouble(),
                estimatedTravelTime = estimatedTravelTime,
                estimatedWalkingTime = estimatedWalkingTime,
                region = pharmacyInfo.region,
                zbs = pharmacyInfo.zbs,
                timeSpan = pharmacyInfo.timeSpan
            )
            
            routeResults.add(result to distance.toDouble())
            DebugConfig.debugPrint("   🏆 ${pharmacyInfo.pharmacy.name}: ${result.formattedDistance}, 🚗${result.formattedTravelTime} 🚶${result.formattedWalkingTime} (${result.regionDisplayName})")
        }
        
        // Sort by driving distance and log top candidates
        routeResults.sortBy { it.second }
        
        DebugConfig.debugPrint("🏆 Top 5 closest pharmacies by driving distance:")
        routeResults.take(5).forEachIndexed { index, (result, _) ->
            DebugConfig.debugPrint("   ${index + 1}. ${result.pharmacy.name}: ${result.formattedDistance}, ${result.formattedTravelTime} (${result.regionDisplayName})")
        }
        
        val closest = routeResults.firstOrNull()
        if (closest == null) {
            DebugConfig.debugPrint("❌ Could not calculate routes to any pharmacies")
            return Result.failure(ClosestPharmacyException.GeocodingFailed)
        }
        
        DebugConfig.debugPrint("🎯 DRIVING WINNER: ${closest.first.pharmacy.name} at ${closest.first.formattedDistance}, ${closest.first.formattedTravelTime} from ${closest.first.regionDisplayName}")
        return Result.success(closest.first)
    }
    
    /**
     * Simple estimation of car travel time based on distance
     * This is a placeholder until proper routing is implemented
     */
    private fun estimateCarTravelTime(distanceMeters: Float): Double {
        // Assume average speed of 50 km/h in urban areas
        val speedKmh = 50.0
        val distanceKm = distanceMeters / 1000.0
        val timeHours = distanceKm / speedKmh
        return timeHours * 3600 // Convert to seconds
    }
    
    /**
     * Simple estimation of walking time based on distance
     * This is a placeholder until proper routing is implemented
     */
    private fun estimateWalkingTime(distanceMeters: Float): Double {
        // Assume average walking speed of 5 km/h
        val speedKmh = 5.0
        val distanceKm = distanceMeters / 1000.0
        val timeHours = distanceKm / speedKmh
        return timeHours * 3600 // Convert to seconds
    }
    
    /**
     * Get the appropriate DutyTimeSpan for a pharmacy based on its operating hours
     */
    private fun getPharmacyTimeSpan(pharmacy: Pharmacy): DutyTimeSpan {
        val info = pharmacy.additionalInfo ?: ""
        
        // Check operating hours based on additional info and return proper DutyTimeSpan
        return when {
            info.contains("24h") -> DutyTimeSpan.FULL_DAY // 24h pharmacies use full day span
            info.contains("10h-22h") -> DutyTimeSpan.RURAL_EXTENDED_DAYTIME // Extended hours (10:00 - 22:00)
            info.contains("10h-20h") -> DutyTimeSpan.RURAL_DAYTIME // Standard hours (10:00 - 20:00)
            else -> DutyTimeSpan.FULL_DAY // Default assumption for pharmacies without specific hours info
        }
    }
}

/**
 * Internal data classes for processing
 */
private data class OnDutyPharmacyInfo(
    val pharmacy: Pharmacy,
    val region: Region,
    val zbs: ZBS?,
    val timeSpan: DutyTimeSpan
)

private data class PharmacyWithCoordinates(
    val pharmacy: Pharmacy,
    val region: Region,
    val zbs: ZBS?,
    val timeSpan: DutyTimeSpan,
    val coordinates: Location
)
