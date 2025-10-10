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
import CoreLocation

/// Manages persistent route caching for driving/walking routes
/// Similar to CoordinateCache but for MapKit route results
class RouteCacheService {
    static let shared = RouteCacheService()
    
    private let userDefaults = UserDefaults.standard
    private let cacheKey = "route_cache"
    private let cacheVersionKey = "route_cache_version"
    private let currentCacheVersion = 1
    private let cacheExpiryDays: TimeInterval = 7 * 24 * 60 * 60 // 7 days in seconds
    
    private init() {
        // Check cache version and clear if outdated
        let storedVersion = userDefaults.integer(forKey: cacheVersionKey)
        if storedVersion != currentCacheVersion {
            DebugConfig.debugPrint("🔄 Route cache version mismatch, clearing route cache")
            clearAllRoutes()
            userDefaults.set(currentCacheVersion, forKey: cacheVersionKey)
        }
    }
    
    /// Get cached route for the given coordinates
    func getCachedRoute(from: CLLocation, to: CLLocation) -> RouteResult? {
        let routeKey = createRouteKey(from: from, to: to)
        
        guard let cacheData = userDefaults.data(forKey: cacheKey),
              let cache = try? JSONDecoder().decode([String: CachedRoute].self, from: cacheData),
              let cached = cache[routeKey] else {
            return nil
        }
        
        // Check if expired
        if cached.isExpired(expiryInterval: cacheExpiryDays) {
            DebugConfig.debugPrint("⏰ Cached route expired: \(routeKey)")
            return nil
        }
        
        DebugConfig.debugPrint("🚗 Using cached route: \(routeKey)")
        return RouteResult(
            distance: cached.distance,
            travelTime: cached.travelTime,
            walkingTime: cached.walkingTime,
            isEstimated: cached.isEstimated
        )
    }
    
    /// Cache route result for the given coordinates
    func setCachedRoute(from: CLLocation, to: CLLocation, result: RouteResult) {
        let routeKey = createRouteKey(from: from, to: to)
        var cache = loadCache()
        
        cache[routeKey] = CachedRoute(
            distance: result.distance,
            travelTime: result.travelTime,
            walkingTime: result.walkingTime,
            isEstimated: result.isEstimated,
            timestamp: Date().timeIntervalSince1970
        )
        
        saveCache(cache)
        DebugConfig.debugPrint("💾 Cached route: \(routeKey) (\(result.formattedDistance))")
    }
    
    /// Clear all cached routes (called when user location changes significantly)
    func clearAllRoutes() {
        let cache = loadCache()
        let routeCount = cache.count
        
        userDefaults.removeObject(forKey: cacheKey)
        DebugConfig.debugPrint("🗑️ Cleared all cached routes (\(routeCount) routes)")
    }
    
    /// Clean up expired route entries
    func cleanupExpiredEntries() {
        var cache = loadCache()
        let originalCount = cache.count
        
        // Remove expired entries
        cache = cache.filter { !$0.value.isExpired(expiryInterval: cacheExpiryDays) }
        
        if cache.count != originalCount {
            saveCache(cache)
            DebugConfig.debugPrint("🧹 Cleaned up \(originalCount - cache.count) expired route cache entries")
        }
    }
    
    /// Get cache statistics
    func getCacheStats() -> (count: Int, sizeEstimate: String) {
        let cache = loadCache()
        let count = cache.count
        let sizeEstimate = count * 200 // Rough estimate: 200 bytes per route entry
        
        let sizeString: String
        if sizeEstimate < 1024 {
            sizeString = "\(sizeEstimate)B"
        } else if sizeEstimate < 1024 * 1024 {
            sizeString = "\(sizeEstimate / 1024)KB"
        } else {
            sizeString = "\(sizeEstimate / (1024 * 1024))MB"
        }
        
        return (count, sizeString)
    }
    
    // MARK: - Private Helpers
    
    /// Create a cache key from two locations using rounded coordinates
    /// Rounds to ~100m precision (3 decimal places)
    private func createRouteKey(from: CLLocation, to: CLLocation) -> String {
        let fromLat = roundCoordinate(from.coordinate.latitude)
        let fromLon = roundCoordinate(from.coordinate.longitude)
        let toLat = roundCoordinate(to.coordinate.latitude)
        let toLon = roundCoordinate(to.coordinate.longitude)
        
        return "\(fromLat),\(fromLon)->\(toLat),\(toLon)"
    }
    
    /// Round coordinate to ~100m precision (3 decimal places)
    private func roundCoordinate(_ coord: Double) -> Double {
        return (coord * 1000).rounded() / 1000
    }
    
    /// Load cache from UserDefaults
    private func loadCache() -> [String: CachedRoute] {
        guard let cacheData = userDefaults.data(forKey: cacheKey),
              let cache = try? JSONDecoder().decode([String: CachedRoute].self, from: cacheData) else {
            return [:]
        }
        return cache
    }
    
    /// Save cache to UserDefaults
    private func saveCache(_ cache: [String: CachedRoute]) {
        if let cacheData = try? JSONEncoder().encode(cache) {
            userDefaults.set(cacheData, forKey: cacheKey)
        } else {
            DebugConfig.debugPrint("❌ Failed to save route cache")
        }
    }
}

// MARK: - Data Structures

/// Represents a cached route with expiration
private struct CachedRoute: Codable {
    let distance: Double // in meters
    let travelTime: TimeInterval // in seconds
    let walkingTime: TimeInterval // in seconds
    let isEstimated: Bool
    let timestamp: TimeInterval
    
    /// Check if this cached route has expired
    func isExpired(expiryInterval: TimeInterval) -> Bool {
        let expiryTime = timestamp + expiryInterval
        return Date().timeIntervalSince1970 > expiryTime
    }
}

