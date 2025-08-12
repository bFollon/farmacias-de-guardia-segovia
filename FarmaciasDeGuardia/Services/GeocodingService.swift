import Foundation
import CoreLocation

class GeocodingService {
    
    // In-memory cache for the current session
    private static var sessionCache: [String: CLLocation] = [:]
    
    static func getCoordinates(for address: String, region: String = "Segovia, España") async -> CLLocation? {
        let cacheKey = "\(address), \(region)"
        
        // Check session cache first (fastest)
        if let cachedLocation = sessionCache[cacheKey] {
            DebugConfig.debugPrint("📍 Using session cache for: \(address)")
            return cachedLocation
        }
        
        // Check persistent cache
        if let persistentLocation = CoordinateCache.getCoordinates(for: cacheKey) {
            DebugConfig.debugPrint("📍 Using persistent cache for: \(address)")
            sessionCache[cacheKey] = persistentLocation // Also cache in session
            return persistentLocation
        }
        
        let geocoder = CLGeocoder()
        let fullAddress = "\(address), \(region)"
        
        do {
            DebugConfig.debugPrint("🔍 Geocoding address: \(fullAddress)")
            let placemarks = try await geocoder.geocodeAddressString(fullAddress)
            
            if let location = placemarks.first?.location {
                // Cache in both session and persistent storage
                sessionCache[cacheKey] = location
                CoordinateCache.setCoordinates(location, for: cacheKey)
                
                DebugConfig.debugPrint("✅ Geocoded \(address) -> \(location.coordinate.latitude), \(location.coordinate.longitude)")
                return location
            } else {
                DebugConfig.debugPrint("❌ No coordinates found for: \(fullAddress)")
                return nil
            }
        } catch {
            DebugConfig.debugPrint("❌ Geocoding failed for \(fullAddress): \(error.localizedDescription)")
            return nil
        }
    }
    
    static func clearSessionCache() {
        sessionCache.removeAll()
        DebugConfig.debugPrint("🗑️ Session geocoding cache cleared")
    }
    
    static func clearAllCaches() {
        sessionCache.removeAll()
        CoordinateCache.clearAll()
        DebugConfig.debugPrint("🗑️ All geocoding caches cleared")
    }
    
    static func getCacheStats() -> (session: Int, persistent: (count: Int, size: String)) {
        return (session: sessionCache.count, persistent: CoordinateCache.getCacheStats())
    }
    
    /// Cleanup expired entries on app startup
    static func performMaintenanceCleanup() {
        CoordinateCache.cleanupExpiredEntries()
    }
}
