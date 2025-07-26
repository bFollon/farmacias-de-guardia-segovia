import Foundation
import PDFKit

public class PDFProcessingService {
    // Segovia Capital PDF layout constants
    private enum SegoviaLayout {
        /// The estimated margin from PDF edges
        static let pageMargin: CGFloat = 40
        
        /// Width ratio for the date column (just enough for "miércoles, 19 de febrero")
        static let dateColumnWidthRatio: CGFloat = 0.22
        
        /// Gap between columns to prevent overlap
        static let columnGap: CGFloat = 5
    }
    
    /// Current active region. For now, always Segovia Capital.
    private let region: Region
    
    public init(region: Region = .segoviaCapital) {
        self.region = region
    }
    
    /// Loads pharmacy schedules for the current region
    public func loadPharmacies() -> [PharmacySchedule] {
        return loadPharmacies(from: region.pdfURL)
    }
    
    private func removeDuplicateAdjacent(blocks: [(y: CGFloat, text: String)]) -> [(y: CGFloat, text: String)] {
        guard !blocks.isEmpty else { return [] }
        
        var result: [(y: CGFloat, text: String)] = [blocks[0]]
        for i in 1..<blocks.count {
            let current = blocks[i]
            // Only add if text is different from the previous block
            if current.text != result.last?.text {
                result.append(current)
            }
        }
        return result
    }
    
    private func extractColumnText(from page: PDFPage) -> ([String], [String], [String]) {
        let pageBounds = page.bounds(for: .mediaBox)
        
        // First analyze fonts to determine optimal block heights
        var fontSizes: Set<CGFloat> = []
        if let pageContent = page.attributedString {
            pageContent.enumerateAttribute(.font, in: NSRange(location: 0, length: pageContent.length)) { font, range, _ in
                if let ctFont = font as! CTFont? {
                    fontSizes.insert(CTFontGetSize(ctFont))
                }
            }
        }
        
        // Use smallest font size as our base scanning height, or default to 5pt if no fonts found
        let baseHeight: CGFloat = fontSizes.min() ?? 5.0
        // Use slightly smaller increment to ensure we don't miss anything
        let scanIncrement: CGFloat = baseHeight / 2
        
        // Define margins and column layout
        let contentWidth = pageBounds.width - (2 * SegoviaLayout.pageMargin)
        
        // Column positions and widths - adjusted to be more precise
        let dateColumnWidth = contentWidth * SegoviaLayout.dateColumnWidthRatio
        let pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2 // Split remaining space evenly
        
        // Column X positions
        let dateColumnX = SegoviaLayout.pageMargin
        let dayColumnX = dateColumnX + dateColumnWidth + SegoviaLayout.columnGap
        let nightColumnX = dayColumnX + pharmacyColumnWidth + SegoviaLayout.columnGap
        
        var dateColumn: [(y: CGFloat, text: String)] = []
        var dayShiftColumn: [(y: CGFloat, text: String)] = []
        var nightShiftColumn: [(y: CGFloat, text: String)] = []
        
        var lastDateText = ""
        var lastDayText = ""
        var lastNightText = ""
        var pendingYear: String? = nil
        let currentYear = DutyDate.getCurrentYear()
        
        // Scan from top to bottom with high precision
        for y in stride(from: 0, to: pageBounds.height, by: scanIncrement) {
            // Date column
            let dateRect = CGRect(x: dateColumnX, y: y, width: dateColumnWidth, height: baseHeight)
            if let selection = page.selection(for: dateRect),
               let text = selection.string?.trimmingCharacters(in: .whitespacesAndNewlines),
               !text.isEmpty,
               text != lastDateText {
                // Check if this is a standalone year for current or next year
                let nextYear = currentYear + 1
                if text == String(currentYear) || text == String(nextYear) {
                    pendingYear = text
                } else if text.contains(",") { // This is a date line
                    if let year = pendingYear {
                        // Combine the date with the pending year
                        dateColumn.append((y: y, text: "\(text) \(year)"))
                        pendingYear = nil
                    } else {
                        dateColumn.append((y: y, text: text))
                    }
                } else {
                    dateColumn.append((y: y, text: text))
                }
                lastDateText = text
            }
            
            // Day shift column
            let dayRect = CGRect(x: dayColumnX, y: y, width: pharmacyColumnWidth, height: baseHeight)
            if let selection = page.selection(for: dayRect),
               let text = selection.string?.trimmingCharacters(in: .whitespacesAndNewlines),
               !text.isEmpty,
               text != lastDayText {
                dayShiftColumn.append((y: y, text: text))
                lastDayText = text
            }
            
            // Night shift column
            let nightRect = CGRect(x: nightColumnX, y: y, width: pharmacyColumnWidth, height: baseHeight)
            if let selection = page.selection(for: nightRect),
               let text = selection.string?.trimmingCharacters(in: .whitespacesAndNewlines),
               !text.isEmpty,
               text != lastNightText {
                nightShiftColumn.append((y: y, text: text))
                lastNightText = text
            }
        }
        
        // Remove duplicate adjacent lines
        dateColumn = removeDuplicateAdjacent(blocks: dateColumn)
        dayShiftColumn = removeDuplicateAdjacent(blocks: dayShiftColumn)
        nightShiftColumn = removeDuplicateAdjacent(blocks: nightShiftColumn)
        
        let nextYear = currentYear + 1
        // Convert to simple string arrays, filtering out standalone year entries
        let dates = dateColumn.map { $0.text }.filter { $0 != String(currentYear) && $0 != String(nextYear) }
        let dayShifts = dayShiftColumn.map { $0.text }
        let nightShifts = nightShiftColumn.map { $0.text }
        
        return (dates, dayShifts, nightShifts)
    }
    
