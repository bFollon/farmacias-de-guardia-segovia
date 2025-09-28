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

package com.example.farmaciasdeguardiaensegovia.services

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * Represents the result of a route calculation
 * Equivalent to iOS RouteResult
 */
data class RouteResult(
    val distance: Double, // in meters
    val travelTime: Double, // in seconds
    val walkingTime: Double, // in seconds
    val isEstimated: Boolean
) {
    val formattedDistance: String
        get() = if (distance < 1000) {
            "${distance.toInt()} m"
        } else {
            String.format("%.1f km", distance / 1000)
        }
    
    val formattedTravelTime: String
        get() = if (isEstimated || travelTime <= 0) {
            ""
        } else {
            val minutes = (travelTime / 60).toInt()
            if (minutes < 60) {
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
    
    val formattedWalkingTime: String
        get() = if (isEstimated || walkingTime <= 0) {
            ""
        } else {
            val minutes = (walkingTime / 60).toInt()
            if (minutes < 60) {
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
}

/**
 * RoutingService handles route calculations using OSRM with fallback to straight-line estimation
 * Equivalent to iOS RoutingService with MKDirections
 */
object RoutingService {
    
    private const val OSRM_BASE_URL = "http://router.project-osrm.org/route/v1"
    private const val DRIVING_PROFILE = "driving"
    private const val WALKING_PROFILE = "foot"
    
    // Fallback estimation constants
    private const val AVERAGE_DRIVING_SPEED_KMH = 40.0 // km/h for urban areas
    private const val AVERAGE_WALKING_SPEED_KMH = 5.0 // km/h
    private const val ROUTE_FACTOR = 1.3 // Factor to account for non-straight routes
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Calculate driving route from user location to destination
     * Equivalent to iOS calculateDrivingRoute
     */
    suspend fun calculateDrivingRoute(from: Location, to: Location): RouteResult? {
        DebugConfig.debugPrint("🗺️ Calculating routes from ${from.latitude},${from.longitude} to ${to.latitude},${to.longitude}")
        
        return try {
            // Try OSRM first for both driving and walking routes
            val drivingRoute = calculateOSRMRoute(from, to, DRIVING_PROFILE)
            val walkingRoute = calculateOSRMRoute(from, to, WALKING_PROFILE)
            
            if (drivingRoute != null && walkingRoute != null) {
                val result = RouteResult(
                    distance = drivingRoute.distance,
                    travelTime = drivingRoute.duration,
                    walkingTime = walkingRoute.duration,
                    isEstimated = false
                )
                DebugConfig.debugPrint("🚗 Driving: ${result.formattedDistance}, ${result.formattedTravelTime}")
                DebugConfig.debugPrint("🚶 Walking: ${result.formattedWalkingTime}")
                result
            } else {
                // Fallback to straight-line estimation
                DebugConfig.debugPrint("🔄 OSRM failed, falling back to straight-line estimation")
                calculateStraightLineEstimation(from, to)
            }
        } catch (exception: Exception) {
            DebugConfig.debugPrint("❌ Route calculation failed: ${exception.message}")
            // Fall back to straight-line distance if routing fails
            calculateStraightLineEstimation(from, to)
        }
    }
    
    /**
     * Calculate driving routes to multiple destinations concurrently
     * Equivalent to iOS calculateDrivingRoutes
     */
    suspend fun calculateDrivingRoutes(from: Location, to: List<Location>): List<RouteResult?> {
        return withContext(Dispatchers.IO) {
            to.map { destination ->
                calculateDrivingRoute(from, destination)
            }
        }
    }
    
    /**
     * Calculate route using OSRM API
     */
    private suspend fun calculateOSRMRoute(from: Location, to: Location, profile: String): OSRMRoute? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$OSRM_BASE_URL/$profile/${from.longitude},${from.latitude};${to.longitude},${to.latitude}?overview=false&alternatives=false&steps=false"
                
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val osrmResponse = json.decodeFromString<OSRMResponse>(responseBody)
                        if (osrmResponse.code == "Ok" && osrmResponse.routes.isNotEmpty()) {
                            osrmResponse.routes.first()
                        } else {
                            DebugConfig.debugPrint("❌ OSRM returned no routes or error: ${osrmResponse.code}")
                            null
                        }
                    } else {
                        DebugConfig.debugPrint("❌ OSRM response body is null")
                        null
                    }
                } else {
                    DebugConfig.debugPrint("❌ OSRM request failed: ${response.code}")
                    null
                }
            } catch (exception: Exception) {
                DebugConfig.debugPrint("❌ OSRM request exception: ${exception.message}")
                null
            }
        }
    }
    
    /**
     * Calculate straight-line estimation as fallback
     * Same logic as iOS fallback
     */
    private fun calculateStraightLineEstimation(from: Location, to: Location): RouteResult {
        val straightLineDistance = calculateHaversineDistance(from, to)
        val routeDistance = straightLineDistance * ROUTE_FACTOR
        
        val drivingTime = (routeDistance / 1000) / AVERAGE_DRIVING_SPEED_KMH * 3600 // seconds
        val walkingTime = (routeDistance / 1000) / AVERAGE_WALKING_SPEED_KMH * 3600 // seconds
        
        DebugConfig.debugPrint("📏 Falling back to straight-line distance: ${String.format("%.1f km", straightLineDistance / 1000)}")
        
        return RouteResult(
            distance = straightLineDistance,
            travelTime = drivingTime,
            walkingTime = walkingTime,
            isEstimated = true
        )
    }
    
    /**
     * Calculate haversine distance between two locations
     */
    private fun calculateHaversineDistance(from: Location, to: Location): Double {
        val earthRadius = 6371000.0 // Earth's radius in meters
        
        val lat1Rad = Math.toRadians(from.latitude)
        val lat2Rad = Math.toRadians(to.latitude)
        val deltaLatRad = Math.toRadians(to.latitude - from.latitude)
        val deltaLonRad = Math.toRadians(to.longitude - from.longitude)
        
        val a = sin(deltaLatRad / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(deltaLonRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
}

/**
 * OSRM API response data classes
 */
@Serializable
private data class OSRMResponse(
    val code: String,
    val routes: List<OSRMRoute>
)

@Serializable
private data class OSRMRoute(
    val distance: Double, // in meters
    val duration: Double  // in seconds
)
