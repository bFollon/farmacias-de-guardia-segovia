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
        date >= start && date <= end
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
}
