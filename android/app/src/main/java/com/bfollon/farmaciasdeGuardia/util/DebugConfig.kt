package com.bfollon.farmaciasdeGuardia.util

import android.util.Log
import com.bfollon.farmaciasdeGuardia.BuildConfig

/**
 * Global debug configuration for the entire application
 * Equivalent to iOS DebugConfig.swift
 */
object DebugConfig {
    private const val TAG = "FarmaciasDebug"
    
    /**
     * Default debug setting when no environment variable is set
     * Change this value to enable/disable debug logging by default
     */
    private const val DEFAULT_DEBUG_ENABLED = false
    
    /**
     * Master debug flag - controls all debug output in the application
     * Automatically enabled in debug builds, can be overridden
     */
    var isDebugEnabled: Boolean = BuildConfig.DEBUG || DEFAULT_DEBUG_ENABLED
        private set
    
    /**
     * Conditional debug print - only prints when debug is enabled
     * @param message The message to print
     */
    fun debugPrint(message: String) {
        if (isDebugEnabled) {
            Log.d(TAG, "[DEBUG] $message")
        }
    }
    
    /**
     * Conditional debug print with format - only prints when debug is enabled
     * @param format The format string
     * @param args The arguments for the format string
     */
    fun debugPrint(format: String, vararg args: Any) {
        if (isDebugEnabled) {
            Log.d(TAG, "[DEBUG] ${format.format(*args)}")
        }
    }
    
    /**
     * Enable debug mode programmatically
     */
    fun enableDebug() {
        isDebugEnabled = true
    }
    
    /**
     * Disable debug mode programmatically
     */
    fun disableDebug() {
        isDebugEnabled = false
    }
    
    /**
     * Get current debug status
     */
    fun getDebugStatus(): Boolean = isDebugEnabled
}
