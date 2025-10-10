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

public struct DutyDate: Codable {
    public let dayOfWeek: String
    public let day: Int
    public let month: String
    public let year: Int?
    
    public init(dayOfWeek: String, day: Int, month: String, year: Int?) {
        self.dayOfWeek = dayOfWeek
        self.day = day
        self.month = month
        self.year = year
    }
}

extension DutyDate {
    public func toTimestamp() -> TimeInterval? {
        let calendar = Calendar.current
        let year = self.year ?? DutyDate.getCurrentYear()
        
        // Convert Spanish month to number (1-12)
        guard let month = DutyDate.monthToNumber(self.month) else { return nil }
        
        // Create date components
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = self.day
        // Set to start of day
        components.hour = 0
        components.minute = 0
        components.second = 0
        
        // Convert to date
        guard let date = calendar.date(from: components) else { return nil }
        return date.timeIntervalSince1970
    }
    
    public static func monthToNumber(_ month: String) -> Int? {
        let months = [
            "enero": 1, "febrero": 2, "marzo": 3, "abril": 4,
            "mayo": 5, "junio": 6, "julio": 7, "agosto": 8,
            "septiembre": 9, "octubre": 10, "noviembre": 11, "diciembre": 12
        ]
        return months[month.lowercased()]
    }
    
    public static func getCurrentYear() -> Int {
        let calendar = Calendar.current
        return calendar.component(.year, from: Date())
    }
    
    public static func parse(_ dateString: String) -> DutyDate? {
        DebugConfig.debugPrint("\nAttempting to parse date: '\(dateString)'")
        
        let datePattern = "\\b(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),\\s(\\d{1,2})\\sde\\s(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\\s(\\d{4}))?\\b"
        
        DebugConfig.debugPrint("Using pattern: \(datePattern)")
        
        guard let regex = try? NSRegularExpression(pattern: datePattern, options: []),
              let match = regex.firstMatch(in: dateString, options: [], range: NSRange(dateString.startIndex..., in: dateString)) else {
            DebugConfig.debugPrint("Failed to match regex pattern")
            return nil
        }
        
        DebugConfig.debugPrint("Number of capture groups: \(match.numberOfRanges)")
        for i in 0..<match.numberOfRanges {
            let range = match.range(at: i)
            DebugConfig.debugPrint("Group \(i) - location: \(range.location), length: \(range.length)")
            if range.location != NSNotFound, let stringRange = Range(range, in: dateString) {
                DebugConfig.debugPrint("Group \(i) value: '\(dateString[stringRange])'")
            }
        }
        
        let dayOfWeek = dateString.split(separator: ",")[0].trimmingCharacters(in: .whitespaces)
        let dayRange = Range(match.range(at: 1), in: dateString)!
        let monthRange = Range(match.range(at: 2), in: dateString)!
        let day = Int(String(dateString[dayRange]))!
        let month = String(dateString[monthRange])
        
        var year: Int? = nil
        if match.numberOfRanges > 3, match.range(at: 3).location != NSNotFound {
            let yearRange = Range(match.range(at: 3), in: dateString)!
            year = Int(String(dateString[yearRange]))
            DebugConfig.debugPrint("Found year in capture group: \(year ?? -1)")
        } else {
            let currentYear = getCurrentYear()
            // Temporary fix: Only January 1st and 2nd are from next year
            if month.lowercased() == "enero" && (day == 1 || day == 2) {
                year = currentYear + 1
                DebugConfig.debugPrint("Applied temporary fix for January 1st/2nd: setting year to \(currentYear + 1)")
            } else {
                year = currentYear
                DebugConfig.debugPrint("No year found, defaulting to \(currentYear)")
            }
        }
        
        DebugConfig.debugPrint("Parsed result: \(dayOfWeek), \(day) de \(month) \(year ?? 2025)")
        
        return DutyDate(dayOfWeek: String(dayOfWeek), day: day, month: month, year: year)
    }
}

extension DutyDate {
    public enum ShiftType {
        case day    // 10:15 to 22:00 same day
        case night  // 22:00 to 10:15 next day
    }
    
    public struct DutyTimeInfo {
        public let date: DutyDate
        public let shiftType: ShiftType
    }
    
    /// Determines which duty schedule should be active for a given timestamp
    /// For example, at 00:05 on July 26th, we need the night shift from July 25th
    public static func dutyTimeInfoForTimestamp(_ timestamp: TimeInterval) -> DutyTimeInfo {
        let date = Date(timeIntervalSince1970: timestamp)
        let calendar = Calendar.current
        
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        guard let hour = components.hour, let minute = components.minute else {
            fatalError("Could not extract time components")
        }
        
        // Convert current time to minutes since midnight for easier comparison
        let currentTimeInMinutes = hour * 60 + minute
        let morningTransitionInMinutes = 10 * 60 + 15  // 10:15
        let eveningTransitionInMinutes = 22 * 60       // 22:00
        
        // If we're between 00:00 and 10:15, we need previous day's night shift
        if currentTimeInMinutes < morningTransitionInMinutes {
            // Get previous day's date
            guard let previousDay = calendar.date(byAdding: .day, value: -1, to: date) else {
                fatalError("Could not calculate previous day")
            }
            
            let prevComponents = calendar.dateComponents([.year, .month, .day], from: previousDay)
            return DutyTimeInfo(
                date: DutyDate(
                    dayOfWeek: "", // This will be filled in by the data
                    day: prevComponents.day ?? 0,
                    month: "", // This will be matched by timestamp
                    year: prevComponents.year
                ),
                shiftType: .night
            )
        }
        
        // If we're between 10:15 and 22:00, we need current day's day shift
        if currentTimeInMinutes < eveningTransitionInMinutes {
            return DutyTimeInfo(
                date: DutyDate(
                    dayOfWeek: "", // This will be filled in by the data
                    day: components.day ?? 0,
                    month: "", // This will be matched by timestamp
                    year: components.year
                ),
                shiftType: .day
            )
        }
        
        // If we're between 22:00 and 23:59, we need current day's night shift
        return DutyTimeInfo(
            date: DutyDate(
                dayOfWeek: "", // This will be filled in by the data
                day: components.day ?? 0,
                month: "", // This will be matched by timestamp
                year: components.year
            ),
            shiftType: .night
        )
    }
}
