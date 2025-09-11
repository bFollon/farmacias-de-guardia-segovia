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
        
        let scrapedData = await PDFURLScrapingService.shared.scrapePDFURLs()
        
        // Print the scraped data to console for debugging
        PDFURLScrapingService.shared.printScrapedData(scrapedData)
        
        // Now that scraping is complete, populate regions with scraped URLs
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
        await MainActor.run {
            isLoading = true
            totalRegions = regions.count
            completedRegions = 0
        }
        
        DebugConfig.debugPrint("🚀 Starting preload of \(regions.count) regions...")
        
        // First, scrape PDF URLs to check for updates
        await scrapePDFURLs()
        
        // Initialize cache manager first
        await MainActor.run {
            loadingProgress = "Inicializando..."
        }
        PDFCacheManager.shared.initialize()
        
        // Preload each region
        for (index, region) in regions.enumerated() {
            await MainActor.run {
                loadingProgress = "Cargando \(region.name)..."
            }
            
            DebugConfig.debugPrint("📥 Preloading region: \(region.name)")
            
            // Load and cache schedules for this region (this will download, parse and cache)
            let schedules = await ScheduleService.loadSchedules(for: region, forceRefresh: false)
            
            DebugConfig.debugPrint("✅ Successfully preloaded \(schedules.count) schedules for: \(region.name)")
            
            await MainActor.run {
                completedRegions = index + 1
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
