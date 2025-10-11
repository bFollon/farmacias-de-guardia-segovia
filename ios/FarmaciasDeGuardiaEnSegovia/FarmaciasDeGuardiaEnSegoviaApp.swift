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

import SwiftUI

@main
struct FarmaciasDeGuardiaEnSegoviaApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @State private var showSplashScreen = true
    @State private var isPreloading = false

    var body: some Scene {
        WindowGroup {
            if showSplashScreen {
                SplashScreen()
                    .onAppear {
                        startPreloadingAndInitialization()
                    }
                    .supportedOrientations(.portrait)
            } else {
                ContentView()
                    .supportedOrientations(.portrait)
            }
        }
    }
    
    private func startPreloadingAndInitialization() {
        Task {
            let startTime = Date()
            
            // Start preloading
            await PreloadService.shared.preloadAllData()
            
            // Ensure minimum splash duration (1.5 seconds) for visual consistency
            let minimumSplashDuration: TimeInterval = 1.5
            let elapsed = Date().timeIntervalSince(startTime)
            let remainingTime = max(0, minimumSplashDuration - elapsed)
            
            if remainingTime > 0 {
                try? await Task.sleep(nanoseconds: UInt64(remainingTime * 1_000_000_000))
            }
            
            // Initialize other app components
            await MainActor.run {
                initializeApp()
                showSplashScreen = false
            }
        }
    }
    
    private func initializeApp() {
        // Initialize PDF cache manager (already done in preload, but ensure it's ready)
        PDFCacheManager.shared.initialize()

        // Perform coordinate cache maintenance (already done in preload)
        GeocodingService.performMaintenanceCleanup()

        // Clean up expired validation and route caches
        PDFURLValidator.shared.clearExpiredCache()

        DebugConfig.debugPrint("✅ App initialization complete with cache maintenance")
    }
}

extension View {
    func supportedOrientations(_ orientations: UIInterfaceOrientationMask) -> some View {
        self.onAppear {
            AppDelegate.orientationLock = orientations
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    static var orientationLock = UIInterfaceOrientationMask.all
    
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        return AppDelegate.orientationLock
    }
}
