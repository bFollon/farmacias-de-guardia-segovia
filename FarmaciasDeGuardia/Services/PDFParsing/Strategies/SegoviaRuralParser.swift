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
            
            // Define scanning areas for all ZBS (Zonas Básicas de Salud)
            // Common columns
            let dateColumn = TextColumn(x: 40, width: 45)     // Just wide enough for "dd-mmm-yy"
            let dataColumn = TextColumn(x: 300, width: pageWidth - 350)  // Main pharmacy data after weekly schedule
            
            // ZBS columns - each represents a healthcare zone
            let carboneroColumn = TextColumn(x: 90, width: 35)    // CARBONERO
            let cantalejoColumn = TextColumn(x: 130, width: 35)   // CANTALEJO
            let riazaColumn = TextColumn(x: 170, width: 35)       // RIAZA
            let sepulvedaColumn = TextColumn(x: 210, width: 45)   // SEPÚLVEDA - wider to catch "(Soria)" text
            let villacastinColumn = TextColumn(x: 260, width: 35) // VILLACASTÍN
            let navasColumn = TextColumn(x: 300, width: 35)       // NAVA
            
            if debug {
                print("📐 Page dimensions: \(pageWidth) x \(pageHeight)")
                print("📏 Using base height: \(baseHeight)")
            }
            
            // Use same scanning height for both columns since text height is consistent
            let scanHeight = baseHeight * 0.6      // Smaller height to separate adjacent dates
            let scanIncrement = baseHeight * 0.5   // Smaller increment to catch each individual date
            
            if debug { 
                print("\n📍 Scanning columns:")
                print("  Date (x: \(dateColumn.x), width: \(dateColumn.width))")
                print("  Carbonero (x: \(carboneroColumn.x), width: \(carboneroColumn.width))")
                print("  Cantalejo (x: \(cantalejoColumn.x), width: \(cantalejoColumn.width))")
                print("  Riaza (x: \(riazaColumn.x), width: \(riazaColumn.width))")
                print("  Sepúlveda (x: \(sepulvedaColumn.x), width: \(sepulvedaColumn.width))")
                print("  Villacastín (x: \(villacastinColumn.x), width: \(villacastinColumn.width))")
                print("  Navas (x: \(navasColumn.x), width: \(navasColumn.width))")
                print("  Pharmacy (x: \(dataColumn.x), width: \(dataColumn.width))")
            }
            
            // Scan all columns with the same height parameters
            let dates = scanColumn(page, column: dateColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let carboneroData = scanColumn(page, column: carboneroColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let cantalejoData = scanColumn(page, column: cantalejoColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let riazaData = scanColumn(page, column: riazaColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let sepulvedaData = scanColumn(page, column: sepulvedaColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let villacastinData = scanColumn(page, column: villacastinColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let navasData = scanColumn(page, column: navasColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let pharmacyData = scanColumn(page, column: dataColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            
            // Print found text for debugging
            if debug {
                print("\n📝 Combined data by line:")
                
                // Convert arrays to dictionaries for easier lookup
                let datesDict = Dictionary(uniqueKeysWithValues: dates.map { ($0.y, $0.text) })
                let carboneroDict = Dictionary(uniqueKeysWithValues: carboneroData.map { ($0.y, $0.text) })
                let cantalejoDict = Dictionary(uniqueKeysWithValues: cantalejoData.map { ($0.y, $0.text) })
                let riazaDict = Dictionary(uniqueKeysWithValues: riazaData.map { ($0.y, $0.text) })
                let sepulvedaDict = Dictionary(uniqueKeysWithValues: sepulvedaData.map { ($0.y, $0.text) })
                let villacastinDict = Dictionary(uniqueKeysWithValues: villacastinData.map { ($0.y, $0.text) })
                let navasDict = Dictionary(uniqueKeysWithValues: navasData.map { ($0.y, $0.text) })
                let pharmacyDict = Dictionary(uniqueKeysWithValues: pharmacyData.map { ($0.y, $0.text) })
                
                // Get all y-coordinates
                let allYCoords = Set(datesDict.keys)
                    .union(carboneroDict.keys)
                    .union(cantalejoDict.keys)
                    .union(riazaDict.keys)
                    .union(sepulvedaDict.keys)
                    .union(villacastinDict.keys)
                    .union(navasDict.keys)
                    .union(pharmacyDict.keys)
                    .sorted()
                
                // Print aligned columns with full zone names
                print("Y-Coord  | Date     | Carbonero | Cantalejo | Riaza | Sepúlveda | Villacastín | Navas | Pharmacy")
                print("---------+----------+-----------+-----------+-------+-----------+------------+-------+---------")
                
                for y in allYCoords {
                    let date = datesDict[y] ?? "        "
                    let carbonero = carboneroDict[y] ?? "   "
                    let cantalejo = cantalejoDict[y] ?? "   "
                    let riaza = riazaDict[y] ?? "   "
                    let sepulveda = sepulvedaDict[y] ?? "   "
                    let villacastin = villacastinDict[y] ?? "   "
                    let navas = navasDict[y] ?? "   "
                    let pharmacy = pharmacyDict[y] ?? ""
                    
                    print(String(format: "%.1f | %@ | %@ | %@ | %@ | %@ | %@ | %@ | %@",
                               y,
                               date.padding(toLength: 8, withPad: " ", startingAt: 0),
                               carbonero.padding(toLength: 9, withPad: " ", startingAt: 0),
                               cantalejo.padding(toLength: 9, withPad: " ", startingAt: 0),
                               riaza.padding(toLength: 5, withPad: " ", startingAt: 0),
                               sepulveda.padding(toLength: 9, withPad: " ", startingAt: 0),
                               villacastin.padding(toLength: 10, withPad: " ", startingAt: 0),
                               navas.padding(toLength: 5, withPad: " ", startingAt: 0),
                               pharmacy))
                }
            }
        }
        
        // For now, return empty array until we implement the full parsing logic
        return schedules
    }
}
