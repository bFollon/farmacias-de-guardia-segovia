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
import android.content.SharedPreferences

/**
 * Manages user consent preferences for monitoring and error tracking
 * Equivalent to iOS MonitoringPreferencesService
 */
object MonitoringPreferencesService {

    private const val PREFS_NAME = "monitoring_preferences"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
    private const val KEY_CHOICE_MADE = "monitoring_choice_made"

    private var sharedPreferences: SharedPreferences? = null

    /**
     * Initialize the service with application context
     * Must be called before using other methods
     */
    fun initialize(context: Context) {
        sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        DebugConfig.debugPrint("MonitoringPreferencesService initialized")
    }

    /**
     * Returns true if user has explicitly opted in to monitoring
     */
    fun hasUserOptedIn(): Boolean {
        val prefs = sharedPreferences ?: run {
            DebugConfig.debugWarn("MonitoringPreferencesService not initialized")
            return false
        }

        // If user hasn't made a choice yet, default to false (opt-in approach)
        if (!hasUserMadeChoice()) {
            return false
        }
        return prefs.getBoolean(KEY_MONITORING_ENABLED, false)
    }

    /**
     * Returns true if user has made any choice (enable or decline)
     */
    fun hasUserMadeChoice(): Boolean {
        val prefs = sharedPreferences ?: run {
            DebugConfig.debugWarn("MonitoringPreferencesService not initialized")
            return false
        }
        return prefs.getBoolean(KEY_CHOICE_MADE, false)
    }

    /**
     * Saves user's monitoring preference
     * @param enabled true to enable monitoring, false to disable
     */
    fun setMonitoringEnabled(enabled: Boolean) {
        val prefs = sharedPreferences ?: run {
            DebugConfig.debugError("MonitoringPreferencesService not initialized")
            return
        }

        prefs.edit().apply {
            putBoolean(KEY_MONITORING_ENABLED, enabled)
            putBoolean(KEY_CHOICE_MADE, true)
            apply()
        }

        if (enabled) {
            DebugConfig.debugPrint("User opted IN to monitoring")
        } else {
            DebugConfig.debugPrint("User opted OUT of monitoring")
        }
    }

    /**
     * Resets all monitoring preferences (for testing)
     */
    fun resetPreferences() {
        val prefs = sharedPreferences ?: return
        prefs.edit().clear().apply()
        DebugConfig.debugPrint("Monitoring preferences reset")
    }

    /**
     * Returns current preferences status for debugging
     */
    fun getPreferencesStatus(): String {
        val prefs = sharedPreferences ?: return "Not initialized"
        return """
            Monitoring Preferences Status:
            - Has made choice: ${hasUserMadeChoice()}
            - Has opted in: ${hasUserOptedIn()}
            - Raw monitoring_enabled: ${prefs.getBoolean(KEY_MONITORING_ENABLED, false)}
            - Raw choice_made: ${prefs.getBoolean(KEY_CHOICE_MADE, false)}
        """.trimIndent()
    }
}
