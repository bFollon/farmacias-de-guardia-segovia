import Foundation

public struct DutyDate {
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
        print("\nAttempting to parse date: '\(dateString)'")
        
        let datePattern = "\\b(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),\\s(\\d{1,2})\\sde\\s(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\\s(\\d{4}))?\\b"
        
        print("Using pattern: \(datePattern)")
        
        guard let regex = try? NSRegularExpression(pattern: datePattern, options: []),
              let match = regex.firstMatch(in: dateString, options: [], range: NSRange(dateString.startIndex..., in: dateString)) else {
            print("Failed to match regex pattern")
            return nil
        }
        
        print("Number of capture groups: \(match.numberOfRanges)")
        for i in 0..<match.numberOfRanges {
            let range = match.range(at: i)
            print("Group \(i) - location: \(range.location), length: \(range.length)")
            if range.location != NSNotFound, let stringRange = Range(range, in: dateString) {
                print("Group \(i) value: '\(dateString[stringRange])'")
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
            print("Found year in capture group: \(year ?? -1)")
        } else {
            let currentYear = getCurrentYear()
            // Temporary fix: Only January 1st and 2nd are from next year
            if month.lowercased() == "enero" && (day == 1 || day == 2) {
                year = currentYear + 1
                print("Applied temporary fix for January 1st/2nd: setting year to \(currentYear + 1)")
            } else {
                year = currentYear
                print("No year found, defaulting to \(currentYear)")
            }
        }
        
        print("Parsed result: \(dayOfWeek), \(day) de \(month) \(year ?? 2025)")
        
        return DutyDate(dayOfWeek: String(dayOfWeek), day: day, month: month, year: year)
    }
}
