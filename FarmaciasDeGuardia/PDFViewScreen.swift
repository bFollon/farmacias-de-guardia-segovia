import PDFKit
import SwiftUI

// Models for pharmacy data
struct DutyDate {
    let dayOfWeek: String
    let day: Int
    let month: String
    let year: Int?
}

extension DutyDate {
    func toTimestamp() -> TimeInterval? {
        let calendar = Calendar.current
        let year = self.year ?? getCurrentYear()
        
        // Convert Spanish month to number (1-12)
        guard let month = DutyDate.monthToNumber(self.month) else { return nil }
        
        // Create date components
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = self.day
        // Set to start of day
        components.hour = 0
        components.minute = 0
        components.second = 0
        
        // Convert to date
        guard let date = calendar.date(from: components) else { return nil }
        return date.timeIntervalSince1970
    }
    
    static func monthToNumber(_ month: String) -> Int? {
        let months = [
            "enero": 1, "febrero": 2, "marzo": 3, "abril": 4,
            "mayo": 5, "junio": 6, "julio": 7, "agosto": 8,
            "septiembre": 9, "octubre": 10, "noviembre": 11, "diciembre": 12
        ]
        return months[month.lowercased()]
    }
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
    @State private var schedules: [PharmacySchedule] = []
    var url: URL

    var body: some View {
        NavigationView {
            Group {
                if let schedule = findTodaysSchedule() {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("\(schedule.date.dayOfWeek), \(schedule.date.day) de \(schedule.date.month) \(String(schedule.date.year ?? getCurrentYear()))")
                                .font(.title2)
                                .padding(.bottom, 5)
                            
                            // Day shift section
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Guardia diurna")
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                                ForEach(schedule.dayShiftPharmacies) { pharmacy in
                                    PharmacyView(pharmacy: pharmacy)
                                }
                            }
                            .padding(.bottom, 10)
                            
                            // Night shift section
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Guardia nocturna")
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                                ForEach(schedule.nightShiftPharmacies) { pharmacy in
                                    PharmacyView(pharmacy: pharmacy)
                                }
                            }
                        }
                        .padding()
                    }
                } else {
                    VStack(spacing: 20) {
                        Text("No hay farmacias de guardia programadas para hoy")
                            .font(.headline)
                            .multilineTextAlignment(.center)
                        
                        Text("(\(Date().formatted(date: .long, time: .omitted)))")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                }
            }
            .navigationTitle("Farmacias de Guardia Hoy")
        }
        .onAppear {
            loadPharmacies()
        }
    }

    private func findTodaysSchedule() -> PharmacySchedule? {
        let calendar = Calendar.current
        var components = DateComponents()
        components.year = calendar.component(.year, from: Date())
        components.month = calendar.component(.month, from: Date())
        components.day = calendar.component(.day, from: Date())
        components.hour = 0
        components.minute = 0
        components.second = 0
        
        guard let today = calendar.date(from: components) else { return nil }
        let todayTimestamp = today.timeIntervalSince1970
        
        return schedules.first { schedule in
            guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
            return Int(scheduleTimestamp) == Int(todayTimestamp)
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
        var pendingYear: String? = nil
        let currentYear = getCurrentYear()
        
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
                    
                    // Only process if the name contains "FARMACIA"
                    if name.contains("FARMACIA") {
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
                    } else {
                        print("Skipping non-pharmacy entry: \(name)")
                    }
                    
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
            schedules = []
            return
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
        schedules = allSchedules.sorted { first, second in
            let currentYear = getCurrentYear()
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
        
        print("\nLoaded \(schedules.count) schedules")
        
        // Print timestamps for validation
        print("\n=== Date Timestamps ===")
        for schedule in schedules {
            if let timestamp = schedule.date.toTimestamp() {
                let date = Date(timeIntervalSince1970: timestamp)
                let formatter = DateFormatter()
                formatter.dateStyle = .full
                formatter.timeStyle = .none
                formatter.locale = Locale(identifier: "es_ES")
                print("\(schedule.date.dayOfWeek), \(schedule.date.day) de \(schedule.date.month) \(schedule.date.year ?? getCurrentYear())")
                print("Timestamp: \(timestamp)")
                print("Parsed back: \(formatter.string(from: date))\n")
            } else {
                print("Failed to convert date: \(schedule.date.dayOfWeek), \(schedule.date.day) de \(schedule.date.month) \(schedule.date.year ?? getCurrentYear())\n")
            }
        }
        print("=== End Timestamps ===\n")
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
    print("\nAttempting to parse date: '\(dateString)'")
    
    let datePattern = "\\b(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),\\s(\\d{1,2})\\sde\\s(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\\s(\\d{4}))?\\b"
    
    print("Using pattern: \(datePattern)")
    
    guard let regex = try? NSRegularExpression(pattern: datePattern, options: []),
          let match = regex.firstMatch(in: dateString, options: [], range: NSRange(dateString.startIndex..., in: dateString)) else {
        print("Failed to match regex pattern")
        return nil
    }
    
    print("Number of capture groups: \(match.numberOfRanges)")
    for i in 0..<match.numberOfRanges {
        let range = match.range(at: i)
        print("Group \(i) - location: \(range.location), length: \(range.length)")
        if range.location != NSNotFound, let stringRange = Range(range, in: dateString) {
            print("Group \(i) value: '\(dateString[stringRange])'")
        }
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
        print("Found year in capture group: \(year ?? -1)")
    } else {
        let currentYear = getCurrentYear()
        // Temporary fix: Only January 1st and 2nd are from next year
        if month.lowercased() == "enero" && (day == 1 || day == 2) {
            year = currentYear + 1
            print("Applied temporary fix for January 1st/2nd: setting year to \(currentYear + 1)")
        } else {
            year = currentYear
            print("No year found, defaulting to \(currentYear)")
        }
    }
    
    print("Parsed result: \(dayOfWeek), \(day) de \(month) \(year ?? 2025)")
    
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

// Helper view for displaying pharmacy information
private struct PharmacyView: View {
    let pharmacy: Pharmacy
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(pharmacy.name)
                .font(.system(.body, design: .rounded))
            Text(pharmacy.address)
                .font(.subheadline)
            if !pharmacy.phone.isEmpty {
                Text("📞 \(pharmacy.phone)")
                    .font(.footnote)
            }
            if let info = pharmacy.additionalInfo {
                Text(info)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

struct PDFViewScreen_Previews: PreviewProvider {
    static var previews: some View {
        PDFViewScreen(
            url: Bundle.main.url(
                forResource: "CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025", withExtension: "pdf")!)
    }
}

// Helper function to get current and next year
private func getCurrentYear() -> Int {
    let calendar = Calendar.current
    return calendar.component(.year, from: Date())
}


