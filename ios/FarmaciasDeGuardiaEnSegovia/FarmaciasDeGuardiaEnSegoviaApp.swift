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
