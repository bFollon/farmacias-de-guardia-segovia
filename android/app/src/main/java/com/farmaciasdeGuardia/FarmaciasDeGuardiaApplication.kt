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

package com.farmaciasdeGuardia

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.farmaciasdeGuardia.utils.DebugConfig

/**
 * Application class for Farmacias de Guardia
 * Initializes Hilt dependency injection and app-wide configurations
 */
@HiltAndroidApp
class FarmaciasDeGuardiaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize debug configuration
        DebugConfig.isDebugMode = BuildConfig.DEBUG
        
        DebugConfig.debugPrint("🚀 FarmaciasDeGuardia Application started")
        DebugConfig.debugPrint("📱 Debug mode: ${DebugConfig.isDebugMode}")
    }
}
