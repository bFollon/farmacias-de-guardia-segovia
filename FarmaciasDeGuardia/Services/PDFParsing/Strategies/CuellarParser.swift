import Foundation
import PDFKit

class CuellarParser: PDFParsingStrategy {
    /// Debug flag - when true, prints detailed parsing information
    private let debug = true
    
    // Lookup tables for Spanish names
    private let weekdays = [
        1: "Domingo",
        2: "Lunes",
        3: "Martes",
        4: "Miércoles",
        5: "Jueves",
        6: "Viernes",
        7: "Sábado"
    ]
    
    private let months = [
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
    
    // Pharmacy information lookup table
    private let pharmacyInfo: [String: (name: String, address: String)] = [
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
    
    /// Converts a weekday number to Spanish name
    private func weekdayName(from weekday: Int) -> String {
        return weekdays[weekday] ?? "Unknown"
    }
    
    /// Converts a month number to Spanish name
    private func monthName(from month: Int) -> String {
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
        var schedules: [PharmacySchedule] = []
        var year = 2025 // Default year
        var pendingDates: [String]? = nil
        
        // Skip the header lines (first 2 lines)
        let dataLines = Array(lines.dropFirst(2))
        
        for line in dataLines {
            // Skip month indicator lines and empty lines
            if line.isEmpty || (try? NSRegularExpression(pattern: "^[A-Z]{3}\\s*$").firstMatch(in: line, range: NSRange(line.startIndex..., in: line))) != nil {
                continue
            }
            
            // Check if we have pending dates from a special format line
            if let dates = pendingDates {
                // This line should be just the pharmacy name
                let pharmacy = line.trimmingCharacters(in: .whitespaces)
                if !pharmacy.isEmpty && pharmacyInfo.keys.contains(pharmacy) {
                    processDateSet(dates: dates, pharmacy: pharmacy, year: year, into: &schedules)
                    pendingDates = nil
                    continue
                }
            }
            
            // Extract dates and pharmacy from the line
            if let (dates, pharmacy) = extractDatesAndPharmacy(from: line) {
                if pharmacy.isEmpty {
                    // This is a special format line, store the dates for the next line
                    pendingDates = dates
                    continue
                }
                processDateSet(dates: dates, pharmacy: pharmacy, year: year, into: &schedules)
            }
        }
        
        return schedules
    }
    
    /// Extract dates and pharmacy from a line
    private func extractDatesAndPharmacy(from line: String) -> ([String], String)? {
        // First, try to handle special format for September transition
        if line.contains("DOMINGO") || line.contains("MARTES") || line.contains("JUEVES") || line.contains("SABADO") {
            // This line is a description, skip it and wait for the pharmacy line
            return nil
        }
        
        // If this is just a pharmacy name following a special format line, skip it
        if pharmacyInfo.keys.contains(line.trimmingCharacters(in: .whitespaces)) {
            return nil
        }
        
        // Handle special cases for September transition
        if let specialDates = parseSeptemberTransition(from: line) {
            return specialDates
        }
        
        // Remove any leading month indicators or year markers
        let cleanLine = line.replacingOccurrences(of: "^(ENE|FEB|MAR|ABR|MAY|JUN|JUL|AGO|SEP|OCT|NOV|DIC)\\s+", with: "", options: .regularExpression)
            .replacingOccurrences(of: "^\\d{4}\\s+\\d{4}\\s+", with: "", options: .regularExpression)
        
        // Split line into components
        let components = cleanLine.split(separator: " ")
        
        // Check if we have enough components and the last part is a pharmacy
        guard components.count >= 2 else { return nil }
        
        // Collect all date components (they should be in format dd-mmm)
        let dates = components.prefix(while: { $0.contains("-") })
            .map(String.init)
        
        // Get the pharmacy name (everything after the dates)
        let pharmacy = components.dropFirst(dates.count)
            .joined(separator: " ")
            .trimmingCharacters(in: .whitespaces)
        
        guard !dates.isEmpty && !pharmacy.isEmpty else { return nil }
        return (dates, pharmacy)
    }
    
    /// Parse special format for September transition period
    private func parseSeptemberTransition(from line: String) -> ([String], String)? {
        // Patterns we need to handle:
        // "DOMINGO 31 DE AGOSTO Y LUNES 1 DE SEPTIEMBRE" followed by pharmacy
        // "MARTES 2 Y MIERCOLES 3 de SEPTIEMBRE" followed by pharmacy
        // "JUEVES 4 Y VIERNES 5 de SEPTIEMBRE" followed by pharmacy
        // "SABADO 6 Y DOMINGO 7 de Septiembre" followed by pharmacy
        
        // Match patterns like "31 DE AGOSTO" and "1 DE SEPTIEMBRE"
        let datePattern = #"(\d+)\s+DE\s+(AGOSTO|SEPTIEMBRE|Septiembre)"#
        let regex = try? NSRegularExpression(pattern: datePattern)
        
        guard let matches = regex?.matches(in: line, range: NSRange(line.startIndex..., in: line)) else {
            return nil
        }
        
        var dates: [String] = []
        for match in matches {
            if let dayRange = Range(match.range(at: 1), in: line),
               let monthRange = Range(match.range(at: 2), in: line) {
                let day = String(line[dayRange])
                let month = line[monthRange].lowercased()
                let monthAbbr = String(month.prefix(3))
                dates.append("\(String(format: "%02d", Int(day) ?? 0))-\(monthAbbr)")
            }
        }
        
        // We'll get the pharmacy name from the next line in processPageTable
        return dates.isEmpty ? nil : (dates, "")
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
            dayOfWeek: weekdayName(from: weekday),
            day: day,
            month: monthName(from: month),
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
                dayOfWeek: weekdayName(from: weekday),
                day: day,
                month: monthName(from: month),
                year: year
            )
            
            dates.append(dutyDate)
        }
        
        return dates
    }
    
    
    /// Process a set of dates with a pharmacy
    private func processDateSet(dates: [String], pharmacy: String, year: Int, into schedules: inout [PharmacySchedule]) {
        // Create schedules for each date in the set
        for date in dates {
            // If we cross into January and we're not in December, we're in the next year
            let currentYear = date.contains("-ene") && !dates.first!.contains("-dic") ? year + 1 : year
            
            if let dutyDate = parseDutyDate(date, year: currentYear) {
                let info = pharmacyInfo[pharmacy] ?? (
                    name: pharmacy,
                    address: "Dirección no disponible"
                )
                
                let pharmacyInstance = Pharmacy(
                    name: info.name,
                    address: info.address,
                    phone: "No disponible",
                    additionalInfo: nil
                )
                
                let schedule = PharmacySchedule(
                    date: dutyDate,
                    dayShiftPharmacies: [pharmacyInstance],
                    nightShiftPharmacies: [pharmacyInstance]
                )
                
                schedules.append(schedule)
            }
        }
    }
}
