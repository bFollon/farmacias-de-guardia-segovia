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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Location permission and status states
 * Equivalent to iOS CLAuthorizationStatus
 */
enum class LocationAuthorizationStatus {
    NOT_DETERMINED,
    DENIED_ONCE,
    DENIED_PERMANENTLY,
    AUTHORIZED
}

/**
 * Location error types
 * Equivalent to iOS LocationError
 */
sealed class LocationError : Exception() {
    object PermissionDenied : LocationError() {
        override val message = "Location permission denied"
    }
    
    object PermissionDeniedPermanently : LocationError() {
        override val message = "Location permission denied permanently"
    }
    
    object LocationServicesDisabled : LocationError() {
        override val message = "Location services disabled"
    }
    
    object Timeout : LocationError() {
        override val message = "Location request timed out"
    }
    
    data class Failed(override val message: String) : LocationError()
}

/**
 * Android equivalent of iOS LocationManager
 * Manages location permissions and provides current location functionality
 */
@Singleton
class LocationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()
    
    private val _authorizationStatus = MutableStateFlow(getAuthorizationStatus())
    val authorizationStatus: StateFlow<LocationAuthorizationStatus> = _authorizationStatus.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<LocationError?>(null)
    val error: StateFlow<LocationError?> = _error.asStateFlow()
    
    companion object {
        private const val LOCATION_TIMEOUT_MS = 10_000L // 10 seconds
        private const val LOCATION_UPDATE_INTERVAL = 10_000L // 10 seconds
        private const val LOCATION_FASTEST_INTERVAL = 5_000L // 5 seconds
    }
    
    /**
     * Get current authorization status based on permissions
     */
    private fun getAuthorizationStatus(): LocationAuthorizationStatus {
        return when {
            hasLocationPermissions() -> LocationAuthorizationStatus.AUTHORIZED
            // Note: Android doesn't distinguish between "denied once" and "denied permanently"
            // in the same way iOS does. This would need to be tracked separately if needed.
            else -> LocationAuthorizationStatus.DENIED_ONCE
        }
    }
    
    /**
     * Check if app has location permissions
     */
    fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Update authorization status (call this after permission requests)
     */
    fun updateAuthorizationStatus() {
        _authorizationStatus.value = getAuthorizationStatus()
    }
    
    /**
     * Request location once (equivalent to iOS requestLocationOnce)
     */
    suspend fun requestLocationOnce(): Result<Location> {
        _error.value = null
        
        if (!hasLocationPermissions()) {
            val error = LocationError.PermissionDenied
            _error.value = error
            return Result.failure(error)
        }
        
        _isLoading.value = true
        
        return try {
            val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                getCurrentLocationInternal()
            }
            
            _isLoading.value = false
            
            if (location != null) {
                _userLocation.value = location
                DebugConfig.debugPrint("📍 Location obtained: ${location.latitude}, ${location.longitude}")
                Result.success(location)
            } else {
                val error = LocationError.Timeout
                _error.value = error
                Result.failure(error)
            }
        } catch (e: Exception) {
            _isLoading.value = false
            val error = LocationError.Failed(e.message ?: "Unknown location error")
            _error.value = error
            DebugConfig.debugPrint("❌ Location error: ${error.message}")
            Result.failure(error)
        }
    }
    
    /**
     * Internal method to get current location using suspending coroutines
     */
    private suspend fun getCurrentLocationInternal(): Location? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermissions()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        
        val locationRequest = createLocationRequest()
        val cancellationToken = CancellationTokenSource()
        
        // Set up cancellation
        continuation.invokeOnCancellation {
            cancellationToken.cancel()
        }
        
        try {
            fusedLocationClient.getCurrentLocation(
                LocationRequest.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            }.addOnFailureListener { exception ->
                if (continuation.isActive) {
                    DebugConfig.debugPrint("❌ LocationService: Failed to get location: ${exception.message}")
                    continuation.resume(null)
                }
            }
        } catch (e: SecurityException) {
            if (continuation.isActive) {
                DebugConfig.debugPrint("❌ LocationService: Security exception: ${e.message}")
                continuation.resume(null)
            }
        }
    }
    
    /**
     * Create location request with appropriate settings
     */
    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
            .setMaxUpdateDelayMillis(LOCATION_TIMEOUT_MS)
            .build()
    }
    
    /**
     * Get last known location (faster but potentially stale)
     */
    suspend fun getLastKnownLocation(): Result<Location> {
        if (!hasLocationPermissions()) {
            val error = LocationError.PermissionDenied
            return Result.failure(error)
        }
        
        return try {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        if (location != null) {
                            _userLocation.value = location
                            DebugConfig.debugPrint("📍 Last known location: ${location.latitude}, ${location.longitude}")
                            continuation.resume(Result.success(location))
                        } else {
                            DebugConfig.debugPrint("❌ No last known location available")
                            continuation.resume(Result.failure(LocationError.Failed("No last known location")))
                        }
                    }
                }.addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        val error = LocationError.Failed(exception.message ?: "Failed to get last location")
                        DebugConfig.debugPrint("❌ Failed to get last known location: ${error.message}")
                        continuation.resume(Result.failure(error))
                    }
                }
            }
        } catch (e: SecurityException) {
            val error = LocationError.PermissionDenied
            DebugConfig.debugPrint("❌ Permission denied for last known location: ${e.message}")
            Result.failure(error)
        }
    }
    
    /**
     * Start continuous location updates (for background tracking if needed)
     */
    fun startLocationUpdates(callback: LocationCallback) {
        if (!hasLocationPermissions()) {
            DebugConfig.debugPrint("❌ LocationService: No permission for location updates")
            return
        }
        
        val locationRequest = createLocationRequest()
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
            DebugConfig.debugPrint("🔄 LocationService: Started location updates")
        } catch (e: SecurityException) {
            DebugConfig.debugPrint("❌ LocationService: Security exception starting location updates: ${e.message}")
        }
    }
    
    /**
     * Stop continuous location updates
     */
    fun stopLocationUpdates(callback: LocationCallback) {
        fusedLocationClient.removeLocationUpdates(callback)
        DebugConfig.debugPrint("⏹️ LocationService: Stopped location updates")
    }
    
    /**
     * Clear current error state
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Clear current location
     */
    fun clearLocation() {
        _userLocation.value = null
    }
    
    /**
     * Check if location services are enabled on the device
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }
}
