import Foundation
import PDFKit

class ElEspinarParser: PDFParsingStrategy {
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
        // El Espinar pharmacies - TODO: Update with real data
        "FRAILE": (
            name: "Farmacia Fraile",
            address: "Av. Hontanilla 10, El Espinar",
            phone: "921 18 21 09"
        ),
        "BARRIO SAN RAFAEL": (
            name: "Farmacia San Rafael",
            address: "Av. Alto del León 7, San Rafael",
            phone: "921 17 12 57"
        ),
        "BARRIO ESTACIÓN": (
            name: "Farmacia Estación",
            address: "Plaza Estación 1, El Espinar",
            phone: "921 18 23 45"
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
    
    /// The line contains a date. These typically have format: "01-ene" or "1 ene" where ene is Spanish month abbreviation
    private func isDateLine(_ line: String) -> Bool {
        // Use a regex pattern that supports both dash and space between day and month
        let pattern = #"^\s*\d{1,2}\s*[‐-]\s*[A-Za-zé]{3,}\s*$"#
        return line.range(of: pattern, options: .regularExpression) != nil
    }
    
    /// Extracts day and month from a date line
    private func extractDayMonth(from line: String) -> (day: Int, month: Int)? {
        let pattern = #"^\s*(\d{1,2})\s*[‐-]\s*([A-Za-zé]{3,})\s*$"#
        guard let match = line.range(of: pattern, options: .regularExpression) else { return nil }
        
        let lineSubstring = line[match]
        let components = lineSubstring.split(whereSeparator: { " -‐".contains($0) })
        
        guard let dayStr = components.first,
              let day = Int(dayStr),
              let monthAbbrev = components.last?.trimmingCharacters(in: .whitespaces).lowercased() else {
            return nil
        }
        
        // Map Spanish month abbreviation to number
        let month: Int
        switch monthAbbrev.prefix(3) {
        case "ene": month = 1
        case "feb": month = 2
        case "mar": month = 3
        case "abr": month = 4
        case "may": month = 5
        case "jun": month = 6
        case "jul": month = 7
        case "ago": month = 8
        case "sep": month = 9
        case "oct": month = 10
        case "nov": month = 11
        case "dic": month = 12
        default: return nil
        }
        
        return (day, month)
    }
    
    /// Returns pharmacy keys based on the location (EL ESPINAR or SAN RAFAEL) and their address
    private func extractPharmacyKey(from location: String, address: String?) -> String? {
        let normalizedLocation = location.trimmingCharacters(in: .whitespaces).uppercased()
        let normalizedAddress = address?.trimmingCharacters(in: .whitespaces).uppercased() ?? ""
        
        if normalizedLocation.contains("EL ESPINAR") {
            if normalizedAddress.contains("HONTANILLA") {
                return "FRAILE"
            } else if normalizedAddress.contains("MARQUES") {
                return "BARRIO ESTACIÓN"
            }
        } else if normalizedLocation.contains("SAN RAFAEL") {
            return "BARRIO SAN RAFAEL"
        }
        return nil
    }

    /// Returns pharmacies mentioned in a line, combining with the next line if it's an address
    private func extractPharmacies(from line: String, nextLine: String?) -> [String] {
        if line.hasSuffix("EL ESPINAR.") || line.hasSuffix("SAN RAFAEL") {
            if let key = extractPharmacyKey(from: line, address: nextLine) {
                return [key]
            }
        }
        return []
    }

    func parseSchedules(from pdfDocument: PDFDocument) -> [PharmacySchedule] {
        var schedules: [PharmacySchedule] = []
        let pageCount = pdfDocument.pageCount
        
        if debug { print("📄 Processing \(pageCount) pages...") }

        for pageIndex in 0..<pageCount {
            guard let page = pdfDocument.page(at: pageIndex),
                  let content = page.string else {
                continue
            }

            if debug { print("📃 Processing page \(pageIndex + 1)") }

            // Split content into lines and process
            let lines = content.components(separatedBy: .newlines)
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }

            // Process each line
            var currentDutyDate: DutyDate?

            for line in lines {
                if debug { print("📝 Processing line: \(line)") }

                if isDateLine(line) {
                    // Extract date information
                    guard let (day, month) = extractDayMonth(from: line) else { continue }
                    
                    // Handle year transition
                    if month == 1 && day == 1 {
                        currentYear += 1
                    }
                    
                    // Create date components to get weekday
                    var dateComponents = DateComponents()
                    dateComponents.year = currentYear
                    dateComponents.month = month
                    dateComponents.day = day
                    
                    if let date = Calendar.current.date(from: dateComponents) {
                        let weekday = Calendar.current.component(.weekday, from: date)
                        currentDutyDate = DutyDate(
                            dayOfWeek: weekdayName(from: weekday),
                            day: day,
                            month: monthName(from: month),
                            year: currentYear
                        )
                        if debug { print("📅 Found date: \(day)-\(monthName(from: month))-\(currentYear)") }
                    }
                } else {
                    // Extract pharmacies from the line, looking ahead at the next line for the address
                    let nextLineIndex = lines.firstIndex(of: line)?.advanced(by: 1)
                    let nextLine = nextLineIndex.flatMap { lines.indices.contains($0) ? lines[$0] : nil }
                    let pharmacies = extractPharmacies(from: line, nextLine: nextLine)
                    if !pharmacies.isEmpty, let dutyDate = currentDutyDate {
                        // Create pharmacy schedules
                        for pharmacyId in pharmacies {
                            if let info = pharmacyInfo[pharmacyId] {
                                let pharmacy = Pharmacy(
                                    name: info.name,
                                    address: info.address,
                                    phone: info.phone,
                                    additionalInfo: nil
                                )
                                
                                let schedule = PharmacySchedule(
                                    date: dutyDate,
                                    dayShiftPharmacies: [pharmacy],
                                    nightShiftPharmacies: [] // El Espinar doesn't distinguish between day/night shifts
                                )
                                
                                schedules.append(schedule)
                                
                                if debug {
                                    print("💊 Added schedule for \(pharmacy.name) on \(dutyDate.day)-\(dutyDate.month)-\(dutyDate.year)")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Return schedules in chronological order
        return schedules
    }
}
