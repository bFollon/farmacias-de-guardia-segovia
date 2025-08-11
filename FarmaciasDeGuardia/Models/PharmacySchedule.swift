import Foundation

public struct PharmacySchedule {
    public let date: DutyDate
    public let shifts: [DutyTimeSpan: [Pharmacy]]
    
    public init(date: DutyDate, shifts: [DutyTimeSpan: [Pharmacy]]) {
        self.date = date
        self.shifts = shifts
    }
    
    // Convenience initializer for backward compatibility during transition
    public init(date: DutyDate, dayShiftPharmacies: [Pharmacy], nightShiftPharmacies: [Pharmacy]) {
        self.date = date
        self.shifts = [
            .capitalDay: dayShiftPharmacies,
            .capitalNight: nightShiftPharmacies
        ]
    }
    
    // Backward compatibility properties (can be removed after UI is updated)
    public var dayShiftPharmacies: [Pharmacy] {
        // Try capital-specific shifts first, then fall back to full day
        shifts[.capitalDay] ?? shifts[.fullDay] ?? []
    }
    
    public var nightShiftPharmacies: [Pharmacy] {
        // Try capital-specific shifts first, then fall back to full day (for 24-hour regions)
        shifts[.capitalNight] ?? shifts[.fullDay] ?? []
    }
}
