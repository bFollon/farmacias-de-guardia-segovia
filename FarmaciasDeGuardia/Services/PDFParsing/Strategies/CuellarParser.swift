import Foundation
import PDFKit

class CuellarParser: PDFParsingStrategy {
    /// Debug flag - when true, prints detailed parsing information
    private let debug = true
    
    // Lookup tables for Spanish names
    private static let weekdays = [
        1: "Domingo",
        2: "Lunes",
        3: "Martes",
        4: "Miércoles",
        5: "Jueves",
        6: "Viernes",
        7: "Sábado"
    ]
    
    private static let months = [
        1: "Enero",
        2: "Febrero",
        3: "Marzo",
        4: "Abril",
        5: "Mayo",
        6: "Junio",
        7: "Julio",
        8: "Agosto",
        9: "Septiembre",
        10: "Octubre",
        11: "Noviembre",
        12: "Diciembre"
    ]
    
    /// Converts a weekday number to Spanish name
    private static func weekdayName(from weekday: Int) -> String {
        return weekdays[weekday] ?? "Unknown"
    }
    
    /// Converts a month number to Spanish name
    private static func monthName(from month: Int) -> String {
        return months[month] ?? "Unknown"
    }
    
    func parseSchedules(from pdfDocument: PDFDocument) -> [PharmacySchedule] {
        var schedules: [PharmacySchedule] = []
        let pageCount = pdfDocument.pageCount
        
        if debug { print("📄 Processing \(pageCount) pages...") }
        
        for pageIndex in 0..<pageCount {
            guard let page = pdfDocument.page(at: pageIndex) else {
                if debug { print("❌ Could not get page \(pageIndex) of PDF") }
                continue
            }
            
            if debug { print("\n📃 Processing page \(pageIndex + 1) of \(pageCount)") }
            
            let content = page.string ?? ""
            let lines = content.components(separatedBy: .newlines)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            
            if debug {
                print("\n📊 Page content structure:")
                for (index, line) in lines.enumerated() {
                    print("Line \(index): '\(line)'")
                }
            }
            
            // Process the table structure for this page
            if let monthSchedules = processPageTable(lines: lines) {
                schedules.append(contentsOf: monthSchedules)
            }
        }
        
        return schedules
    }
    
    /// Represents a weekly schedule entry
    private struct WeekSchedule {
        let startDate: String  // e.g., "01-ene"
        let endDate: String    // e.g., "07-ene"
        let pharmacy: String   // e.g., "FARMACIA TORRE"
    }
    
    /// Process the table structure for a single page
    private func processPageTable(lines: [String]) -> [PharmacySchedule]? {
        // First, try to identify the month and year this table is for
        guard let monthInfo = findMonthAndYear(in: lines) else {
            if debug { print("❌ Could not identify month and year in table") }
            return nil
        }
        
        if debug { print("📅 Processing table for \(monthInfo.month) \(monthInfo.year)") }
        
        var schedules: [PharmacySchedule] = []
        var weekSchedules: [WeekSchedule] = []
        
        // Find table rows - they typically start with dates like "01-ene"
        for line in lines {
            if let weekSchedule = parseWeekSchedule(from: line) {
                weekSchedules.append(weekSchedule)
            }
        }
        
        if debug {
            print("📊 Found \(weekSchedules.count) week schedules:")
            weekSchedules.forEach { print($0) }
        }
        
        // Convert week schedules to daily schedules
        for weekSchedule in weekSchedules {
            if let weekDays = expandWeekDates(start: weekSchedule.startDate, end: weekSchedule.endDate, year: monthInfo.year) {
                for dutyDate in weekDays {
                    // Create pharmacy instances for the duty
                    let info = Self.pharmacyInfo[weekSchedule.pharmacy] ?? (
                        name: weekSchedule.pharmacy,
                        address: "Dirección no disponible"
                    )
                    let pharmacy = Pharmacy(
                        name: info.name,
                        address: info.address,
                        phone: "No disponible", // Phone numbers not in PDF
                        additionalInfo: nil
                    )
                    
                    // Create schedule for this day
                    // In Cuéllar, the same pharmacy covers both day and night shifts
                    let schedule = PharmacySchedule(
                        date: dutyDate,
                        dayShiftPharmacies: [pharmacy],
                        nightShiftPharmacies: [pharmacy]
                    )
                    
                    schedules.append(schedule)
                }
            }
        }
        
        return schedules
    }
    
