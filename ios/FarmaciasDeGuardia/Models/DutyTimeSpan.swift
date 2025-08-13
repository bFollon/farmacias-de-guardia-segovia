import Foundation

public struct DutyTimeSpan: Equatable, Hashable {
    public let start: Date
    public let end: Date
    
    public init(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        let calendar = Calendar.current
        let now = Date()
        
        // Create start date components
        var startComponents = calendar.dateComponents([.year, .month, .day], from: now)
        startComponents.hour = startHour
        startComponents.minute = startMinute
        
        // Create end date components
        var endComponents = calendar.dateComponents([.year, .month, .day], from: now)
        endComponents.hour = endHour
        endComponents.minute = endMinute
        
        // If end time is earlier than start time, it must be for the next day
        if endHour < startHour || (endHour == startHour && endMinute < startMinute) {
            endComponents.day! += 1
        }
        
        // Initialize dates, defaulting to now if date creation fails (should never happen)
        self.start = calendar.date(from: startComponents) ?? now
        self.end = calendar.date(from: endComponents) ?? now
    }
    
    /// Returns true if the span crosses over to the next day
    public var spansMultipleDays: Bool {
        !Calendar.current.isDate(start, inSameDayAs: end)
    }
    
    /// Checks if the given date falls within this duty time span
    public func contains(_ date: Date) -> Bool {
        if spansMultipleDays {
            // For cross-midnight spans, we need to check time of day, not absolute dates
            let calendar = Calendar.current
            let components = calendar.dateComponents([.hour, .minute], from: date)
            return containsTimeOfDay(hour: components.hour ?? 0, minute: components.minute ?? 0)
        } else {
            // For same-day spans, simple date comparison works
            return date >= start && date <= end
        }
    }
    
    /// Checks if a given time of day (hour and minute) falls within this duty time span
    /// This method handles cross-midnight spans correctly
    public func containsTimeOfDay(hour: Int, minute: Int) -> Bool {
        let calendar = Calendar.current
        let timeInMinutes = hour * 60 + minute
        
        let startComponents = calendar.dateComponents([.hour, .minute], from: start)
        let endComponents = calendar.dateComponents([.hour, .minute], from: end)
        
        let startMinutes = (startComponents.hour ?? 0) * 60 + (startComponents.minute ?? 0)
        let endMinutes = (endComponents.hour ?? 0) * 60 + (endComponents.minute ?? 0)
        
        if spansMultipleDays {
            // For spans that cross midnight (e.g., 22:00 - 10:15)
            return timeInMinutes >= startMinutes || timeInMinutes <= endMinutes
        } else {
            // For spans within the same day (e.g., 10:15 - 22:00)
            return timeInMinutes >= startMinutes && timeInMinutes <= endMinutes
        }
    }
    
    /// A human-readable representation of the time span (e.g. "10:15 - 22:00")
    public var displayName: String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return "\(formatter.string(from: start)) - \(formatter.string(from: end))"
    }
}

// MARK: - Common Time Spans
public extension DutyTimeSpan {
    /// Segovia Capital daytime shift (10:15 - 22:00)
    static let capitalDay = DutyTimeSpan(startHour: 10, startMinute: 15, endHour: 22, endMinute: 0)
    
    /// Segovia Capital nighttime shift (22:00 - 10:15 next day)
    static let capitalNight = DutyTimeSpan(startHour: 22, startMinute: 0, endHour: 10, endMinute: 15)
    
    /// 24-hour shift used by Cuéllar and El Espinar (00:00 - 23:59)
    static let fullDay = DutyTimeSpan(startHour: 0, startMinute: 0, endHour: 23, endMinute: 59)
    
    /// Rural daytime shift for standard hours (10:00 - 20:00)
    static let ruralDaytime = DutyTimeSpan(startHour: 10, startMinute: 0, endHour: 20, endMinute: 0)
    
    /// Rural extended daytime shift (10:00 - 22:00)
    static let ruralExtendedDaytime = DutyTimeSpan(startHour: 10, startMinute: 0, endHour: 22, endMinute: 0)
}
