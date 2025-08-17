import Foundation
import CoreLocation
import MapKit

struct ClosestPharmacyResult {
    let pharmacy: Pharmacy
    let distance: CLLocationDistance
    let estimatedTravelTime: TimeInterval?
    let estimatedWalkingTime: TimeInterval?
    let region: Region
    let zbs: ZBS?
    let timeSpan: DutyTimeSpan
    
    var formattedDistance: String {
        if distance < 1000 {
            return "\(Int(distance)) m"
        } else {
            return String(format: "%.1f km", distance / 1000)
        }
    }
    
    var formattedTravelTime: String {
        guard let travelTime = estimatedTravelTime else { return "" }
        let minutes = Int(travelTime / 60)
        if minutes < 1 {
            return "< 1 min"
        } else {
            return "\(minutes) min"
        }
    }
    
    var formattedWalkingTime: String {
        guard let walkingTime = estimatedWalkingTime else { return "" }
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
    
    var regionDisplayName: String {
        if let zbs = zbs {
            return "\(region.name) - \(zbs.name)"
        } else {
            return region.name
        }
    }
}

class ClosestPharmacyService {
    
    enum ClosestPharmacyError: LocalizedError {
        case noPharmaciesOnDuty
        case geocodingFailed
        case noLocationPermission
        
        var errorDescription: String? {
            switch self {
            case .noPharmaciesOnDuty:
                return "No hay farmacias de guardia disponibles en este momento"
            case .geocodingFailed:
                return "No se pudo determinar la ubicación de las farmacias"
            case .noLocationPermission:
                return "Se necesita acceso a la ubicación para encontrar la farmacia más cercana"
            }
        }
    }
    
