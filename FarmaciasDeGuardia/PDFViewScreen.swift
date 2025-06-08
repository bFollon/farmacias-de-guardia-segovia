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
        
        // Use smallest font size as our base scanning height, or default to 5pt if no fonts found
        let baseHeight: CGFloat = fontSizes.min() ?? 5.0
        // Use slightly smaller increment to ensure we don't miss anything
        let scanIncrement: CGFloat = baseHeight / 2
        
        print("\n=== Layout Analysis ===")
        
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
        
        // Remove duplicate adjacent lines that might have been captured
        dateColumn = removeDuplicateAdjacent(blocks: dateColumn)
        dayShiftColumn = removeDuplicateAdjacent(blocks: dayShiftColumn)
        nightShiftColumn = removeDuplicateAdjacent(blocks: nightShiftColumn)
        
        // Print results in order from top to bottom
        print("\nDate Column:")
        for block in dateColumn {
            print("\nY: \(String(format: "%.2f", block.y))")
            print(block.text)
        }
        
        print("\nDay Shift Column:")
        for block in dayShiftColumn {
            print("\nY: \(String(format: "%.2f", block.y))")
            print(block.text)
        }
        
        print("\nNight Shift Column:")
        for block in nightShiftColumn {
            print("\nY: \(String(format: "%.2f", block.y))")
            print(block.text)
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

    private func extractTextWithLayout(from page: PDFPage) -> String {
        // First analyze the structure
        analyzePDFStructure(from: page)
        
        let pageBounds = page.bounds(for: .mediaBox)
        var extractedText = ""
        
        // Use smaller increments (25pt) for better granularity
        for y in stride(from: 0, to: pageBounds.height, by: 25) {
            if let selection = page.selection(for: CGRect(x: 0, y: y, width: pageBounds.width, height: 25)) {
                if let text = selection.string, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    extractedText += text + "\n"
                }
            }
        }
        
        return extractedText
    }

    private func loadPharmacies() {
        guard let pdfDocument = PDFDocument(url: url) else {
            pharmacies = []
            return
        }

        var extractedText = ""
        print("Total pages in PDF: \(pdfDocument.pageCount)")
        for pageIndex in 0..<pdfDocument.pageCount {
            if let page = pdfDocument.page(at: pageIndex) {
                print("Extracting text from page \(pageIndex + 1)")
                extractedText += extractTextWithLayout(from: page) + "\n"
            }
        }

        print("Extracted Text from PDF:\n\(extractedText)")  // Debug output
        pharmacies = []
        // pharmacies = parsePharmacies(from: extractedText)
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
        var currentDayPharmacies: [Pharmacy] = []
        var currentNightPharmacies: [Pharmacy] = []
        var currentDate: DutyDate? = nil
        var currentPharmacyText = ""
        
        // Process left column (day pharmacies)
        for block in leftColumnBlocks {
            let text = block.text.trimmingCharacters(in: .whitespacesAndNewlines)
            
            if let date = parseDate(text) {
                // Save previous schedule if exists
                if let date = currentDate {
                    schedules.append(PharmacySchedule(
                        date: date,
                        dayShiftPharmacies: currentDayPharmacies,
                        nightShiftPharmacies: currentNightPharmacies
                    ))
                }
                
                // Start new schedule
                currentDate = date
                currentDayPharmacies = []
                currentNightPharmacies = []
                currentPharmacyText = ""
            } else {
                currentPharmacyText += text + "\n"
                
                // Try to parse pharmacy when we have accumulated enough lines
                if text.contains("\n\n") || text.contains(".") {
                    if let pharmacy = parsePharmacy(from: currentPharmacyText) {
                        currentDayPharmacies.append(pharmacy)
                        currentPharmacyText = ""
                    }
                }
            }
        }
        
        // Process right column (night pharmacies)
        currentPharmacyText = ""
        for block in rightColumnBlocks {
            let text = block.text.trimmingCharacters(in: .whitespacesAndNewlines)
            currentPharmacyText += text + "\n"
            
            // Try to parse pharmacy when we have accumulated enough lines
            if text.contains("\n\n") || text.contains(".") {
                if let pharmacy = parsePharmacy(from: currentPharmacyText) {
                    currentNightPharmacies.append(pharmacy)
                    currentPharmacyText = ""
                }
            }
        }
        
        // Add final schedule if exists
        if let date = currentDate {
            schedules.append(PharmacySchedule(
                date: date,
                dayShiftPharmacies: currentDayPharmacies,
                nightShiftPharmacies: currentNightPharmacies
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
private func parsePharmacy(from text: String) -> Pharmacy? {
    let lines = text.split(separator: "\n")
    guard lines.count >= 2 else { return nil }
    
    let name = String(lines[0]).trimmingCharacters(in: .whitespaces)
    let address = String(lines[1]).trimmingCharacters(in: .whitespaces)
    
    var phone = ""
    var additionalInfo: String? = nil
    
    if lines.count > 2 {
        let remainingText = lines[2...].joined(separator: "\n").trimmingCharacters(in: .whitespaces)
        // Extract phone number - assuming it's in a standard format like "987654321"
        if let phoneMatch = remainingText.range(of: "\\d{9}", options: .regularExpression) {
            phone = String(remainingText[phoneMatch])
            let additionalText = remainingText.replacingOccurrences(of: phone, with: "").trimmingCharacters(in: .whitespaces)
            if !additionalText.isEmpty {
                additionalInfo = additionalText
            }
        } else {
            additionalInfo = remainingText
        }
    }
    
    return Pharmacy(name: name, address: address, phone: phone, additionalInfo: additionalInfo)
}

struct PDFViewScreen_Previews: PreviewProvider {
    static var previews: some View {
        PDFViewScreen(
            url: Bundle.main.url(
                forResource: "CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025", withExtension: "pdf")!)
    }
}
