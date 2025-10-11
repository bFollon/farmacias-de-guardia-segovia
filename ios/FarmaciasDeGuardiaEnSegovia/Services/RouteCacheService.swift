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

/// Cached route information with location-aware validation
struct CachedRoute: Codable {
    let originLatitude: Double
    let originLongitude: Double
    let destinationLatitude: Double
    let destinationLongitude: Double
    let distance: Double
    let travelTime: TimeInterval
    let walkingTime: TimeInterval
    let timestamp: TimeInterval
    let isEstimated: Bool

    var originCoordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: originLatitude, longitude: originLongitude)
    }

    var destinationCoordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: destinationLatitude, longitude: destinationLongitude)
    }

    /// Check if this cached route is still valid for the given user location
    /// - Parameters:
    ///   - userLocation: Current user location
    ///   - maxDistanceMeters: Maximum distance user can have moved from cached origin (default: 300m)
    ///   - maxAgeHours: Maximum age of cache in hours (default: 24)
    /// - Returns: True if cache is still valid
    func isValid(for userLocation: CLLocation, maxDistanceMeters: Double = 300, maxAgeHours: Double = 24) -> Bool {
        // Check timestamp freshness
        let now = Date().timeIntervalSince1970
        let ageHours = (now - timestamp) / 3600

        if ageHours > maxAgeHours {
            DebugConfig.debugPrint("🗺️ Route cache expired (age: \(String(format: "%.1f", ageHours))h)")
            return false
        }

        // Check if user has moved significantly from cached origin
        let cachedOrigin = CLLocation(latitude: originLatitude, longitude: originLongitude)
        let distanceMoved = userLocation.distance(from: cachedOrigin)

        if distanceMoved > maxDistanceMeters {
            DebugConfig.debugPrint("🗺️ User moved too far from cached origin (\(String(format: "%.0f", distanceMoved))m > \(String(format: "%.0f", maxDistanceMeters))m)")
            return false
        }

        DebugConfig.debugPrint("✅ Route cache valid (age: \(String(format: "%.1f", ageHours))h, moved: \(String(format: "%.0f", distanceMoved))m)")
        return true
    }
}

/// Cache key for route lookups
struct RouteCacheKey: Hashable, Codable {
    let destinationLatitude: Double
    let destinationLongitude: Double

    init(destination: CLLocationCoordinate2D) {
        // Round to 6 decimal places (~0.1m precision) to handle minor coordinate variations
        self.destinationLatitude = (destination.latitude * 1000000).rounded() / 1000000
        self.destinationLongitude = (destination.longitude * 1000000).rounded() / 1000000
    }

    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: destinationLatitude, longitude: destinationLongitude)
    }
}

/// Service for caching route calculations to improve performance and enable offline routing
class RouteCacheService {
    static let shared = RouteCacheService()

    private let cacheDirectory: URL
    private let cacheFileName = "routes.json"
    private var memoryCache: [RouteCacheKey: CachedRoute] = [:]

    private init() {
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        self.cacheDirectory = documentsPath.appendingPathComponent("RouteCache")

        // Create cache directory if needed
        try? FileManager.default.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)

        // Load cache from disk
        loadCache()
    }

    /// Get cached route for destination if valid for current user location
    func getCachedRoute(from userLocation: CLLocation, to destination: CLLocationCoordinate2D) -> CachedRoute? {
        let key = RouteCacheKey(destination: destination)

        guard let cachedRoute = memoryCache[key] else {
            DebugConfig.debugPrint("🗺️ No cached route found for destination")
            return nil
        }

        // Validate cache based on user's current location
        if cachedRoute.isValid(for: userLocation) {
            DebugConfig.debugPrint("🗺️ Using cached route to destination")
            return cachedRoute
        } else {
            // Remove invalid cache entry
            DebugConfig.debugPrint("🗺️ Removing invalid cached route")
            memoryCache.removeValue(forKey: key)
            saveCache()
            return nil
        }
    }

    /// Cache a route calculation result
    func cacheRoute(from origin: CLLocation, to destination: CLLocationCoordinate2D, distance: Double, travelTime: TimeInterval, walkingTime: TimeInterval, isEstimated: Bool) {
        let key = RouteCacheKey(destination: destination)

        let cachedRoute = CachedRoute(
            originLatitude: origin.coordinate.latitude,
            originLongitude: origin.coordinate.longitude,
            destinationLatitude: destination.latitude,
            destinationLongitude: destination.longitude,
            distance: distance,
            travelTime: travelTime,
            walkingTime: walkingTime,
            timestamp: Date().timeIntervalSince1970,
            isEstimated: isEstimated
        )

        memoryCache[key] = cachedRoute
        DebugConfig.debugPrint("🗺️ Cached route to destination (distance: \(String(format: "%.1f", distance/1000))km)")

        // Persist to disk
        saveCache()
    }

    /// Clear all cached routes
    func clearCache() {
        memoryCache.removeAll()
        saveCache()
        DebugConfig.debugPrint("🗺️ Cleared all route cache")
    }

    /// Clear routes older than specified hours
    func clearOldRoutes(olderThanHours hours: Double) {
        let now = Date().timeIntervalSince1970
        let maxAge = hours * 3600

        memoryCache = memoryCache.filter { _, route in
            (now - route.timestamp) <= maxAge
        }

        saveCache()
        DebugConfig.debugPrint("🗺️ Cleared routes older than \(hours) hours")
    }

    // MARK: - Private Methods

    private func loadCache() {
        let cacheFile = cacheDirectory.appendingPathComponent(cacheFileName)

        guard FileManager.default.fileExists(atPath: cacheFile.path) else {
            DebugConfig.debugPrint("🗺️ No route cache file found")
            return
        }

        do {
            let data = try Data(contentsOf: cacheFile)
            memoryCache = try JSONDecoder().decode([RouteCacheKey: CachedRoute].self, from: data)
            DebugConfig.debugPrint("🗺️ Loaded \(memoryCache.count) cached routes from disk")
        } catch {
            DebugConfig.debugPrint("❌ Failed to load route cache: \(error.localizedDescription)")
        }
    }

    private func saveCache() {
        let cacheFile = cacheDirectory.appendingPathComponent(cacheFileName)

        do {
            let data = try JSONEncoder().encode(memoryCache)
            try data.write(to: cacheFile)
            DebugConfig.debugPrint("🗺️ Saved \(memoryCache.count) routes to cache")
        } catch {
            DebugConfig.debugPrint("❌ Failed to save route cache: \(error.localizedDescription)")
        }
    }
}