    /// Optimized version that leverages existing UI logic to find only currently on-duty pharmacies
    static func findClosestOnDutyPharmacy(
        userLocation: CLLocation,
        at date: Date = Date()
    ) async throws -> ClosestPharmacyResult {
        
        DebugConfig.debugPrint("🎯 OPTIMIZED search for closest on-duty pharmacy at \(date)")
        DebugConfig.debugPrint("📍 User location: \(userLocation.coordinate.latitude), \(userLocation.coordinate.longitude)")
        
        var allOnDutyPharmacies: [(pharmacy: Pharmacy, region: Region, zbs: ZBS?, timeSpan: DutyTimeSpan)] = []
        
        // Get all regions to search (same as existing UI)
        let allRegions: [Region] = [.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
        
        for region in allRegions {
            DebugConfig.debugPrint("🌍 Checking region: \(region.name)")
            
            if region.id == "segovia-rural" {
                // Handle rural areas using existing ZBS logic from ZBSScheduleView
                DebugConfig.debugPrint("🏘️ Processing rural ZBS areas")
                
                let zbsSchedules = await ZBSScheduleService.getZBSSchedules(for: region) ?? []
                DebugConfig.debugPrint("📅 Found \(zbsSchedules.count) ZBS schedules")
                
                // Find today's ZBS schedule (same logic as ZBSScheduleView)
                let calendar = Calendar.current
                let selectedComponents = calendar.dateComponents([.day, .month, .year], from: date)
                
                if let todaySchedule = zbsSchedules.first(where: { schedule in
                    let scheduleDay = schedule.date.day
                    let scheduleMonth = monthNameToNumber(schedule.date.month) ?? 0
                    let scheduleYear = schedule.date.year ?? calendar.component(.year, from: date)
                    
                    return scheduleDay == selectedComponents.day &&
                           scheduleMonth == selectedComponents.month &&
                           scheduleYear == selectedComponents.year
                }) {
                    DebugConfig.debugPrint("✅ Found today's ZBS schedule - getting pharmacies")
                    
                    // Get all ZBS areas and their pharmacies
                    for zbs in ZBS.availableZBS {
                        let zbsPharmacies = todaySchedule.pharmacies(for: zbs.id)
                        if !zbsPharmacies.isEmpty {
                            DebugConfig.debugPrint("💊 \(zbs.name): \(zbsPharmacies.count) pharmacies on duty")
                            
                            // Filter by actual operating hours before geocoding
                            var actuallyOpenPharmacies = 0
                            for pharmacy in zbsPharmacies {
                                // Determine the actual timespan for this pharmacy based on its operating hours
                                let pharmacyTimeSpan = getPharmacyTimeSpan(pharmacy)
                                
                                if pharmacyTimeSpan.contains(date) {
                                    allOnDutyPharmacies.append((pharmacy, region, zbs, pharmacyTimeSpan))
                                    actuallyOpenPharmacies += 1
                                    DebugConfig.debugPrint("   ✅ \(pharmacy.name) - OPEN NOW (\(pharmacyTimeSpan.displayName))")
                                } else {
                                    DebugConfig.debugPrint("   ❌ \(pharmacy.name) - CLOSED (\(pharmacyTimeSpan.displayName))")
                                }
                            }
                            DebugConfig.debugPrint("   📊 \(actuallyOpenPharmacies)/\(zbsPharmacies.count) are actually open")
                        }
                    }
                } else {
                    DebugConfig.debugPrint("❌ No ZBS schedule found for today")
                }
            } else {
                // Handle regular regions using existing ScheduleService logic
                let schedules = await ScheduleService.loadSchedules(for: region)
                DebugConfig.debugPrint("📋 Loaded \(schedules.count) schedules for \(region.name)")
                
                // Use the existing ScheduleService.findCurrentSchedule logic
                if let (currentSchedule, timeSpan) = ScheduleService.findCurrentSchedule(in: schedules, for: region) {
                    let formatter = DateFormatter()
                    formatter.dateStyle = .short
                    let scheduleDate = Date(timeIntervalSince1970: currentSchedule.date.toTimestamp() ?? 0)
                    DebugConfig.debugPrint("⏰ Found current schedule: \(formatter.string(from: scheduleDate)), timespan: \(timeSpan)")
                    
                    // OPTIMIZATION: Check if timespan is active BEFORE getting pharmacy list
                    if timeSpan.contains(date) {
                        DebugConfig.debugPrint("✅ Timespan \(timeSpan) is active now - getting pharmacies")
                        
                        // Get on-duty pharmacies for this timespan (only if active)
                        if let onDutyPharmacies = currentSchedule.shifts[timeSpan] {
                            DebugConfig.debugPrint("💊 Found \(onDutyPharmacies.count) pharmacies on duty")
                            
                            // Filter by actual operating hours before geocoding  
                            var actuallyOpenPharmacies = 0
                            for pharmacy in onDutyPharmacies {
                                // For regular regions, the timespan already represents the operating hours
                                if timeSpan.contains(date) {
                                    allOnDutyPharmacies.append((pharmacy, region, nil, timeSpan))
                                    actuallyOpenPharmacies += 1
                                    DebugConfig.debugPrint("   ✅ \(pharmacy.name) - OPEN NOW (\(timeSpan.displayName))")
                                } else {
                                    DebugConfig.debugPrint("   ❌ \(pharmacy.name) - CLOSED (\(timeSpan.displayName))")
                                }
                            }
                            DebugConfig.debugPrint("   📊 \(actuallyOpenPharmacies)/\(onDutyPharmacies.count) are actually open")
                        } else {
                            DebugConfig.debugPrint("❌ No pharmacies found for active timespan \(timeSpan)")
                        }
                    } else {
                        DebugConfig.debugPrint("❌ Timespan \(timeSpan) is NOT active now - skipping geocoding")
                    }
                } else {
                    DebugConfig.debugPrint("❌ No current schedule found for \(region.name)")
                }
            }
        }
        
        guard !allOnDutyPharmacies.isEmpty else {
            DebugConfig.debugPrint("❌ No pharmacies on duty found")
            throw ClosestPharmacyError.noPharmaciesOnDuty
        }
        
        DebugConfig.debugPrint("✅ Found \(allOnDutyPharmacies.count) pharmacies that are actually OPEN right now")
        DebugConfig.debugPrint("🚀 REAL OPTIMIZATION: Only calculating routes for \(allOnDutyPharmacies.count) open pharmacies")
        
        // Calculate driving routes and find the closest
        var routeResults: [(result: ClosestPharmacyResult, distance: CLLocationDistance)] = []
        
        // Get coordinates for all pharmacies first
        var pharmacyCoordinates: [(pharmacy: Pharmacy, region: Region, zbs: ZBS?, timeSpan: DutyTimeSpan, coordinates: CLLocation)] = []
        
        for (pharmacy, region, zbs, timeSpan) in allOnDutyPharmacies {
            DebugConfig.debugPrint("� Getting coordinates for: \(pharmacy.name)")
            
            if let coordinates = await GeocodingService.getCoordinatesForPharmacy(pharmacy) {
                pharmacyCoordinates.append((pharmacy, region, zbs, timeSpan, coordinates))
                DebugConfig.debugPrint("   ✅ Coordinates obtained for \(pharmacy.name)")
            } else {
                DebugConfig.debugPrint("   ❌ Could not get coordinates for: \(pharmacy.name)")
            }
        }
        
        DebugConfig.debugPrint("🗺️ Calculating driving routes to \(pharmacyCoordinates.count) pharmacies...")
        
        // Calculate driving routes to all pharmacies
        for (pharmacy, region, zbs, timeSpan, coordinates) in pharmacyCoordinates {
            DebugConfig.debugPrint("🚗 Calculating route to: \(pharmacy.name)")
            
            if let routeResult = await RoutingService.calculateDrivingRoute(from: userLocation, to: coordinates) {
                let result = ClosestPharmacyResult(
                    pharmacy: pharmacy,
                    distance: routeResult.distance,
                    estimatedTravelTime: routeResult.travelTime > 0 ? routeResult.travelTime : nil,
                    estimatedWalkingTime: routeResult.walkingTime > 0 ? routeResult.walkingTime : nil,
                    region: region,
                    zbs: zbs,
                    timeSpan: timeSpan
                )
                routeResults.append((result, routeResult.distance))
                DebugConfig.debugPrint("   🏆 \(pharmacy.name): \(result.formattedDistance), 🚗\(result.formattedTravelTime) 🚶\(result.formattedWalkingTime) (\(result.regionDisplayName))")
            } else {
                DebugConfig.debugPrint("   ❌ Could not calculate route to: \(pharmacy.name)")
            }
        }
        
        // Sort by driving distance and log top candidates
        routeResults.sort { $0.distance < $1.distance }
        
        DebugConfig.debugPrint("🏆 Top 5 closest pharmacies by driving distance:")
        for (index, (result, distance)) in routeResults.prefix(5).enumerated() {
            DebugConfig.debugPrint("   \(index + 1). \(result.pharmacy.name): \(result.formattedDistance), \(result.formattedTravelTime) (\(result.regionDisplayName))")
        }
        
        guard let closest = routeResults.first else {
            DebugConfig.debugPrint("❌ Could not calculate routes to any pharmacies")
            throw ClosestPharmacyError.geocodingFailed
        }
        
        DebugConfig.debugPrint("🎯 DRIVING WINNER: \(closest.result.pharmacy.name) at \(closest.result.formattedDistance), \(closest.result.formattedTravelTime) from \(closest.result.regionDisplayName)")
        return closest.result
    }
    
    /// Helper function to convert month names to numbers (from ZBSScheduleView)
    private static func monthNameToNumber(_ monthName: String) -> Int? {
        let monthNames = ["ene": 1, "feb": 2, "mar": 3, "abr": 4, "may": 5, "jun": 6,
                         "jul": 7, "ago": 8, "sep": 9, "oct": 10, "nov": 11, "dic": 12]
        return monthNames[monthName.lowercased()]
    }
    
    /// Get the appropriate DutyTimeSpan for a pharmacy based on its operating hours
    private static func getPharmacyTimeSpan(_ pharmacy: Pharmacy) -> DutyTimeSpan {
        let info = pharmacy.additionalInfo ?? ""
        
        // Check operating hours based on additional info and return proper DutyTimeSpan
        if info.contains("24h") {
            return .fullDay // 24h pharmacies use full day span
        } else if info.contains("10h-22h") {
            return .ruralExtendedDaytime // Extended hours (10:00 - 22:00)
        } else if info.contains("10h-20h") {
            return .ruralDaytime // Standard hours (10:00 - 20:00)
        }
        
        // Default assumption for pharmacies without specific hours info
        // Most non-rural pharmacies are 24h
        return .fullDay
    }
}
