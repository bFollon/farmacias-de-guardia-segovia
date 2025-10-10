/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import Foundation

public struct PharmacySchedule: Codable {
    public let date: DutyDate
    public let shifts: [DutyTimeSpan: [Pharmacy]]
    
    public init(date: DutyDate, shifts: [DutyTimeSpan: [Pharmacy]]) {
        self.date = date
        self.shifts = shifts
    }
    
    // Custom Codable implementation for Dictionary with non-String keys
    enum CodingKeys: String, CodingKey {
        case date
        case shifts
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        date = try container.decode(DutyDate.self, forKey: .date)
        
        // Decode shifts as an array of key-value pairs
        let shiftsArray = try container.decode([[String: [Pharmacy]]].self, forKey: .shifts)
        var decodedShifts: [DutyTimeSpan: [Pharmacy]] = [:]
        
        for shiftDict in shiftsArray {
            for (key, pharmacies) in shiftDict {
                if let timeSpan = DutyTimeSpan.from(string: key) {
                    decodedShifts[timeSpan] = pharmacies
                }
            }
        }
        shifts = decodedShifts
    }
    
    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(date, forKey: .date)
        
        // Encode shifts as an array of key-value pairs
        let shiftsArray = shifts.map { [$0.key.toString(): $0.value] }
        try container.encode(shiftsArray, forKey: .shifts)
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
