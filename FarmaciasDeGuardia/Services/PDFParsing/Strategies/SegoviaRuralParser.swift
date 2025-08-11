import Foundation
import PDFKit

class SegoviaRuralParser: ColumnBasedPDFParser, PDFParsingStrategy {
    /// Debug flag - when true, prints detailed parsing information
    private let debug = true
    
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
    
    // ZBS Schedule types
    private enum ScheduleType {
        case fullDay     // 24h
        case extended    // 10-22h
        case standard    // 10-20h
        
        var hours: String {
            switch self {
            case .fullDay: return "24h"
            case .extended: return "10h-22h"
            case .standard: return "10h-20h"
            }
        }
    }
    
    private let zbsSchedules: [String: ScheduleType] = [
        "RIAZA SEPÚLVEDA": .fullDay,
        "LA GRANJA": .extended,
        "LA SIERRA": .standard,
        "FUENTIDUEÑA": .standard,
        "CARBONERO": .standard,
        "NAVAS DE LA ASUNCIÓN": .standard,
        "VILLACASTÍN": .standard
    ]
    
    private func parseDate(_ text: String) -> DutyDate? {
        // Date format: "dd-mmm-yy"
        let components = text.components(separatedBy: "-")
        guard components.count == 3,
              let day = Int(components[0]),
              let year = Int(components[2]) else {
            return nil
        }
        
        // For now, assume the day of week doesn't matter since we can calculate it
        // We'll use "Unknown" as a placeholder
        return DutyDate(
            dayOfWeek: "Unknown",
            day: day,
            month: components[1],
            year: 2000 + year  // Convert YY to YYYY
        )
    }
    
    private func createPharmacy(name: String, zbs: String) -> Pharmacy {
        // For now, use placeholder address and phone
        // The schedule type can be determined from the ZBS
        let scheduleInfo = zbsSchedules[zbs]?.hours ?? "unknown"
        let additionalInfo = "Horario: \(scheduleInfo)"
        
        return Pharmacy(
            name: name,
            address: "Address pending",
            phone: "Phone pending",
            additionalInfo: additionalInfo
        )
    }
    
