import Foundation

class ScheduleService {
    static private var cachedSchedules: [PharmacySchedule]?
    static private let pdfService = PDFProcessingService()
    
    static func loadSchedules(from url: URL) -> [PharmacySchedule] {
        // Return cached schedules if available
        if let cached = cachedSchedules {
            return cached
        }
        
        // Load and cache if not available
        let schedules = pdfService.loadPharmacies(from: url)
        cachedSchedules = schedules
        return schedules
    }
    
    static func clearCache() {
        cachedSchedules = nil
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
