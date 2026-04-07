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

/// Result of URL change detection
struct URLChangeResult {
    let success: Bool
    let changedRegionIds: [String]
    let urlChanges: [String: URLChange]  // Key = region ID
}

/// Details of a URL change
struct URLChange {
    let regionId: String
    let oldURL: String
    let newURL: String
}

/// Service responsible for preloading and caching PDFs during app startup
class PreloadService: ObservableObject {
    static let shared = PreloadService()
    
    @Published var isLoading = false
    @Published var loadingProgress: String = ""
    @Published var completedRegions = 0
    @Published var totalRegions = 0
    
    private var regions: [Region] = [
        .segoviaCapital,
        .cuellar,
        .elEspinar,
        .segoviaRural
    ]
    
    private init() {}
    
    /// Scrape PDF URLs from the stable cofsegovia.com page
    /// This runs at startup to check for URL updates
    private func scrapePDFURLs() async {
        DebugConfig.debugPrint("PreloadService: Starting PDF URL scraping...")

        // Check if offline
        if !NetworkMonitor.shared.isOnline {
            DebugConfig.debugPrint("📡 PreloadService: Offline, skipping URL scraping and change detection")
            // Still populate regions with cached/fallback URLs
            regions = [.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
            return
        }

        // Load old URLs BEFORE scraping
        let oldURLs = PDFURLScrapingService.shared.loadPersistedURLs()
        DebugConfig.debugPrint("📂 PreloadService: Loaded \(oldURLs.count) old URLs for comparison")

        // Scrape fresh URLs
        let scrapedData = await PDFURLScrapingService.shared.scrapePDFURLs()

        // Build URL map: display name → URL
        let newURLs = Dictionary(uniqueKeysWithValues: scrapedData.map { ($0.regionName, $0.pdfURL) })

        // Detect changes
        let changeResult = detectURLChanges(oldURLs: oldURLs, newURLs: newURLs)

        // Invalidate caches for changed regions
        if !changeResult.changedRegionIds.isEmpty {
            await invalidateCachesForChangedURLs(changeResult: changeResult)
        }

        // Analytics: scrape result
        if scrapedData.isEmpty {
            AnalyticsService.shared.track("pdf_url_scrape_failed", with: ["error": "no_urls_returned"])
        } else {
            AnalyticsService.shared.track("pdf_url_scrape_complete", with: [
                "urls_found": scrapedData.count,
                "urls_changed": !changeResult.changedRegionIds.isEmpty
            ])
        }

        // Print scraped data (existing call)
        PDFURLScrapingService.shared.printScrapedData(scrapedData)

        // Re-populate regions with scraped URLs (existing code)
        regions = [
            .segoviaCapital,
            .cuellar,
            .elEspinar,
            .segoviaRural
        ]

        DebugConfig.debugPrint("PreloadService: PDF URL scraping completed, regions populated")
    }
    
    /// Preload all PDFs and cache them
    func preloadAllData() async {
        // Calculate total: 3 main regions + 8 ZBS (skip Segovia Rural region itself)
        let zbsLocations = ZBS.availableZBS
        let totalLocations = regions.count - 1 + zbsLocations.count  // -1 for segoviaRural

        await MainActor.run {
            isLoading = true
            totalRegions = totalLocations
            completedRegions = 0
        }

        DebugConfig.debugPrint("🚀 Starting preload of 3 main regions + \(zbsLocations.count) ZBS...")

        // First, scrape PDF URLs to check for updates
        await scrapePDFURLs()

        // Initialize cache manager first
        await MainActor.run {
            loadingProgress = "Inicializando..."
        }
        PDFCacheManager.shared.initialize()

        var completedCount = 0

        // Preload main regions (Segovia Capital, Cuéllar, El Espinar)
        // Skip Segovia Rural - it doesn't have schedules, only its ZBS do
        for region in regions {
            // Skip Segovia Rural - we'll preload its ZBS instead
            if region.id == "segovia-rural" {
                DebugConfig.debugPrint("⏭️ Skipping Segovia Rural region (will preload ZBS instead)")
                continue
            }

            await MainActor.run {
                loadingProgress = "Cargando \(region.name)..."
            }

            DebugConfig.debugPrint("📥 Preloading region: \(region.name)")

            let location = DutyLocation.fromRegion(region)
            let schedules = await ScheduleService.loadSchedules(for: location, forceRefresh: false)

            DebugConfig.debugPrint("✅ Successfully preloaded \(schedules.count) schedules for: \(region.name)")

            completedCount += 1
            await MainActor.run {
                completedRegions = completedCount
            }
        }

        // Preload Segovia Rural ZBS (8 locations)
        // If any ZBS cache is invalid, parsing will happen once and cache all 8
        for zbs in zbsLocations {
            await MainActor.run {
                loadingProgress = "Cargando \(zbs.name)..."
            }

            DebugConfig.debugPrint("📥 Preloading ZBS: \(zbs.name)")

            let location = DutyLocation.fromZBS(zbs)
            let schedules = await ScheduleService.loadSchedules(for: location, forceRefresh: false)

            DebugConfig.debugPrint("✅ Successfully preloaded \(schedules.count) schedules for: \(zbs.name)")

            completedCount += 1
            await MainActor.run {
                completedRegions = completedCount
            }
        }

        // Perform coordinate cache maintenance
        await MainActor.run {
            loadingProgress = "Finalizando..."
        }
        GeocodingService.performMaintenanceCleanup()

        DebugConfig.debugPrint("🎉 Preload completed!")

        await MainActor.run {
            isLoading = false
            loadingProgress = "Completado"
        }
    }
    
    /// Detect changes between old and new PDF URLs
    private func detectURLChanges(
        oldURLs: [String: String],
        newURLs: [String: URL]
    ) -> URLChangeResult {
        var changedRegionIds: [String] = []
        var urlChanges: [String: URLChange] = [:]

        for (displayName, newURL) in newURLs {
            let newURLString = newURL.absoluteString
            let oldURLString = oldURLs[displayName]

            // Check if URL changed (nil counts as a change)
            if oldURLString != newURLString {
                // Translate display name to region ID
                guard let regionId = Region.displayNameToId(displayName) else {
                    DebugConfig.debugPrint("⚠️ PreloadService: Could not map display name '\(displayName)' to region ID")
                    continue
                }

                DebugConfig.debugPrint("🔄 PreloadService: URL changed for \(displayName) (regionId: \(regionId))")
                if let oldURLString = oldURLString {
                    DebugConfig.debugPrint("   📄 Old: \((oldURLString as NSString).lastPathComponent)")
                }
                DebugConfig.debugPrint("   📄 New: \((newURLString as NSString).lastPathComponent)")

                changedRegionIds.append(regionId)
                urlChanges[regionId] = URLChange(
                    regionId: regionId,
                    oldURL: oldURLString ?? "",
                    newURL: newURLString
                )
            }
        }

        if !changedRegionIds.isEmpty {
            DebugConfig.debugPrint("✅ PreloadService: Detected \(changedRegionIds.count) URL changes: \(changedRegionIds)")
        } else {
            DebugConfig.debugPrint("✅ PreloadService: No URL changes detected")
        }

        return URLChangeResult(
            success: true,
            changedRegionIds: changedRegionIds,
            urlChanges: urlChanges
        )
    }

    /// Invalidate all caches for regions whose PDF URLs have changed
    private func invalidateCachesForChangedURLs(changeResult: URLChangeResult) async {
        DebugConfig.debugPrint("🗑️ PreloadService: Invalidating caches for \(changeResult.changedRegionIds.count) regions with URL changes")

        for regionId in changeResult.changedRegionIds {
            // Find the region
            guard let region = Region.fromId(regionId) else {
                DebugConfig.debugPrint("⚠️ PreloadService: Could not find region for ID: \(regionId)")
                continue
            }

            DebugConfig.debugPrint("🗑️ PreloadService: Clearing caches for \(region.name) (URL changed)")

            // Log the change details
            if let change = changeResult.urlChanges[regionId] {
                DebugConfig.debugPrint("   📄 Old PDF: \((change.oldURL as NSString).lastPathComponent)")
                DebugConfig.debugPrint("   📄 New PDF: \((change.newURL as NSString).lastPathComponent)")
            }

            // Clear all three cache layers:

            // 1. PDF Cache (PDFCacheManager)
            PDFCacheManager.shared.clearCache(for: region)

            // 2. Persistent Schedule Cache (ScheduleCacheService)
            clearScheduleCacheForRegion(region)

            // 3. Memory Cache (ScheduleService)
            clearMemoryCacheForRegion(region)

            DebugConfig.debugPrint("✅ PreloadService: Cleared all caches for \(region.name)")
        }
    }

    /// Clear persistent schedule cache for a region
    private func clearScheduleCacheForRegion(_ region: Region) {
        // Clear main region cache
        let mainLocation = DutyLocation.fromRegion(region)
        ScheduleCacheService.shared.clearLocationCache(for: mainLocation)

        // For Segovia Rural, also clear all ZBS caches
        if region.id == "segovia-rural" {
            for zbs in ZBS.availableZBS {
                let zbsLocation = DutyLocation.fromZBS(zbs)
                ScheduleCacheService.shared.clearLocationCache(for: zbsLocation)
            }
            DebugConfig.debugPrint("🗑️ PreloadService: Cleared \(ZBS.availableZBS.count) ZBS caches for Segovia Rural")
        }
    }

    /// Clear memory cache for a region
    private func clearMemoryCacheForRegion(_ region: Region) {
        // Clear main region cache
        let mainLocation = DutyLocation.fromRegion(region)
        ScheduleService.clearCache(for: mainLocation)

        // For Segovia Rural, also clear all ZBS caches
        if region.id == "segovia-rural" {
            for zbs in ZBS.availableZBS {
                let zbsLocation = DutyLocation.fromZBS(zbs)
                ScheduleService.clearCache(for: zbsLocation)
            }
        }
    }

    /// Check if preloading is needed (if any region lacks cached data)
    func needsPreloading() async -> Bool {
        let allStatuses = await PDFCacheManager.shared.getCacheStatus()

        for region in regions {
            let status = allStatuses.first { $0.region.id == region.id }
            if let status = status, !status.isCached {
                return true
            } else if status == nil {
                return true // Region not found in cache status
            }
        }
        return false
    }
}
