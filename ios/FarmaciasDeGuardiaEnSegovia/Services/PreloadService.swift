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

import Foundation

/// Service responsible for syncing and caching pharmacy schedules during app startup.
class PreloadService: ObservableObject {
    static let shared = PreloadService()

    @Published var isLoading = false
    @Published var loadingProgress: String = ""
    @Published var completedRegions = 0
    @Published var totalRegions = 0
    /// Set right before `isLoading` goes false, so the splash's loader can show a red X instead
    /// of just fading out. True when offline (the sync never actually attempted the network) or
    /// when `ScheduleSyncStatus` recorded a real fetch failure during this preload.
    @Published private(set) var hasError = false

    private init() {}

    /// Scrape PDF URLs from the stable cofsegovia.com page. This only feeds the "view
    /// official PDF" browser hand-off (PDFURLScrapingService/PDFURLValidator) - schedule
    /// freshness is handled entirely by ScheduleSyncService's server-version comparison,
    /// so there's no cache invalidation to do here anymore.
    private func scrapePDFURLs() async {
        DebugConfig.debugPrint("PreloadService: Starting PDF URL scraping...")

        if !NetworkMonitor.shared.isOnline {
            DebugConfig.debugPrint("📡 PreloadService: Offline, skipping URL scraping")
            return
        }

        let scrapedData = await PDFURLScrapingService.shared.scrapePDFURLs()

        if scrapedData.isEmpty {
            AnalyticsService.shared.track("pdf_url_scrape_failed", with: ["error": "no_urls_returned"])
        } else {
            AnalyticsService.shared.track("pdf_url_scrape_complete", with: ["urls_found": scrapedData.count])
        }

        PDFURLScrapingService.shared.printScrapedData(scrapedData)

        DebugConfig.debugPrint("PreloadService: PDF URL scraping completed")
    }

    /// Sync and cache schedules for all 11 locations.
    func preloadAllData() async {
        let locations = DutyLocation.allSyncable

        await MainActor.run {
            isLoading = true
            hasError = false
            totalRegions = locations.count
            completedRegions = 0
        }

        DebugConfig.debugPrint("🚀 Starting preload of \(locations.count) locations...")

        // Scrape PDF URLs for the "view official PDF" feature (unrelated to schedule sync)
        await scrapePDFURLs()

        await MainActor.run {
            loadingProgress = "Inicializando..."
        }

        await MainActor.run {
            loadingProgress = "Sincronizando..."
        }

        // One batched sync call for every location, instead of one call (and one manifest
        // fetch) per location - see ScheduleService.preloadAll's doc comment for why: with an
        // unreachable server, per-location fetches meant up to 11 sequential timeouts stacking
        // up before this screen would let go of the user, plus a near-duplicate Bugsink report
        // for each one.
        let results = await ScheduleService.preloadAll(locations: locations)

        var completedCount = 0
        for (location, schedules) in results {
            DebugConfig.debugPrint("✅ Successfully preloaded \(schedules.count) schedules for: \(location.name)")

            completedCount += 1
            await MainActor.run {
                loadingProgress = "Cargando \(location.name)..."
                completedRegions = completedCount
            }

            // Purely cosmetic: the actual sync already happened in one batched call above, so
            // without this the splash screen's region icons would all light up in the same
            // frame instead of the progressive reveal they're designed to show (matches
            // Android's equivalent 200ms stagger in SplashViewModel.loadRegionsSequentially).
            try? await Task.sleep(nanoseconds: 150_000_000)
        }

        // Perform coordinate cache maintenance
        await MainActor.run {
            loadingProgress = "Finalizando..."
        }
        GeocodingService.performMaintenanceCleanup()

        DebugConfig.debugPrint("🎉 Preload completed!")

        // Determine failure before letting go of isLoading — offline means the sync never
        // even attempted the network (ScheduleSyncService.syncAll's skippedOffline path never
        // touches ScheduleSyncStatus), so it needs its own check alongside isServerUnreachable.
        let offline = !NetworkMonitor.shared.isOnline
        let serverUnreachable = await MainActor.run { ScheduleSyncStatus.shared.isServerUnreachable }
        let failed = offline || serverUnreachable

        await MainActor.run { hasError = failed }

        if failed {
            // The app's own minimum-splash-duration floor (see
            // FarmaciasDeGuardiaEnSegoviaApp.swift) is measured from when loading started, so a
            // real network timeout that already ran past it leaves zero guaranteed time for the
            // red X to actually be seen before the splash dismisses. Hold here instead, keeping
            // isLoading true (and therefore the splash visible) for a beat regardless of how
            // long the failure itself took.
            try? await Task.sleep(nanoseconds: 1_500_000_000)
        }

        await MainActor.run {
            isLoading = false
            loadingProgress = "Completado"
        }
    }
}
