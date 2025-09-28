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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * LocationManager handles user location requests using Google Play Services Location API
 * Equivalent to iOS LocationManager with CoreLocation
 */
class LocationManager(private val context: Context) {
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    sealed class LocationError : Exception() {
        object PermissionDenied : LocationError() {
            override val message: String = "Se necesita acceso a la ubicación para encontrar la farmacia más cercana"
        }
        
        object LocationUnavailable : LocationError() {
            override val message: String = "No se pudo obtener la ubicación actual"
        }
        
        object Timeout : LocationError() {
            override val message: String = "Tiempo de espera agotado al obtener la ubicación"
        }
        
        data class Unknown(override val message: String) : LocationError()
    }
    
    /**
     * Check if location permissions are granted
     */
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Request current location once (equivalent to iOS requestLocationOnce)
     * Uses high accuracy with timeout
     */
    suspend fun requestLocationOnce(): Location {
        if (!hasLocationPermission()) {
            DebugConfig.debugPrint("❌ Location permission not granted")
            throw LocationError.PermissionDenied
        }
        
        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            
            // Configure location request for high accuracy
            val locationRequest = CurrentLocationRequest.Builder()
                .setDurationMillis(30000) // 30 second timeout
                .setMaxUpdateAgeMillis(60000) // Accept locations up to 1 minute old
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()
            
            DebugConfig.debugPrint("📍 Requesting current location with high accuracy")
            
            try {
                fusedLocationClient.getCurrentLocation(
                    locationRequest,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        DebugConfig.debugPrint("✅ Location obtained: ${location.latitude}, ${location.longitude}")
                        continuation.resume(location)
                    } else {
                        DebugConfig.debugPrint("❌ Location is null")
                        continuation.resumeWithException(LocationError.LocationUnavailable)
                    }
                }.addOnFailureListener { exception ->
                    DebugConfig.debugPrint("❌ Location request failed: ${exception.message}")
                    continuation.resumeWithException(
                        LocationError.Unknown(exception.message ?: "Unknown location error")
                    )
                }
            } catch (securityException: SecurityException) {
                DebugConfig.debugPrint("❌ Security exception: ${securityException.message}")
                continuation.resumeWithException(LocationError.PermissionDenied)
            }
            
            // Handle cancellation
            continuation.invokeOnCancellation {
                DebugConfig.debugPrint("🚫 Location request cancelled")
                cancellationTokenSource.cancel()
            }
        }
    }
    
    /**
     * Get last known location (faster but potentially less accurate)
     */
    suspend fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) {
            return null
        }
        
        return suspendCancellableCoroutine { continuation ->
            try {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            DebugConfig.debugPrint("📍 Last known location: ${location.latitude}, ${location.longitude}")
                        } else {
                            DebugConfig.debugPrint("📍 No last known location available")
                        }
                        continuation.resume(location)
                    }
                    .addOnFailureListener { exception ->
                        DebugConfig.debugPrint("❌ Failed to get last known location: ${exception.message}")
                        continuation.resume(null)
                    }
            } catch (securityException: SecurityException) {
                DebugConfig.debugPrint("❌ Security exception getting last location: ${securityException.message}")
                continuation.resume(null)
            }
        }
    }
    
    /**
     * Calculate distance between two locations in meters
     */
    fun calculateDistance(from: Location, to: Location): Float {
        return from.distanceTo(to)
    }
    
    companion object {
        /**
         * Check if location services are enabled on the device
         */
        fun isLocationEnabled(context: Context): Boolean {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                   locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        }
    }
}
