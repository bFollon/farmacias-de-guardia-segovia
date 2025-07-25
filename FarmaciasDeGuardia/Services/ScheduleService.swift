import Foundation

class ScheduleService {
    static private var cachedSchedules: [PharmacySchedule]?
    static private let pdfService = PDFProcessingService()
    static private var cacheInvalidationTimer: Timer?
    
    static func loadSchedules(from url: URL) -> [PharmacySchedule] {
        // Return cached schedules if available and timer is still valid
        if let cached = cachedSchedules {
            return cached
        }
        
        // Load and cache if not available
        let schedules = pdfService.loadPharmacies(from: url)
        cachedSchedules = schedules
        scheduleNextInvalidation()
        return schedules
    }
    
    static func clearCache() {
        cachedSchedules = nil
        cacheInvalidationTimer?.invalidate()
        cacheInvalidationTimer = nil
    }
    
    private static func scheduleNextInvalidation() {
        // Cancel any existing timer
        cacheInvalidationTimer?.invalidate()
        
        // Get current timestamp
        let now = Date()
        let currentTimestamp = now.timeIntervalSince1970
        
        // Get the duty time info for current timestamp
        let dutyInfo = DutyDate.dutyTimeInfoForTimestamp(currentTimestamp)
        
        // Calculate next shift change time
        let calendar = Calendar.current
        var components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: now)
        
        // Set components for next shift change
        if dutyInfo.shiftType == .day {
            // If we're in day shift, next change is at 22:00
            components.hour = 22
            components.minute = 0
        } else {
            // If we're in night shift, next change is at 10:15
            // If it's after midnight, it's the same day, if before midnight, it's next day
            if calendar.component(.hour, from: now) < 10 || 
               (calendar.component(.hour, from: now) == 10 && calendar.component(.minute, from: now) < 15) {
                // Same day at 10:15
                components.hour = 10
                components.minute = 15
            } else {
                // Next day at 10:15
                components.day! += 1
                components.hour = 10
                components.minute = 15
            }
        }
        
        // Create date for next shift change
        if let nextShiftChange = calendar.date(from: components) {
            // Schedule cache invalidation
            cacheInvalidationTimer = Timer.scheduledTimer(withTimeInterval: nextShiftChange.timeIntervalSince(now), repeats: false) { _ in
                clearCache()
            }
        }
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
    
    static func getCurrentDateTime() -> String {
        let today = Date()
        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "es_ES")
        dateFormatter.setLocalizedDateFormatFromTemplate("EEEE d MMMM")
        return "\(dateFormatter.string(from: today)) · \(today.formatted(.dateTime.hour().minute()))"
    }
}
