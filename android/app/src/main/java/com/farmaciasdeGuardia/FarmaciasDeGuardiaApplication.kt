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