    /// Parse a week schedule from a line of text
    private func parseWeekSchedule(from line: String) -> WeekSchedule? {
        // Expected format: "01-ene al 07-ene FARMACIA TORRE"
        // or "01-ene a 07-ene FARMACIA TORRE"
        let pattern = #"(\d{2}-[a-zA-Z]{3})\s+(?:al?|to)\s+(\d{2}-[a-zA-Z]{3})\s+(.+)"#
        
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)) else {
            return nil
        }
        
        let ranges = (0..<match.numberOfRanges).map { match.range(at: $0) }
        let startDate = String(line[Range(ranges[1], in: line)!])
        let endDate = String(line[Range(ranges[2], in: line)!])
        let pharmacy = String(line[Range(ranges[3], in: line)!]).trimmingCharacters(in: .whitespaces)
        
        return WeekSchedule(startDate: startDate, endDate: endDate, pharmacy: pharmacy)
    }
    
    /// Find the month and year in the page content
    private func findMonthAndYear(in lines: [String]) -> (month: String, year: Int)? {
        // First line usually contains the month and year
        guard let firstLine = lines.first else { return nil }
        
        // Try to find a year in the text (4 digits)
        let yearPattern = #"\b(20\d{2})\b"#
        guard let yearRegex = try? NSRegularExpression(pattern: yearPattern),
              let yearMatch = yearRegex.firstMatch(in: firstLine, range: NSRange(firstLine.startIndex..., in: firstLine)),
              let yearRange = Range(yearMatch.range(at: 1), in: firstLine),
              let year = Int(firstLine[yearRange]) else {
            return nil
        }
        
        // Try to find a month name
        for (_, monthName) in Self.months {
            if firstLine.lowercased().contains(monthName.lowercased()) {
                return (monthName, year)
            }
        }
        
        return nil
    }
    
    /// Converts a Spanish abbreviated month to a month number
    private func monthNumber(from abbreviation: String) -> Int? {
        let months = [
            "ene": 1, "feb": 2, "mar": 3, "abr": 4, "may": 5, "jun": 6,
            "jul": 7, "ago": 8, "sep": 9, "oct": 10, "nov": 11, "dic": 12
        ]
        return months[abbreviation.lowercased()]
    }
    
    /// Converts a date string like "01-ene" to a DutyDate
    private func parseDutyDate(_ dateString: String, year: Int = 2025) -> DutyDate? {
        let components = dateString.components(separatedBy: "-")
        guard components.count == 2,
              let day = Int(components[0]),
              let month = monthNumber(from: components[1]) else {
            return nil
        }
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        dateFormatter.locale = Locale(identifier: "es_ES")
        let dateString = String(format: "%d-%02d-%02d", year, month, day)
        
        guard let date = dateFormatter.date(from: dateString) else {
            return nil
        }
        
        let calendar = Calendar.current
        let weekday = calendar.component(.weekday, from: date)
        
        return DutyDate(
            dayOfWeek: Self.weekdayName(from: weekday),
            day: day,
            month: Self.monthName(from: month),
            year: year
        )
    }
    
    /// Expand a date range into individual DutyDate objects
    private func expandWeekDates(start: String, end: String, year: Int) -> [DutyDate]? {
        // Convert start and end strings to dates
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "dd-MMM-yyyy"
        dateFormatter.locale = Locale(identifier: "es_ES")
        
        // Append year to the date strings
        let startWithYear = start + "-\(year)"
        let endWithYear = end + "-\(year)"
        
        guard let startDate = dateFormatter.date(from: startWithYear),
              let endDate = dateFormatter.date(from: endWithYear) else {
            return nil
        }
        
        var dates: [DutyDate] = []
        let calendar = Calendar.current
        let daysBetween = calendar.dateComponents([.day], from: startDate, to: endDate).day ?? 0
        
        for dayOffset in 0...daysBetween {
            guard let currentDate = calendar.date(byAdding: .day, value: dayOffset, to: startDate) else {
                continue
            }
            
            let components = calendar.dateComponents([.year, .month, .day, .weekday], from: currentDate)
            guard let month = components.month,
                  let day = components.day,
                  let weekday = components.weekday else {
                continue
            }
            
            let dutyDate = DutyDate(
                dayOfWeek: Self.weekdayName(from: weekday),
                day: day,
                month: Self.monthName(from: month),
                year: year
            )
            
            dates.append(dutyDate)
        }
        
        return dates
    }
    
    /// Pharmacy information lookup table
    private static let pharmacyInfo: [String: (name: String, address: String)] = [
        "Av C.J. CELA": (
            name: "Farmacia Av. Camilo José Cela",
            address: "Av. Camilo José Cela, Cuéllar"
        ),
        "Ctra. BAHABON": (
            name: "Farmacia Ctra. Bahabón",
            address: "Ctra. Bahabón, Cuéllar"
        ),
        "C/ RESINA": (
            name: "Farmacia C/ Resina",
            address: "Calle Resina, Cuéllar"
        ),
        "STA. MARINA": (
            name: "Farmacia Santa Marina",
            address: "Santa Marina, Cuéllar"
        )
    ]
    

}
