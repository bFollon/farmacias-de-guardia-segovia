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

/// Parser implementation for Segovia Capital pharmacy schedules.
/// Wraps our new SegoviaPDFParser to maintain compatibility while we transition.
public class SegoviaCapitalParser: PDFParsingStrategy {
    private let parser: SegoviaPDFParser
    private var detectedYear: Int?

    public init() {
        self.parser = SegoviaPDFParser()
    }

    public func parseSchedules(from pdf: PDFDocument, pdfUrl: String? = nil) -> [DutyLocation: [PharmacySchedule]] {
        var allSchedules: [PharmacySchedule] = []

        // Detect year from first page
        if detectedYear == nil, let firstPage = pdf.page(at: 0), let pageText = firstPage.string {
            let yearResult = YearDetectionService.shared.detectYear(from: pageText, pdfUrl: pdfUrl)
            detectedYear = yearResult.year

            if let warning = yearResult.warning {
                DebugConfig.debugPrint("⚠️ Year detection warning: \(warning)")
            }

            DebugConfig.debugPrint("📅 Detected starting year for Segovia Capital: \(yearResult.year) (source: \(yearResult.source))")
        }

        // Process each page
        for pageIndex in 0..<pdf.pageCount {
            guard let page = pdf.page(at: pageIndex) else { continue }
            
            // Get the column text from our new parser
            let (dates, dayShiftLines, nightShiftLines) = parser.extractColumnTextFlattened(from: page)
            
            // Convert pharmacy lines to Pharmacy objects
            let dayPharmacies = Pharmacy.parseBatch(from: dayShiftLines)
            let nightPharmacies = Pharmacy.parseBatch(from: nightShiftLines)
            
            // Parse dates and remove duplicates while preserving order
            var seen = Set<TimeInterval>()
            let parsedDates = dates
                .compactMap { DutyDate.parse($0) }
                .filter { date in
                    guard let timestamp = date.toTimestamp() else { return false }
                    return seen.insert(timestamp).inserted
                }

            DebugConfig.debugPrint("DEBUG: Array lengths - parsedDates: \(parsedDates.count), dayPharmacies: \(dayPharmacies.count), nightPharmacies: \(nightPharmacies.count)")
            DebugConfig.debugPrint("DEBUG: All parsed dates: \(parsedDates)")
            DebugConfig.debugPrint("DEBUG: All day pharmacies: \(dayPharmacies)")
            DebugConfig.debugPrint("DEBUG: All night pharmacies: \(nightPharmacies)")
            
            // Create schedules for valid dates with available pharmacies
            for (index, date) in parsedDates.enumerated() where index < dayPharmacies.count && index < nightPharmacies.count {
                allSchedules.append(PharmacySchedule(
                    date: date,
                    shifts: [
                        .capitalDay: [dayPharmacies[index]],
                        .capitalNight: [nightPharmacies[index]]
                    ]
                ))
            }
        }

        // Sort schedules by date
        let sortedSchedules = allSchedules.sorted { first, second in
            let currentYear = DutyDate.getCurrentYear()
            let firstYear = first.date.year ?? currentYear
            let secondYear = second.date.year ?? currentYear

            if firstYear != secondYear {
                return firstYear < secondYear
            }

            let firstMonth = DutyDate.monthToNumber(first.date.month) ?? 0
            let secondMonth = DutyDate.monthToNumber(second.date.month) ?? 0

            if firstMonth != secondMonth {
                return firstMonth < secondMonth
            }
            return first.date.day < second.date.day
        }

        // Return as dictionary with DutyLocation as key
        let location = DutyLocation.fromRegion(.segoviaCapital)
        return [location: sortedSchedules]
    }
}
