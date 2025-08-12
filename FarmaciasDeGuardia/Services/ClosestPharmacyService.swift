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
    
    static func findClosestOnDutyPharmacy(
        userLocation: CLLocation,
        at date: Date = Date()
    ) async throws -> ClosestPharmacyResult {
        
        DebugConfig.debugPrint("🔍 Starting search for closest on-duty pharmacy at \(date)")
        
        var allOnDutyPharmacies: [(pharmacy: Pharmacy, region: Region, zbs: ZBS?, timeSpan: DutyTimeSpan)] = []
        
        // Get all regions to search
        let allRegions: [Region] = [.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
        
        for region in allRegions {
            DebugConfig.debugPrint("📍 Checking region: \(region.name)")
            
            if region.id == "segovia-rural" {
                // Handle rural areas with ZBS
                let zbsSchedules = await ZBSScheduleService.getZBSSchedules(for: region) ?? []
                
                for zbs in ZBS.availableZBS {
                    DebugConfig.debugPrint("🏘️ Checking ZBS: \(zbs.name)")
                    
                    // Find schedules for this ZBS
                    for zbsSchedule in zbsSchedules {
                        let zbsPharmacies = zbsSchedule.pharmacies(for: zbs.id)
                        
                        if !zbsPharmacies.isEmpty {
                            // Check if this schedule matches the current date
                            if let scheduleTimestamp = zbsSchedule.date.toTimestamp() {
                                let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
                                if Calendar.current.isDate(scheduleDate, inSameDayAs: date) {
                                    // For rural areas, pharmacies are typically on duty all day
                                    for pharmacy in zbsPharmacies {
                                        allOnDutyPharmacies.append((pharmacy, region, zbs, .fullDay))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Handle regular regions
                let schedules = await ScheduleService.loadSchedules(for: region)
                let onDutyPharmacies = findOnDutyPharmacies(in: schedules, at: date)
                
                for (pharmacy, timeSpan) in onDutyPharmacies {
                    allOnDutyPharmacies.append((pharmacy, region, nil, timeSpan))
                }
            }
        }
        
        guard !allOnDutyPharmacies.isEmpty else {
            DebugConfig.debugPrint("❌ No pharmacies on duty found")
            throw ClosestPharmacyError.noPharmaciesOnDuty
        }
        
        DebugConfig.debugPrint("✅ Found \(allOnDutyPharmacies.count) on-duty pharmacies")
        
        // Calculate distances and find the closest
        var closestResult: ClosestPharmacyResult?
        
        for (pharmacy, region, zbs, timeSpan) in allOnDutyPharmacies {
            if let distance = await pharmacy.distance(from: userLocation) {
                let result = ClosestPharmacyResult(
                    pharmacy: pharmacy,
                    distance: distance,
                    region: region,
                    zbs: zbs,
                    timeSpan: timeSpan
                )
                
                if closestResult == nil || distance < closestResult!.distance {
                    closestResult = result
                }
            }
        }
        
        guard let result = closestResult else {
            DebugConfig.debugPrint("❌ Could not calculate distances to pharmacies")
            throw ClosestPharmacyError.geocodingFailed
        }
        
        DebugConfig.debugPrint("🎯 Closest pharmacy: \(result.pharmacy.name) at \(result.formattedDistance)")
        return result
    }
    
    private static func findOnDutyPharmacies(
        in schedules: [PharmacySchedule],
        at date: Date
    ) -> [(pharmacy: Pharmacy, timeSpan: DutyTimeSpan)] {
        
        var onDutyPharmacies: [(pharmacy: Pharmacy, timeSpan: DutyTimeSpan)] = []
        
        // Find schedule for the current date
        if let todaySchedule = ScheduleService.findSchedule(for: date, in: schedules) {
            DebugConfig.debugPrint("📅 Found schedule for today")
            
            // Check each time span in the schedule
            for (timeSpan, pharmacies) in todaySchedule.shifts {
                if timeSpan.contains(date) {
                    DebugConfig.debugPrint("⏰ Found \(pharmacies.count) pharmacies on duty for timespan \(timeSpan.displayName)")
                    for pharmacy in pharmacies {
                        onDutyPharmacies.append((pharmacy, timeSpan))
                    }
                }
            }
        }
        
        return onDutyPharmacies
    }
}
