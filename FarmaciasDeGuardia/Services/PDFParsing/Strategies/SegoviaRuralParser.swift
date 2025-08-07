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
            let dateColumn = TextColumn(x: 42, width: 42)     // Just wide enough for "dd-mmm-yy"
            let dataColumn = TextColumn(x: 300, width: pageWidth - 350)  // Main pharmacy data after weekly schedule
            let fullLineColumn = TextColumn(x: 0, width: pageWidth)      // Full width to see all text in the line
            
            // ZBS columns - each represents a healthcare zone
            let riazaColumn = TextColumn(x: 175, width: 200)       // ZBS RIAZA SEPÚLVEDA
            let laGranjaColumn = TextColumn(x: 390, width: 100)    // ZBS LA GRANJA
            let laSierraColumn = TextColumn(x: 500, width: 70)    // ZBS LA SIERRA
            let fuentiduenaColumn = TextColumn(x: 570, width: 50)  // ZBS FUENTIDUEÑA
            let carboneroColumn = TextColumn(x: 620, width: 80)    // ZBS CARBONERO
            let navasDeLaAsuncionColumn = TextColumn(x: 700, width: 65)  // ZBS NAVAS DE LA ASUNCIÓN
            let villacastinColumn = TextColumn(x: 770, width: 60)  // ZBS VILLACASTÍN
            
            if debug {
                print("📐 Page dimensions: \(pageWidth) x \(pageHeight)")
                print("📏 Using base height: \(baseHeight)")
            }
            
            // Use same scanning height for both columns since text height is consistent
            let scanHeight = baseHeight * 0.8     // Smaller height to separate adjacent dates
            let scanIncrement = baseHeight * 0.8   // Move down the page in small steps
            
            if debug { 
                print("\n📍 Scanning columns:")
                print("  Date (x: \(dateColumn.x), width: \(dateColumn.width))")
                print("  Carbonero (x: \(carboneroColumn.x), width: \(carboneroColumn.width))")
                print("  Riaza (x: \(riazaColumn.x), width: \(riazaColumn.width))")
                print("  Navas de la Asunción (x: \(navasDeLaAsuncionColumn.x), width: \(navasDeLaAsuncionColumn.width))")
                print("  Villacastín (x: \(villacastinColumn.x), width: \(villacastinColumn.width))")
                print("  Pharmacy (x: \(dataColumn.x), width: \(dataColumn.width))")
            }
            
            // Scan all columns with the same height parameters
            let fullLineData = scanColumn(page, column: fullLineColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let dates = scanColumn(page, column: dateColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let riazaData = scanColumn(page, column: riazaColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let laGranjaData = scanColumn(page, column: laGranjaColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let laSierraData = scanColumn(page, column: laSierraColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let fuentiduenaData = scanColumn(page, column: fuentiduenaColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let carboneroData = scanColumn(page, column: carboneroColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let navasDeLaAsuncionData = scanColumn(page, column: navasDeLaAsuncionColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let villacastinData = scanColumn(page, column: villacastinColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let pharmacyData = scanColumn(page, column: dataColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            
            
            // Print found text for debugging
            if debug {
                
                // print("RIAZA DATA:")
                // riazaData.forEach({ print($0.text) })
                
                // Convert arrays to dictionaries for easier lookup
                let datesDict = Dictionary(uniqueKeysWithValues: dates.map { ($0.y, $0.text) })
                let riazaDict = Dictionary(uniqueKeysWithValues: riazaData.map { ($0.y, $0.text) })
                let laGranjaDict = Dictionary(uniqueKeysWithValues: laGranjaData.map { ($0.y, $0.text) })
                let laSierraDict = Dictionary(uniqueKeysWithValues: laSierraData.map { ($0.y, $0.text) })
                let fuentiduenaDict = Dictionary(uniqueKeysWithValues: fuentiduenaData.map { ($0.y, $0.text) })
                let carboneroDict = Dictionary(uniqueKeysWithValues: carboneroData.map { ($0.y, $0.text) })
                let navasDeLaAsuncionDict = Dictionary(uniqueKeysWithValues: navasDeLaAsuncionData.map { ($0.y, $0.text) })
                let villacastinDict = Dictionary(uniqueKeysWithValues: villacastinData.map { ($0.y, $0.text) })
                let pharmacyDict = Dictionary(uniqueKeysWithValues: pharmacyData.map { ($0.y, $0.text) })
                
                // Get all y-coordinates and sort them in descending order
                // We want higher Y values first since the PDF has dates from bottom to top
                let allYCoords = Set(datesDict.keys)
                    .union(riazaDict.keys)
                    .union(laGranjaDict.keys)
                    .union(laSierraDict.keys)
                    .union(fuentiduenaDict.keys)
                    .union(carboneroDict.keys)
                    .union(navasDeLaAsuncionDict.keys)
                    .union(villacastinDict.keys)
                    .union(pharmacyDict.keys)
                    .sorted(by: >)  // Sort in descending order to show Feb 1st first
                
                // Print aligned columns with full zone names
                print("\n📝 Combined data by line:")
                print("Y-Coord  | Date         | Riaza | La Granja | La Sierra | Fuentidueña | Carbonero | Navas | Villacastín | RAW")
                print("---------+-------------+-------+-----------+-----------+------------+-----------+-------+------------+----")
                
                // Create dictionary for full line data for easier lookup
                let fullLineDict = Dictionary(uniqueKeysWithValues: fullLineData.map { ($0.y, $0.text) })
                print("\n📝 Column data:")
                
                for y in allYCoords {
                    let date = datesDict[y] ?? ""
                    let riaza = riazaDict[y] ?? ""
                    let laGranja = laGranjaDict[y] ?? ""
                    let carbonero = carboneroDict[y] ?? ""

                    let pharmacy = pharmacyDict[y] ?? ""
                    let rawLine = fullLineDict[y] ?? ""
                    
                    // Skip empty rows
                    if date.isEmpty && riaza.isEmpty && laGranja.isEmpty && carbonero.isEmpty && pharmacy.isEmpty {
                        continue
                    }
                    
                    // Replace newlines and carriage returns with spaces in all fields
                    let sanitize = { (text: String) -> String in
                        text.replacingOccurrences(of: "\n", with: " ")
                            .replacingOccurrences(of: "\r", with: " ")
                    }
                    
                    // print(String(format: "%.1f | %@ | %@ | %@ | %@ | %@ | %@ | %@ | %@ | RAW: %@",
                    //            y,
                    //            sanitize(date),
                    //            sanitize(carbonero),
                    //            sanitize(cantalejo),
                    //            sanitize(riaza),
                    //            sanitize(sepulveda),
                    //            sanitize(villacastin),
                    //            sanitize(navas),
                    //            sanitize(pharmacy),
                    //            sanitize(rawLine)))

                    print(String(format: "%.1f | %@ | %@ | %@ | %@ | %@ | %@ | %@ | %@ | RAW: %@",
                                 y,
                                 sanitize(date),
                                 sanitize(riaza),
                                 sanitize(laGranja),
                                 sanitize(laSierraDict[y] ?? ""),
                                 sanitize(fuentiduenaDict[y] ?? ""),
                                 sanitize(carboneroDict[y] ?? ""),
                                 sanitize(navasDeLaAsuncionDict[y] ?? ""),
                                 sanitize(villacastinDict[y] ?? ""),
                                 sanitize(rawLine)))
                }
            }
        }
        
        // For now, return empty array until we implement the full parsing logic
        return schedules
    }
}
