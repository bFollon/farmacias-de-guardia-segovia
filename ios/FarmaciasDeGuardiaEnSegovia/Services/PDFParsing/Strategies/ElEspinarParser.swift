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
import PDFKit

class ElEspinarParser: PDFParsingStrategy {
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
    private func processDateSet(dates: [String], address: String, into schedules: inout [PharmacySchedule]) {
        DebugConfig.debugPrint("\n📋 Processing date set:")
        DebugConfig.debugPrint("📅 Dates: \(dates)")
        DebugConfig.debugPrint("🏠 Address: \(address)")
        DebugConfig.debugPrint("📆 Current year: \(currentYear)")
        
        for date in dates {
            // If this is January 1st, increment the year
            let pattern = #"01[‐-]ene"#
            if let regex = try? NSRegularExpression(pattern: pattern),
               regex.firstMatch(in: date, range: NSRange(date.startIndex..., in: date)) != nil {
                currentYear += 1
                DebugConfig.debugPrint("🎊 New year detected! Now processing year \(currentYear)")
            }
            
            DebugConfig.debugPrint("📆 Processing date: \(date) (year: \(currentYear))")
            
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
                
                // Use address directly to look up pharmacy info
                if let info = pharmacyInfo[address] {
                    let pharmacy = Pharmacy(
                        name: info.name,
                        address: info.address,
                        phone: info.phone,
                        additionalInfo: nil
                    )
                    
                    let schedule = PharmacySchedule(
                        date: dutyDate,
                        shifts: [
                            .fullDay: [pharmacy]
                        ]
                    )
                    
                    schedules.append(schedule)
                    
                    DebugConfig.debugPrint("💊 Added schedule for \(pharmacy.name) on \(dutyDate.day)-\(dutyDate.month)-\(dutyDate.year ?? DutyDate.getCurrentYear())")
                }
            }
        }
    }

    func parseSchedules(from pdfDocument: PDFDocument) -> [DutyLocation: [PharmacySchedule]] {
        var schedules: [PharmacySchedule] = []
        let pageCount = pdfDocument.pageCount

        DebugConfig.debugPrint("📄 Processing \(pageCount) pages...")

        for pageIndex in 0..<pageCount {
            guard let page = pdfDocument.page(at: pageIndex),
                  let content = page.string else {
                continue
            }

            DebugConfig.debugPrint("📃 Processing page \(pageIndex + 1)")

            // Split content into lines and process
            let lines = content.components(separatedBy: .newlines)
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }

            var dates: [String] = []
            var currentAddress: String?

            for line in lines {
                DebugConfig.debugPrint("📝 Processing line: \(line)")

                // Skip header lines
                if line.contains("COLEGIO") || line.contains("TURNOS") || line.contains("LUNES MARTES") {
                    continue
                }

                // Extract dates using regex
                let datePattern = #"\d{1,2}[‐-]\w{3}"#
                let regex = try? NSRegularExpression(pattern: datePattern)
                let range = NSRange(line.startIndex..., in: line)
                let matches = regex?.matches(in: line, range: range) ?? []

                var lineDates: [String] = []
                for match in matches {
                    if let range = Range(match.range, in: line) {
                        lineDates.append(String(line[range]))
                    }
                }

                // First, collect any dates we find
                if !lineDates.isEmpty {
                    dates = lineDates
                }

                // Then check for pharmacy addresses
                if line.contains("HONTANILLA") {
                    currentAddress = "AV. HONTANILLA 18"
                    // Process the dates we've collected with this pharmacy
                    if !dates.isEmpty {
                        processDateSet(dates: dates, address: currentAddress!, into: &schedules)
                        dates = []
                    }
                } else if line.contains("MARQUES PERALES") {
                    currentAddress = "C/ MARQUES PERALES"
                    // Process the dates we've collected with this pharmacy
                    if !dates.isEmpty {
                        processDateSet(dates: dates, address: currentAddress!, into: &schedules)
                        dates = []
                    }
                } else if line.hasSuffix("SAN RAFAEL") {
                    currentAddress = "SAN RAFAEL"
                    // Process both any previous dates and the current line dates
                    if !dates.isEmpty {
                        processDateSet(dates: dates, address: currentAddress!, into: &schedules)
                    }
                    if !lineDates.isEmpty {
                        processDateSet(dates: lineDates, address: currentAddress!, into: &schedules)
                    }
                    dates = []
                    currentAddress = nil
                }
            }

            // Process any remaining dates
            if !dates.isEmpty && currentAddress != nil {
                processDateSet(dates: dates, address: currentAddress!, into: &schedules)
            }
        }

        // Return as dictionary with DutyLocation as key
        let location = DutyLocation.fromRegion(.elEspinar)
        return [location: schedules]
    }
}
