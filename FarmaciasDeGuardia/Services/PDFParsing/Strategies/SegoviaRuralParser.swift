import Foundation
import PDFKit

class SegoviaRuralParser: ColumnBasedPDFParser, PDFParsingStrategy {
    /// Debug flag - when true, prints detailed parsing information
    private let debug = true
    
    func parseSchedules(from pdfDocument: PDFDocument) -> [PharmacySchedule] {
        var schedules: [PharmacySchedule] = []
        let pageCount = pdfDocument.pageCount
        
        if debug { print("📄 Processing \(pageCount) pages of Segovia Rural PDF...") }

        for pageIndex in 0..<pageCount {
            guard let page = pdfDocument.page(at: pageIndex) else { continue }
            
            if debug { print("\n📃 Processing page \(pageIndex + 1)") }
            
            // Get page dimensions
            let pageRect = page.bounds(for: .mediaBox)
            let pageWidth = pageRect.width
            let pageHeight = pageRect.height
            
            // Define fixed scanning parameters
            let baseHeight = 10.0  // Base height for a single line of text
            
            // Define scanning areas:
            // - Date column for dd-mm-yy format
            // - ZBS column for RIAZA SEPÚLVEDA (after sideways text)
            // - Pharmacy data column after the sideways weekly schedule text
            let dateColumn = TextColumn(x: 40, width: 45)     // Just wide enough for "dd-mmm-yy"
            let zbsColumn = TextColumn(x: 171.1, width: 200)     // Wide enough for RIAZA SEPÚLVEDA
            let dataColumn = TextColumn(x: 300, width: pageWidth - 350)  // Main pharmacy data after weekly schedule
            
            if debug {
                print("📐 Page dimensions: \(pageWidth) x \(pageHeight)")
                print("📏 Using base height: \(baseHeight)")
            }
            
            // Use same scanning height for both columns since text height is consistent
            let scanHeight = baseHeight * 0.6      // Smaller height to separate adjacent dates
            let scanIncrement = baseHeight * 0.5   // Smaller increment to catch each individual date
            
            if debug { 
                print("\n📍 Scanning date column from x: \(dateColumn.x), width: \(dateColumn.width)")
                print("📍 Scanning ZBS column from x: \(zbsColumn.x), width: \(zbsColumn.width)")
                print("📍 Scanning pharmacy column from x: \(dataColumn.x), width: \(dataColumn.width)")
            }
            
            // Scan all three columns with the same height parameters
            let dates = scanColumn(page, column: dateColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let zbsData = scanColumn(page, column: zbsColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let pharmacyData = scanColumn(page, column: dataColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            
            // Print found text for debugging
            if debug {
                print("\n� Combined data by line:")
                
                // Convert arrays to dictionaries for easier lookup
                let datesDict = Dictionary(uniqueKeysWithValues: dates.map { ($0.y, $0.text) })
                let zbsDict = Dictionary(uniqueKeysWithValues: zbsData.map { ($0.y, $0.text) })
                let pharmacyDict = Dictionary(uniqueKeysWithValues: pharmacyData.map { ($0.y, $0.text) })
                
                // Get all y-coordinates
                let allYCoords = Set(datesDict.keys).union(zbsDict.keys).union(pharmacyDict.keys).sorted()
                
                // Print aligned columns
                for y in allYCoords {
                    let date = datesDict[y] ?? "        "  // 8 spaces
                    let zbs = zbsDict[y] ?? "        "    // 8 spaces
                    let pharmacy = pharmacyDict[y] ?? ""
                    print(String(format: "📝 y=%.1f | %@ | %@ | %@", 
                               y,
                               date.padding(toLength: 12, withPad: " ", startingAt: 0),
                               zbs.padding(toLength: 8, withPad: " ", startingAt: 0),
                               pharmacy))
                }
            }
        }
        
        // For now, return empty array until we implement the full parsing logic
        return schedules
    }
}
