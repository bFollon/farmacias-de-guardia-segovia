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
        shifts[.capitalDay] ?? []
    }
    
    public var nightShiftPharmacies: [Pharmacy] {
        shifts[.capitalNight] ?? []
    }
}
