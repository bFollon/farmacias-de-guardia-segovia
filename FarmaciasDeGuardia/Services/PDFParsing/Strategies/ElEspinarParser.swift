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
        "AV. HONTANILLA 18": (
            name: "FARMACIA ANA MARÍA APARICIO HERNAN",
            address: "Av. Hontanilla, 18, 40400 El Espinar, Segovia",
            phone: "921 181 011"
        ),
        "C/ MARQUES PERALES": (
            name: "Farmacia Lda M J. Bartolomé Sánchez",
            address: "Calle del, C. Marqués de Perales, 2, 40400, Segovia",
            phone: "921 181 171"
        ),
        "SAN RAFAEL": (
            name: "Farmacia San Rafael",
            address: "Tr.ª Alto del León, 19, 40410 San Rafael, Segovia",
            phone: "921 171 105"
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
    
    /// Process a set of dates with a pharmacy
    private func processDateSet(dates: [String], location: String, address: String, into schedules: inout [PharmacySchedule]) {
        if debug { print("\n📋 Processing date set:") }
        if debug { print("📅 Dates: \(dates)") }
        if debug { print("📍 Location: \(location)") }
        if debug { print("🏠 Address: \(address)") }
        if debug { print("📆 Current year: \(currentYear)") }
        
        for date in dates {
            // If this is January 1st, increment the year
            let pattern = #"01[‐-]ene"#
            if let regex = try? NSRegularExpression(pattern: pattern),
               regex.firstMatch(in: date, range: NSRange(date.startIndex..., in: date)) != nil {
                currentYear += 1
                if debug { print("🎊 New year detected! Now processing year \(currentYear)") }
            }
            
            if debug { print("📆 Processing date: \(date) (year: \(currentYear))") }
            
            // Extract day and month from the date
            let datePattern = #"(\d{1,2})[‐-](\w{3})"#
            guard let regex = try? NSRegularExpression(pattern: datePattern),
                  let match = regex.firstMatch(in: date, range: NSRange(date.startIndex..., in: date)),
                  let dayRange = Range(match.range(at: 1), in: date),
                  let monthRange = Range(match.range(at: 2), in: date),
                  let day = Int(String(date[dayRange])) else {
                continue
            }
            
            let monthAbbrev = String(date[monthRange]).lowercased()
            let monthMapping = [
                "ene": 1, "feb": 2, "mar": 3, "abr": 4, "may": 5, "jun": 6,
                "jul": 7, "ago": 8, "sep": 9, "oct": 10, "nov": 11, "dic": 12
            ]
            
            guard let month = monthMapping[monthAbbrev] else { continue }
            
            // Create date components to get weekday
            var dateComponents = DateComponents()
            dateComponents.year = currentYear
            dateComponents.month = month
            dateComponents.day = day
            
            if let date = Calendar.current.date(from: dateComponents) {
                let weekday = Calendar.current.component(.weekday, from: date)
                let dutyDate = DutyDate(
                    dayOfWeek: weekdayName(from: weekday),
                    day: day,
                    month: monthName(from: month),
                    year: currentYear
                )
                
                // Map location and address to pharmacy ID
                let pharmacyId: String?
                if location == "EL ESPINAR." {
                    pharmacyId = address.contains("HONTANILLA") ? "AV. HONTANILLA 18" : "C/ MARQUES PERALES"
                } else if location == "SAN RAFAEL" {
                    pharmacyId = "SAN RAFAEL"
                } else {
                    pharmacyId = nil
                }
                
                if let pharmacyId = pharmacyId,
                   let info = pharmacyInfo[pharmacyId] {
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

            var dates: [String] = []
            var currentLocation: String?
            var currentAddress: String?

            for line in lines {
                if debug { print("📝 Processing line: \(line)") }
                
                // Skip header lines
                if line.contains("COLEGIO") || line.contains("TURNOS") || line.contains("LUNES MARTES") {
                    continue
                }
                
                // If this line starts with a month abbreviation (e.g., "ENE", "FEB"), skip it
                if line.count <= 3 && ["ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC"].contains(line.uppercased()) {
                    continue
                }
                
                // Extract dates and location from line
                let components = line.components(separatedBy: " ")
                var lineDates: [String] = []
                
                // Process each component
                for component in components {
                    let trimmed = component.trimmingCharacters(in: .whitespaces)
                    if trimmed.range(of: #"^\d{1,2}[‐-][a-zé]{3}"#, options: .regularExpression) != nil {
                        lineDates.append(trimmed)
                    }
                }
                
                // Check if line ends with location
                if line.hasSuffix("EL ESPINAR.") {
                    if !dates.isEmpty && currentLocation != nil && currentAddress != nil {
                        processDateSet(dates: dates, location: currentLocation!, address: currentAddress!, into: &schedules)
                        dates = []
                    }
                    dates = lineDates
                    currentLocation = "EL ESPINAR."
                    currentAddress = nil
                } else if line.hasSuffix("SAN RAFAEL") {
                    if !dates.isEmpty && currentLocation != nil && currentAddress != nil {
                        processDateSet(dates: dates, location: currentLocation!, address: currentAddress!, into: &schedules)
                        dates = []
                    }
                    dates = lineDates
                    currentLocation = "SAN RAFAEL"
                    currentAddress = nil
                } else if line.contains("HONTANILLA") {
                    currentAddress = "AV. HONTANILLA 18"
                } else if line.contains("MARQUES PERALES") {
                    currentAddress = "C/ MARQUES PERALES"
                    if !dates.isEmpty && currentLocation != nil {
                        processDateSet(dates: dates, location: currentLocation!, address: currentAddress!, into: &schedules)
                        dates = []
                    }
                } else if !lineDates.isEmpty {
                    dates = lineDates
                }
            }
            
            // Process any remaining dates
            if !dates.isEmpty && currentLocation != nil && currentAddress != nil {
                processDateSet(dates: dates, location: currentLocation!, address: currentAddress!, into: &schedules)
            }
        }

        return schedules
    }
}
