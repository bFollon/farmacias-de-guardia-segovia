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
    
    /// Enhanced geocoding for pharmacies that includes the pharmacy name for better accuracy
    static func getCoordinatesForPharmacy(_ pharmacy: Pharmacy) async -> CLLocation? {
        // Use enhanced query with pharmacy name (like in PharmacyView)
        let enhancedQuery = "\(pharmacy.name), \(pharmacy.address), Segovia, España"
        let cacheKey = enhancedQuery
        
        // Check session cache first (fastest)
        if let cachedLocation = sessionCache[cacheKey] {
            DebugConfig.debugPrint("📍 Using session cache for pharmacy: \(pharmacy.name)")
            return cachedLocation
        }
        
        // Check persistent cache
        if let persistentLocation = CoordinateCache.getCoordinates(for: cacheKey) {
            DebugConfig.debugPrint("📍 Using persistent cache for pharmacy: \(pharmacy.name)")
            sessionCache[cacheKey] = persistentLocation // Also cache in session
            return persistentLocation
        }
        
        let geocoder = CLGeocoder()
        
        do {
            DebugConfig.debugPrint("🔍 Geocoding pharmacy: \(enhancedQuery)")
            let placemarks = try await geocoder.geocodeAddressString(enhancedQuery)
            
            if let location = placemarks.first?.location {
                // Cache in both session and persistent storage
                sessionCache[cacheKey] = location
                CoordinateCache.setCoordinates(location, for: cacheKey)
                
                DebugConfig.debugPrint("✅ Geocoded pharmacy \(pharmacy.name) -> \(location.coordinate.latitude), \(location.coordinate.longitude)")
                return location
            } else {
                DebugConfig.debugPrint("❌ No coordinates found for pharmacy: \(enhancedQuery)")
                
                // Fallback to address-only geocoding
                DebugConfig.debugPrint("🔄 Trying fallback geocoding with address only for: \(pharmacy.name)")
                let fallbackResult = await getCoordinates(for: pharmacy.address)
                if fallbackResult != nil {
                    DebugConfig.debugPrint("✅ Fallback geocoding succeeded for: \(pharmacy.name)")
                } else {
                    DebugConfig.debugPrint("❌ Fallback geocoding also failed for: \(pharmacy.name)")
                }
                return fallbackResult
            }
        } catch {
            DebugConfig.debugPrint("❌ Pharmacy geocoding failed for \(enhancedQuery): \(error.localizedDescription)")
            
            // Fallback to address-only geocoding
            DebugConfig.debugPrint("🔄 Trying fallback geocoding with address only for: \(pharmacy.name)")
            let fallbackResult = await getCoordinates(for: pharmacy.address)
            if fallbackResult != nil {
                DebugConfig.debugPrint("✅ Fallback geocoding succeeded for: \(pharmacy.name)")
            } else {
                DebugConfig.debugPrint("❌ Fallback geocoding also failed for: \(pharmacy.name)")
            }
            return fallbackResult
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
