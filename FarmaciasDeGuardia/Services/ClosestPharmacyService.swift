import Foundation
import CoreLocation

struct ClosestPharmacyResult {
    let pharmacy: Pharmacy
    let distance: CLLocationDistance
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
                                if isPharmacyCurrentlyOpen(pharmacy, at: date) {
                                    allOnDutyPharmacies.append((pharmacy, region, zbs, .fullDay))
                                    actuallyOpenPharmacies += 1
                                    DebugConfig.debugPrint("   ✅ \(pharmacy.name) - OPEN NOW")
                                } else {
                                    DebugConfig.debugPrint("   ❌ \(pharmacy.name) - CLOSED (outside operating hours)")
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
                                if isPharmacyCurrentlyOpen(pharmacy, at: date) {
                                    allOnDutyPharmacies.append((pharmacy, region, nil, timeSpan))
                                    actuallyOpenPharmacies += 1
                                    DebugConfig.debugPrint("   ✅ \(pharmacy.name) - OPEN NOW")
                                } else {
                                    DebugConfig.debugPrint("   ❌ \(pharmacy.name) - CLOSED (outside operating hours)")
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
        DebugConfig.debugPrint("🚀 REAL OPTIMIZATION: Only geocoding \(allOnDutyPharmacies.count) open pharmacies (filtered from on-duty list)")
        
        // Calculate distances and find the closest
        var distanceResults: [(result: ClosestPharmacyResult, distance: CLLocationDistance)] = []
        
        for (pharmacy, region, zbs, timeSpan) in allOnDutyPharmacies {
            DebugConfig.debugPrint("📏 Geocoding and calculating distance to: \(pharmacy.name)")
            
            // Use the enhanced geocoding service
            if let coordinates = await GeocodingService.getCoordinatesForPharmacy(pharmacy) {
                let distance = userLocation.distance(from: coordinates)
                
                let result = ClosestPharmacyResult(
                    pharmacy: pharmacy,
                    distance: distance,
                    region: region,
                    zbs: zbs,
                    timeSpan: timeSpan
                )
                distanceResults.append((result, distance))
                DebugConfig.debugPrint("   📏 \(pharmacy.name): \(result.formattedDistance) (\(result.regionDisplayName))")
            } else {
                DebugConfig.debugPrint("   ❌ Could not get coordinates for: \(pharmacy.name)")
            }
        }
        
        // Sort by distance and log top candidates
        distanceResults.sort { $0.distance < $1.distance }
        
        DebugConfig.debugPrint("🏆 Top 5 closest pharmacies:")
        for (index, (result, distance)) in distanceResults.prefix(5).enumerated() {
            DebugConfig.debugPrint("   \(index + 1). \(result.pharmacy.name): \(result.formattedDistance) (\(result.regionDisplayName))")
        }
        
        guard let closest = distanceResults.first else {
            DebugConfig.debugPrint("❌ Could not calculate distances to any pharmacies")
            throw ClosestPharmacyError.geocodingFailed
        }
        
        DebugConfig.debugPrint("🎯 OPTIMIZED WINNER: \(closest.result.pharmacy.name) at \(closest.result.formattedDistance) from \(closest.result.regionDisplayName)")
        return closest.result
    }
    
    /// Helper function to convert month names to numbers (from ZBSScheduleView)
    private static func monthNameToNumber(_ monthName: String) -> Int? {
        let monthNames = ["ene": 1, "feb": 2, "mar": 3, "abr": 4, "may": 5, "jun": 6,
                         "jul": 7, "ago": 8, "sep": 9, "oct": 10, "nov": 11, "dic": 12]
        return monthNames[monthName.lowercased()]
    }
    
    /// Check if a pharmacy is currently open based on its operating hours
    private static func isPharmacyCurrentlyOpen(_ pharmacy: Pharmacy, at date: Date) -> Bool {
        let calendar = Calendar.current
        let hour = calendar.component(.hour, from: date)
        
        let info = pharmacy.additionalInfo ?? ""
        
        // Check operating hours based on additional info
        if info.contains("24h") {
            return true // 24h pharmacies are always open
        } else if info.contains("10h-22h") {
            return hour >= 10 && hour < 22 // Extended hours
        } else if info.contains("10h-20h") {
            return hour >= 10 && hour < 20 // Standard hours
        }
        
        // Default assumption for pharmacies without specific hours info
        // Most non-rural pharmacies are 24h
        return true
    }
}
