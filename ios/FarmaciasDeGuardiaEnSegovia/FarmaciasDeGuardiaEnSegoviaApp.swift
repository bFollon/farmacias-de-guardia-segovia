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
import StoreKit
import GRPC
import NIO
import OpenTelemetryApi
import OpenTelemetrySdk
import OpenTelemetryProtocolExporterCommon
import OpenTelemetryProtocolExporterGrpc

@main
struct FarmaciasDeGuardiaEnSegoviaApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @State private var showSplashScreen = true
    @State private var isPreloading = false
    @State private var showMonitoringConsent = false

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
                    .overlay {
                        if showMonitoringConsent {
                            ZStack {
                                // Semi-transparent background
                                Color.black.opacity(0.4)
                                    .ignoresSafeArea()

                                // Centered popup
                                MonitoringConsentView(isPresented: $showMonitoringConsent)
                                    .frame(maxWidth: 500)
                                    .background(Color(uiColor: .systemBackground))
                                    .cornerRadius(16)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 16)
                                            .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                                    )
                                    .shadow(radius: 20)
                                    .padding(40)
                            }
                        }
                    }
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

                // Show monitoring consent if user hasn't made a choice yet
                if !MonitoringPreferencesService.shared.hasUserMadeChoice() {
                    showMonitoringConsent = true
                }
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

        // Track app launch for review prompts
        ReviewPromptService.shared.recordAppLaunch()

        DebugConfig.debugPrint("✅ App initialization complete with cache maintenance")
    }
}

extension SwiftUI.View {
    func supportedOrientations(_ orientations: UIInterfaceOrientationMask) -> some SwiftUI.View {
        self.onAppear {
            AppDelegate.orientationLock = orientations
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    static var orientationLock = UIInterfaceOrientationMask.all

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Initialize OpenTelemetry (Signoz) only if user has opted in
        if MonitoringPreferencesService.shared.hasUserOptedIn() {
            // Get app version and environment
            let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
            #if DEBUG
            let environment = "debug"
            #else
            let environment = "production"
            #endif

            // Create resource with service info
            let resource = Resource(attributes: [
                "service.name": AttributeValue.string("farmacias-guardia-segovia"),
                "service.version": AttributeValue.string(appVersion),
                "deployment.environment": AttributeValue.string(environment)
            ])

            // Configure OTLP configuration
            let otlpConfiguration = OtlpConfiguration(timeout: TimeInterval(10))

            // Create gRPC channel for Signoz
            let grpcChannel = ClientConnection.insecure(group: MultiThreadedEventLoopGroup(numberOfThreads: 1))
                .connect(host: "homeserver.local", port: 4317)

            // Configure OTLP exporter for Signoz
            let otlpExporter = OtlpTraceExporter(
                channel: grpcChannel,
                config: otlpConfiguration
            )

            // Create span processor with immediate export
            let spanProcessor = SimpleSpanProcessor(spanExporter: otlpExporter)

            // Create tracer provider
            let tracerProvider = TracerProviderBuilder()
                .add(spanProcessor: spanProcessor)
                .with(resource: resource)
                .build()

            // Register globally
            OpenTelemetry.registerTracerProvider(tracerProvider: tracerProvider)

            DebugConfig.debugPrint("✅ OpenTelemetry (Signoz) monitoring initialized (user opted in)")
        } else {
            DebugConfig.debugPrint("⚠️ OpenTelemetry (Signoz) monitoring disabled (user has not opted in)")
        }

        return true
    }

    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        return AppDelegate.orientationLock
    }
}
