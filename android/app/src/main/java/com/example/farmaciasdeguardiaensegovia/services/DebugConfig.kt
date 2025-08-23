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

import android.util.Log

/**
 * Global debug configuration for the entire Android application
 * Android equivalent of iOS DebugConfig
 */
object DebugConfig {
    
    private const val TAG = "FarmaciasDeGuardia"
    
    /**
     * Default debug setting when no environment variable is set
     * Change this value to enable/disable debug logging by default
     */
    private const val DEFAULT_DEBUG_ENABLED = true // Enable by default for development
    
    /**
     * Master debug flag - controls all debug output in the application
     * TODO: Can be extended to read from BuildConfig.DEBUG or system properties
     */
    var isDebugEnabled: Boolean = DEFAULT_DEBUG_ENABLED
    
    /**
     * Conditional debug print - only prints when debug is enabled
     * Uses Android Log.d() for debug level logging
     * @param message The message to print
     */
    fun debugPrint(message: String) {
        if (isDebugEnabled) {
            Log.d(TAG, "[DEBUG] $message")
            // Also print to console for immediate visibility
            println("[DEBUG] $message")
        }
    }
    
    /**
     * Conditional debug print with tag - only prints when debug is enabled
     * @param tag Custom tag for this log message
     * @param message The message to print
     */
    fun debugPrint(tag: String, message: String) {
        if (isDebugEnabled) {
            Log.d("$TAG-$tag", "[DEBUG] $message")
            println("[DEBUG][$tag] $message")
        }
    }
    
    /**
     * Debug print for errors - always logs errors regardless of debug flag
     * @param message The error message to print
     * @param throwable Optional exception to log
     */
    fun debugError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[ERROR] $message", throwable)
        println("[ERROR] $message")
        throwable?.printStackTrace()
    }
    
    /**
     * Debug print for warnings
     * @param message The warning message to print
     */
    fun debugWarn(message: String) {
        if (isDebugEnabled) {
            Log.w(TAG, "[WARN] $message")
            println("[WARN] $message")
        }
    }
    
    /**
     * Enable debug mode programmatically
     */
    fun enableDebug() {
        isDebugEnabled = true
        debugPrint("Debug mode enabled")
    }
    
    /**
     * Disable debug mode programmatically
     */
    fun disableDebug() {
        isDebugEnabled = false
        println("Debug mode disabled") // Use println since debug is disabled
    }
    
    /**
     * Get current debug status
     */
    fun getDebugStatus(): Boolean = isDebugEnabled
}
