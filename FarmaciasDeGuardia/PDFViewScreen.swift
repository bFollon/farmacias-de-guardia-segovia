import PDFKit
import SwiftUI

// Models for pharmacy data
struct DutyDate {
    let dayOfWeek: String
    let day: Int
    let month: String
    let year: Int?
}

struct PharmacySchedule {
    let date: DutyDate
    let dayShiftPharmacies: [Pharmacy]
    let nightShiftPharmacies: [Pharmacy]
}

struct Pharmacy: Identifiable {
    let id = UUID()
    let name: String
    let address: String
    let phone: String
    let additionalInfo: String?
}

struct PDFViewScreen: View {
    @State private var pharmacies: [Pharmacy] = []
    var url: URL

    var body: some View {
        NavigationView {
            List(pharmacies) { pharmacy in
                VStack(alignment: .leading) {
                    Text(pharmacy.name)
                        .font(.headline)
                    Text(pharmacy.address)
                        .font(.subheadline)
                    Text(pharmacy.phone)
                        .font(.footnote)
                }
            }
            .navigationTitle("Pharmacies on Duty")
        }
        .onAppear {
            loadPharmacies()
        }
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
        let pageMargin: CGFloat = 40 // Estimated margin from PDF edges
        let contentWidth = pageBounds.width - (2 * pageMargin)
        
        // Column positions and widths - adjusted to be more precise
        let dateColumnWidth = contentWidth * 0.22 // Just enough for "miércoles, 19 de febrero"
        let pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2 // Split remaining space evenly
        
        // Add small gaps between columns to prevent overlap
        let columnGap: CGFloat = 5
        
        // Column X positions
        let dateColumnX = pageMargin
        let dayColumnX = dateColumnX + dateColumnWidth + columnGap
        let nightColumnX = dayColumnX + pharmacyColumnWidth + columnGap
        
        var dateColumn: [(y: CGFloat, text: String)] = []
        var dayShiftColumn: [(y: CGFloat, text: String)] = []
        var nightShiftColumn: [(y: CGFloat, text: String)] = []
        
        var lastDateText = ""
        var lastDayText = ""
        var lastNightText = ""
        
        // Scan from top to bottom with high precision
        for y in stride(from: 0, to: pageBounds.height, by: scanIncrement) {
            // Date column
            let dateRect = CGRect(x: dateColumnX, y: y, width: dateColumnWidth, height: baseHeight)
            if let selection = page.selection(for: dateRect),
               let text = selection.string?.trimmingCharacters(in: .whitespacesAndNewlines),
               !text.isEmpty,
               text != lastDateText {
                dateColumn.append((y: y, text: text))
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
        
        // Convert to simple string arrays
        let dates = dateColumn.map { $0.text }
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

    private func extractTextWithLayout(from page: PDFPage) -> [PharmacySchedule] {
        print("\nAnalyzing PDF structure first:")
        analyzePDFStructure(from: page)
        
        // Get the column text using our new function
        let (dates, dayShiftLines, nightShiftLines) = extractColumnText(from: page)
        
        print("\nProcessing text data:")
        print("Dates found: \(dates.count)")
        print("Day shift blocks: \(dayShiftLines.count)")
        print("Night shift blocks: \(nightShiftLines.count)")
        
        // Process pharmacy lines in groups of 3
        var dayPharmacies: [Pharmacy] = []
        var nightPharmacies: [Pharmacy] = []
        
        // Helper function to process lines in reverse groups of 3
        func processPharmacyLines(_ lines: [String]) -> [Pharmacy] {
            var pharmacies: [Pharmacy] = []
            var currentGroup: [String] = []
            
            // Process lines in groups of 3
            for line in lines {
                currentGroup.append(line)
                if currentGroup.count == 3 {
                    // Remember: lines are [Additional info, Address, Name] in this order
                    let additionalInfo = currentGroup[0].trimmingCharacters(in: .whitespacesAndNewlines)
                    let address = currentGroup[1].trimmingCharacters(in: .whitespacesAndNewlines)
                    let name = currentGroup[2].trimmingCharacters(in: .whitespacesAndNewlines)
                    
                    // Extract phone from additional info if present
                    var phone = ""
                    var finalAdditionalInfo = additionalInfo
                    
                    if let phoneMatch = additionalInfo.range(of: "Tfno:\\s*\\d{3}\\s*\\d{6}", options: .regularExpression) {
                        phone = String(additionalInfo[phoneMatch])
                            .replacingOccurrences(of: "Tfno:", with: "")
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                        finalAdditionalInfo = additionalInfo
                            .replacingOccurrences(of: String(additionalInfo[phoneMatch]), with: "")
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                    }
                    
                    // Create pharmacy
                    pharmacies.append(Pharmacy(
                        name: name,
                        address: address,
                        phone: phone,
                        additionalInfo: finalAdditionalInfo.isEmpty ? nil : finalAdditionalInfo
                    ))
                    
                    // Start new group
                    currentGroup = []
                }
            }
            
            return pharmacies
        }
        
        // Process both columns
        dayPharmacies = processPharmacyLines(dayShiftLines)
        nightPharmacies = processPharmacyLines(nightShiftLines)
        
        // Print size check
        print("\nSize check:")
        print("Dates: \(dates.count)")
        print("Day pharmacies: \(dayPharmacies.count)")
        print("Night pharmacies: \(nightPharmacies.count)")
        
        // Create schedules by matching dates with pharmacies
        var schedules: [PharmacySchedule] = []
        for (index, dateString) in dates.enumerated() {
            if let date = parseDate(dateString),
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

    private func loadPharmacies() {
        guard let pdfDocument = PDFDocument(url: url) else {
            pharmacies = []
            return
        }

        var allSchedules: [PharmacySchedule] = []
        print("Total pages in PDF: \(pdfDocument.pageCount)")
        
        for pageIndex in 0..<pdfDocument.pageCount {
            if let page = pdfDocument.page(at: pageIndex) {
                print("Processing page \(pageIndex + 1)")
                let schedules = extractTextWithLayout(from: page)
                allSchedules.append(contentsOf: schedules)
            }
        }

        // Update pharmacies array with all unique pharmacies from all schedules
        var uniquePharmacies: Set<String> = [] // Use pharmacy names as unique identifier
        pharmacies = allSchedules.flatMap { schedule in
            schedule.dayShiftPharmacies + schedule.nightShiftPharmacies
        }.filter { pharmacy in
            let isNew = !uniquePharmacies.contains(pharmacy.name)
            uniquePharmacies.insert(pharmacy.name)
            return isNew
        }
        
        print("\nFound \(pharmacies.count) unique pharmacies across all schedules")
    }

    private func extractPharmacyData(from page: PDFPage) -> [PharmacySchedule] {
        let pageBounds = page.bounds(for: .mediaBox)
        let blockHeight: CGFloat = 15
        let columnWidth = pageBounds.width/2
        
        var schedules: [PharmacySchedule] = []
        var leftColumnBlocks: [(y: CGFloat, text: String)] = []
        var rightColumnBlocks: [(y: CGFloat, text: String)] = []
        
        // First collect all blocks for both columns
        for y in stride(from: 0, to: pageBounds.height, by: blockHeight) {
            // Left column
            let leftRect = CGRect(x: 0, y: y, width: columnWidth, height: blockHeight)
            if let selection = page.selection(for: leftRect),
               let text = selection.string,
               !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                leftColumnBlocks.append((y: y, text: text))
            }
            
            // Right column
            let rightRect = CGRect(x: columnWidth, y: y, width: columnWidth, height: blockHeight)
            if let selection = page.selection(for: rightRect),
               let text = selection.string,
               !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                rightColumnBlocks.append((y: y, text: text))
            }
        }
        
        // Process blocks into schedules
        var currentDayPharmacyLines: [String] = []
        var currentNightPharmacyLines: [String] = []
        var currentDate: DutyDate? = nil
        
        // Process left column (day pharmacies)
        for block in leftColumnBlocks {
            let text = block.text.trimmingCharacters(in: .whitespacesAndNewlines)
            
            if let date = parseDate(text) {
                // Save previous schedule if exists
                if let date = currentDate {
                    if let dayPharmacy = parsePharmacy(from: currentDayPharmacyLines),
                       let nightPharmacy = parsePharmacy(from: currentNightPharmacyLines) {
                        schedules.append(PharmacySchedule(
                            date: date,
                            dayShiftPharmacies: [dayPharmacy],
                            nightShiftPharmacies: [nightPharmacy]
                        ))
                    }
                }
                
                // Start new schedule
                currentDate = date
                currentDayPharmacyLines = []
                currentNightPharmacyLines = []
            } else {
                currentDayPharmacyLines.append(text)
            }
        }
        
        // Process right column (night pharmacies)
        for block in rightColumnBlocks {
            let text = block.text.trimmingCharacters(in: .whitespacesAndNewlines)
            currentNightPharmacyLines.append(text)
        }
        
        // Add final schedule if exists
        if let date = currentDate,
           let dayPharmacy = parsePharmacy(from: currentDayPharmacyLines),
           let nightPharmacy = parsePharmacy(from: currentNightPharmacyLines) {
            schedules.append(PharmacySchedule(
                date: date,
                dayShiftPharmacies: [dayPharmacy],
                nightShiftPharmacies: [nightPharmacy]
            ))
        }
        
        return schedules
    }

    private func parsePharmacyPair(from line: String) -> [Pharmacy] {
        return []
    }

    private func parsePharmacies(from text: String) -> [Pharmacy] {
        let lines = text.split(separator: "\n")
        var pharmacyLines: [String] = []
        var currentPharmacyLine = ""
        var foundFirstDate = false

        for line in lines {
            if containsDate(in: String(line)) != nil {
                if !currentPharmacyLine.isEmpty {
                    pharmacyLines.append(currentPharmacyLine)
                }
                currentPharmacyLine = String(line)
                foundFirstDate = true
            } else if foundFirstDate {
                currentPharmacyLine += "\n" + line
            }
        }

        // Don't forget to add the last block
        if !currentPharmacyLine.isEmpty {
            pharmacyLines.append(currentPharmacyLine)
        }

        return pharmacyLines.flatMap { parsePharmacyPair(from: $0) }
    }

    private func containsDate(in line: String) -> String? {
        let datePattern =
            "\\b(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),\\s(\\d{1,2}\\sde\\s(?:enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\\s\\d{4})?)\\b"  // Matches dates like martes, 27 de enero or martes, 27 de enero 2026
        let regex = try? NSRegularExpression(pattern: datePattern, options: .caseInsensitive)
        let range = NSRange(location: 0, length: line.utf16.count)
        if let match = regex?.firstMatch(in: line, options: [], range: range) {
            if let dateRange = Range(match.range(at: 1), in: line) {
                return String(line[dateRange])
            }
        }
        return nil
    }
}

// Helper for date parsing
private func parseDate(_ dateString: String) -> DutyDate? {
    let datePattern = "\\b(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),\\s(\\d{1,2})\\sde\\s(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\\s(\\d{4}))?\\b"
    
    guard let regex = try? NSRegularExpression(pattern: datePattern, options: []),
          let match = regex.firstMatch(in: dateString, options: [], range: NSRange(dateString.startIndex..., in: dateString)) else {
        return nil
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
    }
    
    return DutyDate(dayOfWeek: String(dayOfWeek), day: day, month: month, year: year)
}

// Helper for pharmacy parsing
private func parsePharmacy(from lines: [String]) -> Pharmacy? {
    // Clean up and filter lines
    let nonEmptyLines = lines.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty }
    
    guard !nonEmptyLines.isEmpty else { return nil }
    
    // Find the pharmacy name (usually starts with "FARMACIA")
    let nameIndex = nonEmptyLines.firstIndex { $0.contains("FARMACIA") } ?? 0
    let name = nonEmptyLines[nameIndex]
    
    // Get lines after the name
    let remainingLines = Array(nonEmptyLines.dropFirst(nameIndex + 1))
    guard !remainingLines.isEmpty else { return nil }
    
    // First line after name is usually the address
    let address = remainingLines[0]
    
    // Remaining lines contain phone and additional info
    let infoLines = remainingLines.dropFirst().joined(separator: " ")
    
    // Extract phone number if present
    var phone = ""
    var additionalInfo = infoLines
    
    if let phoneMatch = infoLines.range(of: "Tfno:\\s*\\d{3}\\s*\\d{6}", options: .regularExpression) {
        phone = String(infoLines[phoneMatch]).replacingOccurrences(of: "Tfno:", with: "").trimmingCharacters(in: .whitespaces)
        additionalInfo = infoLines.replacingOccurrences(of: String(infoLines[phoneMatch]), with: "").trimmingCharacters(in: .whitespaces)
    }
    
    // Only keep additional info if it's not empty
    let finalAdditionalInfo = additionalInfo.isEmpty ? nil : additionalInfo
    
    return Pharmacy(
        name: name,
        address: address,
        phone: phone,
        additionalInfo: finalAdditionalInfo
    )
}

struct PDFViewScreen_Previews: PreviewProvider {
    static var previews: some View {
        PDFViewScreen(
            url: Bundle.main.url(
                forResource: "CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025", withExtension: "pdf")!)
    }
}
