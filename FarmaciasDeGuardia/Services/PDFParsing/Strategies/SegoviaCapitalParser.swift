import Foundation
import PDFKit

/// Parser implementation for Segovia Capital pharmacy schedules.
/// Wraps our new SegoviaPDFParser to maintain compatibility while we transition.
public class SegoviaCapitalParser: PDFParsingStrategy {
    private let parser: SegoviaPDFParser
    
    public init() {
        self.parser = SegoviaPDFParser()
    }
    
    public func parseSchedules(from pdf: PDFDocument) -> [PharmacySchedule] {
        var allSchedules: [PharmacySchedule] = []
        
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

            print("DEBUG: Array lengths - parsedDates: \(parsedDates.count), dayPharmacies: \(dayPharmacies.count), nightPharmacies: \(nightPharmacies.count)")
            print("DEBUG: All parsed dates: \(parsedDates)")
            print("DEBUG: All day pharmacies: \(dayPharmacies)")
            print("DEBUG: All night pharmacies: \(nightPharmacies)")
            
            // Create schedules for valid dates with available pharmacies
            for (index, date) in parsedDates.enumerated() where index < dayPharmacies.count && index < nightPharmacies.count {
                allSchedules.append(PharmacySchedule(
                    date: date,
                    dayShiftPharmacies: [dayPharmacies[index]],
                    nightShiftPharmacies: [nightPharmacies[index]]
                ))
            }
        }
        
        // Sort schedules by date
        return allSchedules.sorted { first, second in
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
    }
}
