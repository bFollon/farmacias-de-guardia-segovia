import SwiftUI

@main
struct FarmaciasDeGuardiaApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @State private var showSplashScreen = true

    var body: some Scene {
        WindowGroup {
            if showSplashScreen {
                SplashScreen()
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                            showSplashScreen = false
                        }
                    }
                    .supportedOrientations(.portrait)
            } else {
                ContentView()
                    .onAppear {
                        initializeApp()
                    }
                    .supportedOrientations(.portrait)
            }
        }
    }
    
    private func initializeApp() {
        // Initialize PDF cache manager
        PDFCacheManager.shared.initialize()
        
        // Perform coordinate cache maintenance
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