    func parseSchedules(from pdfDocument: PDFDocument) -> [PharmacySchedule] {
        var schedules: [PharmacySchedule] = []
        let pageCount = pdfDocument.pageCount
        
        if debug { print("\n=== Segovia Rural Schedules ===") }
        
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
                print("  Riaza (x: \(riazaColumn.x), width: \(riazaColumn.width))")
                print("  La Granja (x: \(laGranjaColumn.x), width: \(laGranjaColumn.width))")
                print("  La Sierra (x: \(laSierraColumn.x), width: \(laSierraColumn.width))")
                print("  Fuentidueña (x: \(fuentiduenaColumn.x), width: \(fuentiduenaColumn.width))")
                print("  Carbonero (x: \(carboneroColumn.x), width: \(carboneroColumn.width))")
                print("  Navas de la Asunción (x: \(navasDeLaAsuncionColumn.x), width: \(navasDeLaAsuncionColumn.width))")
                print("  Villacastín (x: \(villacastinColumn.x), width: \(villacastinColumn.width))")
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
            
            
            // Print found text for debugging
            if debug {
                // Convert arrays to dictionaries for easier lookup
                let datesDict = Dictionary(uniqueKeysWithValues: dates.map { ($0.y, $0.text) })
                let riazaDict = Dictionary(uniqueKeysWithValues: riazaData.map { ($0.y, $0.text) })
                let laGranjaDict = Dictionary(uniqueKeysWithValues: laGranjaData.map { ($0.y, $0.text) })
                let laSierraDict = Dictionary(uniqueKeysWithValues: laSierraData.map { ($0.y, $0.text) })
                let fuentiduenaDict = Dictionary(uniqueKeysWithValues: fuentiduenaData.map { ($0.y, $0.text) })
                let carboneroDict = Dictionary(uniqueKeysWithValues: carboneroData.map { ($0.y, $0.text) })
                let navasDeLaAsuncionDict = Dictionary(uniqueKeysWithValues: navasDeLaAsuncionData.map { ($0.y, $0.text) })
                let villacastinDict = Dictionary(uniqueKeysWithValues: villacastinData.map { ($0.y, $0.text) })
                
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
                    .sorted(by: >)  // Sort in descending order to show Feb 1st first
                
                // Print aligned columns with full zone names
                print("\n📝 Combined data by line:")
                print("Y-Coord  | Date         | Riaza | La Granja | La Sierra | Fuentidueña | Carbonero | Navas | Villacastín | RAW")
                print("---------+-------------+-------+-----------+-----------+------------+-----------+-------+------------+----")
                
                // Create dictionary for full line data for easier lookup
                let fullLineDict = Dictionary(uniqueKeysWithValues: fullLineData.map { ($0.y, $0.text) })
                print("\n📝 Column data:")
                
                for y in allYCoords {
                    let dateStr = datesDict[y] ?? ""
                    let riaza = riazaDict[y] ?? ""
                    let laGranja = laGranjaDict[y] ?? ""
                    let laSierra = laSierraDict[y] ?? ""
                    let fuentidueña = fuentiduenaDict[y] ?? ""
                    let carbonero = carboneroDict[y] ?? ""
                    let navasAsuncion = navasDeLaAsuncionDict[y] ?? ""
                    let villacastin = villacastinDict[y] ?? ""
                    let rawLine = fullLineDict[y] ?? ""
                    
                    // Replace newlines and carriage returns with spaces in all fields
                    let sanitize = { (text: String) -> String in
                        text.replacingOccurrences(of: "\n", with: " ")
                            .replacingOccurrences(of: "\r", with: " ")
                    }
                    
                    let sanitizedDateStr = sanitize(dateStr)
                    
                    // Skip if there's no valid date
                    guard !sanitizedDateStr.isEmpty,
                          let date = parseDate(sanitizedDateStr) else {
                        continue
                    }
                    
                    // Create pharmacies for each non-empty cell
                    var pharmacies: [Pharmacy] = []
                    
                    let zbsData = [
                        ("RIAZA SEPÚLVEDA", sanitize(riaza)),
                        ("LA GRANJA", sanitize(laGranja)),
                        ("LA SIERRA", sanitize(laSierra)),
                        ("FUENTIDUEÑA", sanitize(fuentidueña)),
                        ("CARBONERO", sanitize(carbonero)),
                        ("NAVAS DE LA ASUNCIÓN", sanitize(navasAsuncion)),
                        ("VILLACASTÍN", sanitize(villacastin))
                    ]
                    
                    for (zbs, pharmacyName) in zbsData {
                        if !pharmacyName.isEmpty {
                            pharmacies.append(createPharmacy(name: pharmacyName, zbs: zbs))
                        }
                    }
                    
                    // Create the schedule using fullDay shift since the PDF doesn't 
                    // distinguish between day/night shifts
                    if !pharmacies.isEmpty {
                        let schedule = PharmacySchedule(
                            date: date,
                            shifts: [
                                .fullDay: pharmacies
                            ]
                        )
                        schedules.append(schedule)
                    }
                    
                    print(String(format: "%.1f | %@ | %@ | %@ | %@ | %@ | %@ | %@ | %@ | RAW: %@",
                                 y,
                                 sanitizedDateStr,
                                 sanitize(riaza),
                                 sanitize(laGranja),
                                 sanitize(laSierra),
                                 sanitize(fuentidueña),
                                 sanitize(carbonero),
                                 sanitize(navasAsuncion),
                                 sanitize(villacastin),
                                 sanitize(rawLine)))
                }
            }
        }
        
        // For now, return empty array until we implement the full parsing logic
        return schedules
    }
}
