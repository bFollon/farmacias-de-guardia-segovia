import Foundation
import PDFKit

class CuellarParser: PDFParsingStrategy {
    /// Debug flag - when true, prints detailed parsing information
    private let debug = true
    
    /// Current year being processed, incremented when January 1st is found
    private var currentYear = 2024
    
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
    private let pharmacyInfo: [String: (name: String, address: String, phone: String)] = [
        "Av C.J. CELA": (
            name: "Farmacia Fernando Redondo",
            address: "Av. Camilo Jose Cela, 46, 40200 Cuéllar, Segovia",
            phone: "No disponible"
        ),
        "Ctra. BAHABON": (
            name: "Farmacia San Andrés",
            address: "Ctra. Bahabón, 9, 40200 Cuéllar, Segovia",
            phone: "921144794"
        ),
        "C/ RESINA": (
            name: "Farmacia Ldo. Fco. Javier Alcaraz García de la Barrera",
            address: "C. Resina, 14, 40200 Cuéllar, Segovia",
            phone: "921144812"
        ),
        "STA. MARINA": (
            name: "Farmacia Ldo. César Cabrerizo Izquierdo",
            address: "Calle Sta. Marina, 5, 40200 Cuéllar, Segovia",
            phone: "921140606"
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
        var pendingDates: [String]? = nil
        
        // Skip the header lines (first 2 lines)
        let dataLines = Array(lines.dropFirst(2))
        
        for line in dataLines {
            if debug { print("\n🔍 Processing line: '\(line)'") }
            
            // Skip month indicator lines and empty lines
            if line.isEmpty || (try? NSRegularExpression(pattern: "^[A-Z]{3}\\s*$").firstMatch(in: line, range: NSRange(line.startIndex..., in: line))) != nil {
                if debug { print("⏭️ Skipping empty or month indicator line") }
                continue
            }
            
            // Check if we have pending dates from a special format line
            if let dates = pendingDates {
                if debug { print("📅 Have pending dates: \(dates)") }
                // This line should be just the pharmacy name
                let pharmacy = line.trimmingCharacters(in: .whitespaces)
                if !pharmacy.isEmpty && pharmacyInfo.keys.contains(pharmacy) {
                    if debug { print("🏥 Found matching pharmacy: \(pharmacy)") }
                    processDateSet(dates: dates, pharmacy: pharmacy, into: &schedules)
                    pendingDates = nil
                    continue
                } else {
                    if debug { print("⚠️ Line doesn't match expected pharmacy format: '\(pharmacy)'") }
                }
            }
            
            // Extract dates and pharmacy from the line
            if let (dates, pharmacy) = extractDatesAndPharmacy(from: line) {
                if debug { print("📆 Extracted dates: \(dates), pharmacy: '\(pharmacy)'") }
                if pharmacy.isEmpty {
                    // This is a special format line, store the dates for the next line
                    if debug { print("⏳ Storing dates for next line") }
                    pendingDates = dates
                    continue
                }
                processDateSet(dates: dates, pharmacy: pharmacy, into: &schedules)
            }
        }
        
        return schedules
    }
    
    /// Extract dates and pharmacy from a line
    private func extractDatesAndPharmacy(from line: String) -> ([String], String)? {
        // If this is just a pharmacy name following a special format line, skip it
        if pharmacyInfo.keys.contains(line.trimmingCharacters(in: .whitespaces)) {
            if debug { print("🏥 Found standalone pharmacy line") }
            return nil
        }
        
        // Regular expression to match dates in format dd-mmm (with figure dash)
        let datePattern = #"\d{1,2}[‐-]\w{3}"#
        let regex = try? NSRegularExpression(pattern: datePattern)
        let range = NSRange(line.startIndex..., in: line)
        let matches = regex?.matches(in: line, range: range) ?? []
        
        if debug {
            print("🔍 Found \(matches.count) regular dates in line")
            // Print each character's Unicode value to help debug dash types
            print("📝 Line characters:")
            for (i, char) in line.unicodeScalars.enumerated() {
                print("   \(i): '\(char)' (Unicode: U+\(String(format:"%04X", char.value)))")
                if i > 20 { break } // Only show first few characters
            }
        }
        
        // If we found regular dates, parse them
        if !matches.isEmpty {
            var dates: [String] = []
            if debug { print("🔍 Date matches found:") }
            for match in matches {
                if let range = Range(match.range, in: line) {
                    let date = String(line[range])
                    if debug { print("   - Found date: '\(date)'") }
                    dates.append(date)
                }
            }
            
            // Get everything after the last date as the pharmacy name
            let lastDate = dates.last ?? ""
            if let pharmacyStartRange = line.range(of: lastDate)?.upperBound {
                let pharmacy = String(line[pharmacyStartRange...])
                    .trimmingCharacters(in: .whitespaces)
                
                if debug { print("📅 Regular dates: \(dates)") }
                if debug { print("🏥 Pharmacy: \(pharmacy)") }
                
                if !dates.isEmpty && !pharmacy.isEmpty {
                    return (dates, pharmacy)
                }
            }
        }
        
        // If no regular dates found, try special format (September transition)
        if line.contains("DOMINGO") || line.contains("MARTES") || line.contains("JUEVES") || line.contains("SABADO") {
            if debug { print("🎯 Found special format line") }
            if let specialTransition = parseSeptemberTransition(from: line) {
                if debug { print("✅ Parsed special dates: \(specialTransition.0)") }
                return specialTransition
            }
        }
        
        // If we get here, we couldn't parse the line
        return nil
    }
    
    /// Parse special format for September transition period and return dates with empty pharmacy
    private func parseSeptemberTransition(from line: String) -> ([String], String)? {
        if let dates = parseSeptemberTransitionDates(from: line) {
            return (dates, "")
        }
        return nil
    }
    
    /// Parse special format for September transition period and return just the dates
    private func parseSeptemberTransitionDates(from line: String) -> [String]? {
        if debug { print("\n🔄 Parsing special format dates only: '\(line)'") }
        
        // Patterns we need to handle:
        // "DOMINGO 31 DE AGOSTO Y LUNES 1 DE SEPTIEMBRE" followed by pharmacy
        // "MARTES 2 Y MIERCOLES 3 de SEPTIEMBRE" followed by pharmacy
        // "JUEVES 4 Y VIERNES 5 de SEPTIEMBRE" followed by pharmacy
        // "SABADO 6 Y DOMINGO 7 de Septiembre" followed by pharmacy
        
        // Handle different September transition patterns
        // Like "31 DE AGOSTO" or just "2" in "MARTES 2"
        let datePattern = #"(?:(\d+)\s+DE\s+(AGOSTO|SEPTIEMBRE|Septiembre)|(?:LUNES|MARTES|MIERCOLES|JUEVES|VIERNES|SABADO|DOMINGO)\s+(\d+)(?:\s+[Dd][Ee]\s+(?:SEPTIEMBRE|Septiembre))?)"#
        let regex = try? NSRegularExpression(pattern: datePattern)
        
        guard let matches = regex?.matches(in: line, range: NSRange(line.startIndex..., in: line)) else {
            if debug { print("❌ No matches found in line") }
            return nil
        }
        
        if debug { print("✅ Found \(matches.count) date matches") }
        
        var dates: [String] = []
        for match in matches {
            var day: String?
            var month: String?
            
            // Try first pattern: "31 DE AGOSTO"
            if let dayRange = Range(match.range(at: 1), in: line),
               let monthRange = Range(match.range(at: 2), in: line) {
                day = String(line[dayRange])
                month = String(line[monthRange].lowercased())
            }
            // Try second pattern: "MARTES 2"
            else if let dayRange = Range(match.range(at: 3), in: line) {
                day = String(line[dayRange])
                month = line.contains("SEPTIEMBRE") || line.contains("Septiembre") ? "septiembre" : "agosto"
            }
            
            if let day = day, let month = month {
                let monthAbbr = String(month.prefix(3))
                dates.append("\(String(format: "%02d", Int(day) ?? 0))-\(monthAbbr)")
            }
        }
        
        if debug { print("📅 Parsed dates: \(dates)") }
        return dates.isEmpty ? nil : dates
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
    private func parseDutyDate(_ dateString: String, year: Int) -> DutyDate? {
        let pattern = #"(\d{1,2})[‐-](\w{3})"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: dateString, range: NSRange(dateString.startIndex..., in: dateString)),
              let dayRange = Range(match.range(at: 1), in: dateString),
              let monthRange = Range(match.range(at: 2), in: dateString),
              let day = Int(String(dateString[dayRange])),
              let month = monthNumber(from: String(dateString[monthRange])) else {
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
    private func processDateSet(dates: [String], pharmacy: String, into schedules: inout [PharmacySchedule]) {
        if debug { print("\n📋 Processing date set:") }
        if debug { print("📅 Dates: \(dates)") }
        if debug { print("🏥 Pharmacy: \(pharmacy)") }
        if debug { print("📆 Current year: \(currentYear)") }
        
        // Create schedules for each date in the set
        for date in dates {
            // If this is January 1st, increment the year
            let pattern = #"01[‐-]ene"#
            if let regex = try? NSRegularExpression(pattern: pattern),
               regex.firstMatch(in: date, range: NSRange(date.startIndex..., in: date)) != nil {
                currentYear += 1
                if debug { print("🎊 New year detected! Now processing year \(currentYear)") }
            }

            if debug { print("📆 Processing date: \(date) (year: \(currentYear))") }
            
            if let dutyDate = parseDutyDate(date, year: currentYear) {
                let info = pharmacyInfo[pharmacy] ?? (
                    name: pharmacy,
                    address: "Dirección no disponible",
                    phone: "No disponible"
                )
                
                let pharmacyInstance = Pharmacy(
                    name: info.name,
                    address: info.address,
                    phone: info.phone,
                    additionalInfo: nil
                )
                
                let schedule = PharmacySchedule(
                    date: dutyDate,
                    dayShiftPharmacies: [pharmacyInstance],
                    nightShiftPharmacies: [] // Cuéllar doesn't use separate night shifts
                )
                
                schedules.append(schedule)
            }
        }
    }
}
