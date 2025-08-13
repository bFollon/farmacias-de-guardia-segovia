import Foundation

/// Service responsible for preloading and caching PDFs during app startup
class PreloadService: ObservableObject {
    static let shared = PreloadService()
    
    @Published var isLoading = false
    @Published var loadingProgress: String = ""
    @Published var completedRegions = 0
    @Published var totalRegions = 0
    
    private let regions: [Region] = [
        .segoviaCapital,
        .cuellar,
        .elEspinar,
        .segoviaRural
    ]
    
    private init() {}
    
    /// Preload all PDFs and cache them
    func preloadAllData() async {
        await MainActor.run {
            isLoading = true
            totalRegions = regions.count
            completedRegions = 0
        }
        
        DebugConfig.debugPrint("🚀 Starting preload of \(regions.count) regions...")
        
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
            
            do {
                // Load and cache schedules for this region (this will download, parse and cache)
                let schedules = await ScheduleService.loadSchedules(for: region, forceRefresh: false)
                
                DebugConfig.debugPrint("✅ Successfully preloaded \(schedules.count) schedules for: \(region.name)")
                
                await MainActor.run {
                    completedRegions = index + 1
                }
                
            } catch {
                DebugConfig.debugPrint("❌ Failed to preload \(region.name): \(error)")
                // Continue with other regions even if one fails
                await MainActor.run {
                    completedRegions = index + 1
                }
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
