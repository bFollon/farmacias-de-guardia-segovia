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
import MapKit
import CoreLocation

// Note: MapKit may generate harmless system warnings about RenderBox.framework/default.metallib
// on first use. This is a known iOS system issue related to Metal shader loading and doesn't
// affect app functionality. The warning typically appears as:
// "Unable to open mach-O at path: /Library/Caches/com.apple.xbs/Binaries/RenderBox/..."

struct RouteResult {
    let distance: Double
    let travelTime: TimeInterval
    let walkingTime: TimeInterval
    let isEstimated: Bool
    
    var formattedDistance: String {
        if distance < 1000 {
            return String(format: "%.0f m", distance)
        } else {
            return String(format: "%.1f km", distance / 1000)
        }
    }
    
    var formattedTravelTime: String {
        if isEstimated || travelTime <= 0 {
            return ""
        }
        
        let minutes = Int(travelTime / 60)
        if minutes < 60 {
            return "\(minutes) min"
        } else {
            let hours = minutes / 60
            let remainingMinutes = minutes % 60
            if remainingMinutes == 0 {
                return "\(hours)h"
            } else {
                return "\(hours)h \(remainingMinutes)m"
            }
        }
    }
    
    var formattedWalkingTime: String {
        if isEstimated || walkingTime <= 0 {
            return ""
        }
        
        let minutes = Int(walkingTime / 60)
        if minutes < 60 {
            return "\(minutes) min"
        } else {
            let hours = minutes / 60
            let remainingMinutes = minutes % 60
            if remainingMinutes == 0 {
                return "\(hours)h"
            } else {
                return "\(hours)h \(remainingMinutes)m"
            }
        }
    }
}

class RoutingService {
    
    /// Calculate driving route from user location to destination
    static func calculateDrivingRoute(from userLocation: CLLocation, to destination: CLLocation) async -> RouteResult? {
        // Check cache first
        if let cachedResult = RouteCacheService.shared.getCachedRoute(from: userLocation, to: destination) {
            return cachedResult
        }
        // Create requests for both driving and walking routes
        let drivingRequest = MKDirections.Request()
        drivingRequest.source = MKMapItem(placemark: MKPlacemark(coordinate: userLocation.coordinate))
        drivingRequest.destination = MKMapItem(placemark: MKPlacemark(coordinate: destination.coordinate))
        drivingRequest.transportType = .automobile
        drivingRequest.requestsAlternateRoutes = false
        
        let walkingRequest = MKDirections.Request()
        walkingRequest.source = MKMapItem(placemark: MKPlacemark(coordinate: userLocation.coordinate))
        walkingRequest.destination = MKMapItem(placemark: MKPlacemark(coordinate: destination.coordinate))
        walkingRequest.transportType = .walking
        walkingRequest.requestsAlternateRoutes = false
        
        do {
            DebugConfig.debugPrint("🗺️ Calculating routes from \(userLocation.coordinate) to \(destination.coordinate)")
            
            // Calculate both routes concurrently
            async let drivingResponse = MKDirections(request: drivingRequest).calculate()
            async let walkingResponse = MKDirections(request: walkingRequest).calculate()
            
            let (drivingResult, walkingResult) = try await (drivingResponse, walkingResponse)
            
            if let drivingRoute = drivingResult.routes.first,
               let walkingRoute = walkingResult.routes.first {
                let result = RouteResult(
                    distance: drivingRoute.distance,
                    travelTime: drivingRoute.expectedTravelTime,
                    walkingTime: walkingRoute.expectedTravelTime,
                    isEstimated: false
                )
                DebugConfig.debugPrint("🚗 Driving: \(result.formattedDistance), \(result.formattedTravelTime)")
                DebugConfig.debugPrint("🚶 Walking: \(result.formattedWalkingTime)")
                
                // Cache the result
                RouteCacheService.shared.setCachedRoute(from: userLocation, to: destination, result: result)
                
                return result
            }
        } catch {
            DebugConfig.debugPrint("❌ Route calculation failed: \(error.localizedDescription)")
            // Fall back to straight-line distance if routing fails
            let straightLineDistance = userLocation.distance(from: destination)
            DebugConfig.debugPrint("📏 Falling back to straight-line distance: \(String(format: "%.1f km", straightLineDistance / 1000))")
            let fallbackResult = RouteResult(
                distance: straightLineDistance,
                travelTime: 0,
                walkingTime: 0,
                isEstimated: true
            )
            
            // Cache the fallback result too
            RouteCacheService.shared.setCachedRoute(from: userLocation, to: destination, result: fallbackResult)
            
            return fallbackResult
        }
        
        return nil
    }
    
    /// Calculate driving routes to multiple destinations concurrently
    static func calculateDrivingRoutes(from userLocation: CLLocation, to destinations: [CLLocation]) async -> [RouteResult?] {
        // Calculate routes concurrently but with a limit to avoid overwhelming the service
        let maxConcurrent = 5
        var results: [RouteResult?] = []
        
        for chunk in destinations.chunked(into: maxConcurrent) {
            let chunkResults = await withTaskGroup(of: (Int, RouteResult?).self) { group in
                for (index, destination) in chunk.enumerated() {
                    group.addTask {
                        let result = await calculateDrivingRoute(from: userLocation, to: destination)
                        return (index, result)
                    }
                }
                
                var chunkResults: [(Int, RouteResult?)] = []
                for await result in group {
                    chunkResults.append(result)
                }
                return chunkResults.sorted { $0.0 < $1.0 }.map { $0.1 }
            }
            
            results.append(contentsOf: chunkResults)
        }
        
        return results
    }
}

// Helper extension to chunk arrays
extension Array {
    func chunked(into size: Int) -> [[Element]] {
        return stride(from: 0, to: count, by: size).map {
            Array(self[$0..<Swift.min($0 + size, count)])
        }
    }
}