    private func analyzePDFStructure(from page: PDFPage) {
        print("=== Page Analysis ===")
        let pageBounds = page.bounds(for: .mediaBox)
        print("Page size: \(pageBounds)")
        
        // First analyze fonts to determine optimal block heights
        var fontSizes: Set<CGFloat> = []
        if let pageContent = page.attributedString {
            pageContent.enumerateAttribute(.font, in: NSRange(location: 0, length: pageContent.length)) { font, range, _ in
                if let ctFont = font as! CTFont? {
                    let pointSize = CTFontGetSize(ctFont)
                    fontSizes.insert(pointSize)
                    let text = pageContent.attributedSubstring(from: range).string
                    print("\nFont Block:")
                    print("Font: \(String(describing: font))")
                    print("Size: \(pointSize)pt")
                    print("Content: \(text)")
                }
            }
        }
        
        print("\n=== Layout Analysis ===")
        
        // Extract column text
        let (dates, dayShifts, nightShifts) = extractColumnText(from: page)
        
        // Print results
        print("\nDate Column:")
        for text in dates {
            print("\n\(text)")
        }
        
        print("\nDay Shift Column:")
        for text in dayShifts {
            print("\n\(text)")
        }
        
        print("\nNight Shift Column:")
        for text in nightShifts {
            print("\n\(text)")
        }
        
        print("=== End Analysis ===\n")
    }
    
    public func extractTextWithLayout(from page: PDFPage) -> [PharmacySchedule] {
        print("\nAnalyzing PDF structure first:")
        analyzePDFStructure(from: page)
        
        // Get the column text using our new function
        let (dates, dayShiftLines, nightShiftLines) = extractColumnText(from: page)
        
        print("\nProcessing text data:")
        print("Dates found: \(dates.count)")
        print("Day shift blocks: \(dayShiftLines.count)")
        print("Night shift blocks: \(nightShiftLines.count)")
        
        // Process pharmacy lines in groups of 3
        let dayPharmacies = Pharmacy.parseBatch(from: dayShiftLines)
        let nightPharmacies = Pharmacy.parseBatch(from: nightShiftLines)
        
        // Print size check
        print("\nSize check:")
        print("Dates: \(dates.count)")
        print("Day pharmacies: \(dayPharmacies.count)")
        print("Night pharmacies: \(nightPharmacies.count)")
        
        // Create schedules by matching dates with pharmacies
        var schedules: [PharmacySchedule] = []
        for (index, dateString) in dates.enumerated() {
            if let date = DutyDate.parse(dateString),
               index < dayPharmacies.count && index < nightPharmacies.count {
                schedules.append(PharmacySchedule(
                    date: date,
                    dayShiftPharmacies: [dayPharmacies[index]],
                    nightShiftPharmacies: [nightPharmacies[index]]
                ))
            }
        }
        
        // Print the schedules for verification
        print("\n=== Pharmacy Schedules ===")
        for schedule in schedules {
            print("\nDate: \(schedule.date.dayOfWeek), \(schedule.date.day) de \(schedule.date.month)")
            print("Day Shift:")
            for pharmacy in schedule.dayShiftPharmacies {
                print("  - Name: \(pharmacy.name)")
                print("  - Address: \(pharmacy.address)")
                if !pharmacy.phone.isEmpty {
                    print("  - Phone: \(pharmacy.phone)")
                }
                if let info = pharmacy.additionalInfo {
                    print("  - Additional info: \(info)")
                }
            }
            print("Night Shift:")
            for pharmacy in schedule.nightShiftPharmacies {
                print("  - Name: \(pharmacy.name)")
                print("  - Address: \(pharmacy.address)")
                if !pharmacy.phone.isEmpty {
                    print("  - Phone: \(pharmacy.phone)")
                }
                if let info = pharmacy.additionalInfo {
                    print("  - Additional info: \(info)")
                }
            }
        }
        print("=== End Schedules ===\n")
        
        return schedules
    }
    
    // Internal method, kept for backward compatibility and testing
    func loadPharmacies(from url: URL) -> [PharmacySchedule] {
        guard let pdfDocument = PDFDocument(url: url) else {
            return []
        }

        var allSchedules: [PharmacySchedule] = []
        print("Total pages in PDF: \(pdfDocument.pageCount)")
        
        for pageIndex in 0..<pdfDocument.pageCount {
            if let page = pdfDocument.page(at: pageIndex) {
                print("Processing page \(pageIndex + 1)")
                let pageSchedules = extractTextWithLayout(from: page)
                allSchedules.append(contentsOf: pageSchedules)
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
        
        print("\nLoaded \(sortedSchedules.count) schedules")
        
        // Print timestamps for validation
        print("\n=== Date Timestamps ===")
        for schedule in sortedSchedules {
            if let timestamp = schedule.date.toTimestamp() {
                let date = Date(timeIntervalSince1970: timestamp)
                let formatter = DateFormatter()
                formatter.dateStyle = .full
                formatter.timeStyle = .none
                formatter.locale = Locale(identifier: "es_ES")
                print("\(schedule.date.dayOfWeek), \(schedule.date.day) de \(schedule.date.month) \(schedule.date.year ?? DutyDate.getCurrentYear())")
                print("Timestamp: \(timestamp)")
                print("Parsed back: \(formatter.string(from: date))\n")
            } else {
                print("Failed to convert date: \(schedule.date.dayOfWeek), \(schedule.date.day) de \(schedule.date.month) \(schedule.date.year ?? DutyDate.getCurrentYear())\n")
            }
        }
        print("=== End Timestamps ===\n")
        
        return sortedSchedules
    }
}
