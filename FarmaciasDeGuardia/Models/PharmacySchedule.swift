import Foundation

public struct PharmacySchedule {
    public let date: DutyDate
    public let dayShiftPharmacies: [Pharmacy]
    public let nightShiftPharmacies: [Pharmacy]
    
    public init(date: DutyDate, dayShiftPharmacies: [Pharmacy], nightShiftPharmacies: [Pharmacy]) {
        self.date = date
        self.dayShiftPharmacies = dayShiftPharmacies
        self.nightShiftPharmacies = nightShiftPharmacies
    }
}
