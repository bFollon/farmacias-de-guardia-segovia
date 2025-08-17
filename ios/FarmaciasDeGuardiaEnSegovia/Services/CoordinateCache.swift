import Foundation
import CoreLocation

/// Manages persistent coordinate caching for pharmacy addresses
class CoordinateCache {
    private static let userDefaults = UserDefaults.standard
    private static let cacheKey = "pharmacy_coordinates_cache"
    private static let cacheVersionKey = "coordinate_cache_version"
    private static let currentCacheVersion = 1
    
    /// Get cached coordinates for an address
    static func getCoordinates(for address: String) -> CLLocation? {
        guard let cacheData = userDefaults.data(forKey: cacheKey),
              let cache = try? JSONDecoder().decode([String: CachedCoordinate].self, from: cacheData),
              let cached = cache[address],
              !cached.isExpired else {
            return nil
        }
        
        return CLLocation(latitude: cached.latitude, longitude: cached.longitude)
    }
    
    /// Cache coordinates for an address
    static func setCoordinates(_ location: CLLocation, for address: String) {
        var cache = loadCache()
        cache[address] = CachedCoordinate(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            timestamp: Date()
        )
        saveCache(cache)
    }
    
    /// Clear expired cache entries
    static func cleanupExpiredEntries() {
        var cache = loadCache()
        let originalCount = cache.count
        
        cache = cache.filter { !$0.value.isExpired }
        
        if cache.count != originalCount {
            saveCache(cache)
            DebugConfig.debugPrint("🧹 Cleaned up \(originalCount - cache.count) expired coordinate cache entries")
        }
    }
    
    /// Clear all cached coordinates
    static func clearAll() {
        userDefaults.removeObject(forKey: cacheKey)
        DebugConfig.debugPrint("🗑️ Cleared all coordinate cache")
    }
    
    /// Get cache statistics
    static func getCacheStats() -> (count: Int, size: String) {
        let cache = loadCache()
        let data = userDefaults.data(forKey: cacheKey) ?? Data()
        let sizeKB = Double(data.count) / 1024.0
        return (count: cache.count, size: String(format: "%.1f KB", sizeKB))
    }
    
    private static func loadCache() -> [String: CachedCoordinate] {
        // Check cache version
        let savedVersion = userDefaults.integer(forKey: cacheVersionKey)
        if savedVersion != currentCacheVersion {
            // Clear cache if version mismatch
            userDefaults.removeObject(forKey: cacheKey)
            userDefaults.set(currentCacheVersion, forKey: cacheVersionKey)
            return [:]
        }
        
        guard let data = userDefaults.data(forKey: cacheKey),
              let cache = try? JSONDecoder().decode([String: CachedCoordinate].self, from: data) else {
            return [:]
        }
        
        return cache
    }
    
    private static func saveCache(_ cache: [String: CachedCoordinate]) {
        if let data = try? JSONEncoder().encode(cache) {
            userDefaults.set(data, forKey: cacheKey)
        }
    }
}

private struct CachedCoordinate: Codable {
    let latitude: Double
    let longitude: Double
    let timestamp: Date
    
    // Cache expires after 30 days
    var isExpired: Bool {
        Date().timeIntervalSince(timestamp) > 30 * 24 * 60 * 60
    }
}
