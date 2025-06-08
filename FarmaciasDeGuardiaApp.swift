import SwiftUI

@main
struct FarmaciasDeGuardiaApp: App {
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
            } else {
                ContentView()
            }
        }
    }
}
