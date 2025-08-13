import Foundation
import MapKit
import CoreLocation

struct RouteResult {
    let distance: CLLocationDistance // in meters
    let estimatedTravelTime: TimeInterval // in seconds
    
    var formattedDistance: String {
        if distance < 1000 {
            return "\(Int(distance)) m"
        } else {
            return String(format: "%.1f km", distance / 1000)
        }
    }
    
    var formattedTravelTime: String {
        let minutes = Int(estimatedTravelTime / 60)
        if minutes < 1 {
            return "< 1 min"
        } else {
            return "\(minutes) min"
        }
    }
}

class RoutingService {
    
    /// Calculate driving route from user location to destination
    static func calculateDrivingRoute(from userLocation: CLLocation, to destination: CLLocation) async -> RouteResult? {
        let request = MKDirections.Request()
        
        // Set source and destination
        request.source = MKMapItem(placemark: MKPlacemark(coordinate: userLocation.coordinate))
        request.destination = MKMapItem(placemark: MKPlacemark(coordinate: destination.coordinate))
        
        // Request driving directions
        request.transportType = .automobile
        request.requestsAlternateRoutes = false // We only need the best route
        
        let directions = MKDirections(request: request)
        
        do {
            DebugConfig.debugPrint("🗺️ Calculating route from \(userLocation.coordinate) to \(destination.coordinate)")
            let response = try await directions.calculate()
            
            if let route = response.routes.first {
                let result = RouteResult(
                    distance: route.distance,
                    estimatedTravelTime: route.expectedTravelTime
                )
                DebugConfig.debugPrint("🚗 Route calculated: \(result.formattedDistance), \(result.formattedTravelTime)")
                return result
            }
        } catch {
            DebugConfig.debugPrint("❌ Route calculation failed: \(error.localizedDescription)")
            // Fall back to straight-line distance if routing fails
            let straightLineDistance = userLocation.distance(from: destination)
            DebugConfig.debugPrint("📏 Falling back to straight-line distance: \(String(format: "%.1f km", straightLineDistance / 1000))")
            return RouteResult(
                distance: straightLineDistance,
                estimatedTravelTime: straightLineDistance / 1000 * 60 // Rough estimate: 1 km/min
            )
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
