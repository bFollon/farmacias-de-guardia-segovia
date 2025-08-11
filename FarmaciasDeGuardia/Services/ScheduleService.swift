import Foundation

class ScheduleService {
    // Cache by region ID
    static private var cachedSchedules: [String: [PharmacySchedule]] = [:]
    static private let pdfService = PDFProcessingService()
    
    static func loadSchedules(for region: Region, forceRefresh: Bool = false) async -> [PharmacySchedule] {
        // Return cached schedules if available and not forcing refresh
        if let cached = cachedSchedules[region.id], !forceRefresh {
            print("ScheduleService: Using cached schedules for region \(region.name)")
            return cached
        }
        
        // Load and cache if not available or force refresh requested
        print("ScheduleService: Loading schedules from PDF for region \(region.name)...")
        let schedules = await pdfService.loadPharmacies(for: region, forceRefresh: forceRefresh)
        cachedSchedules[region.id] = schedules
        print("ScheduleService: Successfully cached \(schedules.count) schedules for \(region.name)")
        
        // Print a sample schedule for verification
        if let sampleSchedule = schedules.first {
            print("\nSample schedule for \(region.name):")
            print("Date: \(sampleSchedule.date)")
            
            print("\nDay Shift Pharmacies:")
            for pharmacy in sampleSchedule.dayShiftPharmacies {
                print("- \(pharmacy.name)")
                print("  Address: \(pharmacy.address)")
                print("  Phone: \(pharmacy.formattedPhone)")
                if let info = pharmacy.additionalInfo {
                    print("  Additional Info: \(info)")
                }
            }
            
            print("\nNight Shift Pharmacies:")
            for pharmacy in sampleSchedule.nightShiftPharmacies {
                print("- \(pharmacy.name)")
                print("  Address: \(pharmacy.address)")
                print("  Phone: \(pharmacy.formattedPhone)")
                if let info = pharmacy.additionalInfo {
                    print("  Additional Info: \(info)")
                }
            }
            print("")
        }
        
        return schedules
    }
    
    // Keep backward compatibility for direct URL loading
    static func loadSchedules(from url: URL, forceRefresh: Bool = false) async -> [PharmacySchedule] {
        // For direct URL loading, treat as Segovia Capital
        return await loadSchedules(for: .segoviaCapital, forceRefresh: forceRefresh)
    }
    
    static func clearCache() {
        cachedSchedules.removeAll()
    }
    static func findCurrentSchedule(in schedules: [PharmacySchedule]) -> (PharmacySchedule, DutyDate.ShiftType)? {
        let now = Date()
        let currentTimestamp = now.timeIntervalSince1970
        
        // Get the duty time info for current timestamp
        let dutyInfo = DutyDate.dutyTimeInfoForTimestamp(currentTimestamp)
        
        // Find the schedule for the required date (using dutyInfo.date)
        guard let schedule = schedules.first(where: { schedule in
            // Both dates should have the same day and month
            return schedule.date.day == dutyInfo.date.day &&
                   schedule.date.year == dutyInfo.date.year &&
                   DutyDate.monthToNumber(schedule.date.month) == Calendar.current.component(.month, from: now)
        }) else {
            return nil
        }
        
        return (schedule, dutyInfo.shiftType)
    }
    
    /// Region-aware version that detects shift pattern from schedule data
    static func findCurrentSchedule(in schedules: [PharmacySchedule], for region: Region) -> (PharmacySchedule, DutyTimeSpan)? {
        let now = Date()
        let calendar = Calendar.current
        
        // First, find a sample schedule to determine the shift pattern for this region
        guard let sampleSchedule = schedules.first else { return nil }
        
        // Detect shift pattern based on what shifts are available in the schedule
        let has24HourShifts = sampleSchedule.shifts[.fullDay] != nil
        let hasDayNightShifts = sampleSchedule.shifts[.capitalDay] != nil || sampleSchedule.shifts[.capitalNight] != nil
        
        if has24HourShifts {
            // For 24-hour regions, always use current day and full-day shift
            guard let schedule = schedules.first(where: { schedule in
                guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
                let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
                return calendar.isDate(scheduleDate, inSameDayAs: now)
            }) else {
                return nil
            }
            return (schedule, .fullDay)
        } else if hasDayNightShifts {
            // For day/night regions, use the existing logic
            if let legacyResult = findCurrentSchedule(in: schedules) {
                let timeSpan: DutyTimeSpan = legacyResult.1 == .day ? .capitalDay : .capitalNight
                return (legacyResult.0, timeSpan)
            }
            return nil
        } else {
            // Fallback: treat as 24-hour if we can't determine
            guard let schedule = schedules.first(where: { schedule in
                guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
                let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
                return calendar.isDate(scheduleDate, inSameDayAs: now)
            }) else {
                return nil
            }
            return (schedule, .fullDay)
        }
    }
    
    static func getCurrentDateTime() -> String {
        let today = Date()
        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "es_ES")
        dateFormatter.setLocalizedDateFormatFromTemplate("EEEE d MMMM")
        return "\(dateFormatter.string(from: today)) · Ahora"
    }
    static func findSchedule(for date: Date, in schedules: [PharmacySchedule]) -> PharmacySchedule? {
        let calendar = Calendar.current
        return schedules.first { schedule in
            guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
            let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
            return calendar.isDate(scheduleDate, inSameDayAs: date)
        }
    }
}
