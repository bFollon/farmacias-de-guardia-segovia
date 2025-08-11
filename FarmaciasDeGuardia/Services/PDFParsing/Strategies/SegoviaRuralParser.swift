import Foundation
import PDFKit

class SegoviaRuralParser: ColumnBasedPDFParser, PDFParsingStrategy {
    /// Debug flag - when true, prints detailed parsing information
    private let debug = true
    
    /// Store ZBS schedules separately for access by ZBSScheduleService
    static private var cachedZBSSchedules: [ZBSSchedule] = []
    
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
    
    // Pharmacy information lookup table for Segovia Rural
    // TODO: Fill in the actual pharmacy details for each location
    private let pharmacyInfo: [String: (name: String, address: String, phone: String)] = [
        // Riaza-Sepúlveda ZBS pharmacies
        "RIAZA": (
            name: "Farmacia César Fernando Gutiérrez Miguel",
            address: "C. Ricardo Provencio, 16, 40500 Riaza, Segovia",
            phone: "921550131"
        ),
        "SEPÚLVEDA": (
            name: "Farmacia Francisco Ruiz Carrasco",
            address: "Pl. España, 16, 40300 Sepúlveda, Segovia", 
            phone: "921540018"
        ),
        "S.E. GORMAZ (Soria)": (
            name: "Farmacia Irigoyen",
            address: "C. Escuelas, 5, 42330 San Esteban de Gormaz, Soria", 
            phone: "975350208"
        ),
        "CEREZO ABAJO": (
            name: "Farmacia Mario Caballero Serrano",
            address: "C. Real, 2, 40591 Cerezo de Abajo, Segovia", 
            phone: "921557110"
        ),
        "BOCEGUILLAS": (
            name: "Farmacia Lcda Mª del Pilar Villas Miguel",
            address: "C. Bayona, 21, 40560 Boceguillas, Segovia", 
            phone: "921543849"
        ),
        "AYLLON": (
            name: "Farmacia Luis de la Peña Buquerin",
            address: "Plaza Mayor, 12, 40520 Ayllón, Segovia", 
            phone: "921553003"
        ),
        
        // La Granja ZBS pharmacies
        "LA GRANJA": (
            name: "Farmacia Cristina Mínguez Del Pozo",
            address: "C. Valenciana, 3, BAJO, 40100 Real Sitio de San Ildefonso, Segovia",
            phone: "921470038"
        ),
        
        // La Sierra ZBS pharmacies
        "PRÁDENA": (
            name: "Farmacia Ana Belén Tomero Díez",
            address: "Calle Pl., 18, 40165 Prádena, Segovia",
            phone: "921507050"
        ),
        "ARCONES": (
            name: "Farmacia Teresa Laporta Sánchez",
            address: "Pl. Mayor, 3, 40164 Arcones, Segovia",
            phone: "921504134"
        ),
        "NAVAFRÍA": (
            name: "Farmacia Martín Cuesta",
            address: "C. la Reina, 0, 40161 Navafría, Segovia",
            phone: "921506113"
        ),
        "TORREVAL": (
            name: "Farmacia Lda. Mónica Carrasco Herrero",
            address: "Travesia la Fragua, 16, 40171 Torre Val de San Pedro, Segovia",
            phone: "921506028"
        ),
        
        // Fuentidueña ZBS pharmacies
        "HONTALBILLA": (
            name: "Farmacia Lcdo Burgos Burgos Isabel",
            address: "Plaza Mayor, 1, 40353 Hontalbilla, Segovia",
            phone: "921148190"
        ),
        "TORRECILLA": (
            name: "Farmacia Lcdo Gallego Esteban Fernando",
            address: "C. Povedas, 6, 40359 Torrecilla del Pinar, Segovia",
            phone: "No disponible"
        ),
        "OLOMBRADA": (
            name: "Dr. Jesús Santos del Cura",
            address: "C. Real, 3, 40220 Olombrada, Segovia",
            phone: "921164327"
        ),
        "FUENTIDUEÑA": (
            name: "Farmacia Fuentidueña",
            address: "C. Real, 40, 40357 Fuentidueña, Segovia",
            phone: "921533630"
        ),
        "FUENTESAUCO": (
            name: "Farmacia Paloma María Prieto Pérez",
            address: "S N, Plaza Mercado, 0, 40355 Fuentesaúco de Fuentidueña, Segovia",
            phone: "No disponible"
        ),
        
        // Carbonero ZBS pharmacies
        "NAVALMANZANO": (
            name: "Farmacia Carmen I. Tomero Díez",
            address: "Pl. Mayor, 2, 40280 Navalmanzano, Segovia",
            phone: "921575109"
        ),
        "CARBONERO M": (
            name: "Farmacia Carbonero",
            address: "Pl. Pósito Real, 1, 40270 Carbonero el Mayor, Segovia",
            phone: "921560427"
        ),
        "ZARZUELA PINAR": (
            name: "Farmacia Maria Sol Benito Sanz",
            address: "C/ Caño, 7, 40293 Zarzuela del Pinar (Segovia)",
            phone: "921574621"
        ),
        "ESCARABAJOSA": (
            name: "Farmacia GILSANZ",
            address: "Pl. Mayor, 40291 Escarabajosa de Cabezas, Segovia",
            phone: "921562159"
        ),
        "LASTRAS DE CUÉLLAR": (
            name: "Farmacia Mª Antonia Sacristán Rodríguez",
            address: "C. Rincón, 3, 40352 Lastras de Cuéllar, Segovia",
            phone: "921169250"
        ),
        "FUENTEPELAYO": (
            name: "Farmacia Lda. Patricia Avellón Senovilla",
            address: "C. Santillana, 3, 40260 Fuentepelayo, Segovia",
            phone: "921574392"
        ),
        "CANTIMPALOS": (
            name: "Farmacia Enrique Covisa Nager",
            address: "Pl. Mayor, 17, 40360 Cantimpalos, Segovia",
            phone: "921496025"
        ),
        "AGUILAFUENTE": (
            name: "Farmacia Miriam Chamorro García",
            address: "Av. del Escultor D. Florentino Trapero, 5, 40340 Aguilafuente, Segovia",
            phone: "921572445"
        ),
        "MOZONCILLO": (
            name: "Farmacia Isabel Frías López",
            address: "C. Real, 16-18, 40250 Mozoncillo, Segovia",
            phone: "921577273"
        ),
        "COCA": (
            name: "Farmacia Ana Isabel Maroto Arenas",
            address: "Pl. Arco, 2, 40480 Coca, Segovia",
            phone: "921586677"
        ),
        "STA. Mª REAL": (
            name: "Farmacia Pilar Tribiño Mendiola",
            address: "Pl. Mayor, 11, 40440 Santa María la Real de Nieva, Segovia",
            phone: "921594013"
        ),
        "NIEVA": (
            name: "Farmacia María Dolores Gómez Roán",
            address: "Calle Ayuntamiento, 12, 40447 Nieva, Segovia",
            phone: "921594727"
        ),
        "SANTIUSTE": (
            name: "Farmacia Lda Amparo Maroto Gomez",
            address: "Pl. Iglesia, 5, 40460 Santiuste de San Juan Bautista, Segovia",
            phone: "921596259"
        ),
        "NAVAS DE ORO": (
            name: "Farmacia Cubero. Gdo. Sergio Cubero de Blas",
            address: "C. Libertad, 1, 40470 Navas de Oro, Segovia",
            phone: "921591585"
        ),
        "NAVAS DE LA ASUNCIÓN": (
            name: "Farmacia Ldo. Vicente Rebollo Antolín Javier",
            address: "C. de Elías Vírseda, 3, 40450 Nava de la Asunción, Segovia",
            phone: "921580533"
        ),
        "BERNARDOS": (
            name: "Farmacia Lcdo Casado Rata Coral",
            address: "Pl. Mayor, 8, 40430 Bernardos, Segovia",
            phone: "921566012"
        ),
        "VILLACASTÍN": (
            name: "Farmacia Cristina Herradón Gil-Gallardo",
            address: "Calle Iglesia, 18, 40150 Villacastín, Segovia",
            phone: "921198173"
        ),
        "ZARZUELA M.": (
            name: "Farmacia María A. Reviriego Morcuende",
            address: "Av. San Antonio, 2, 40152 Zarzuela del Monte, Segovia",
            phone: "921198297"
        ),
        "NAVAS DE SAN ANTONIO": (
            name: "Farmacia María José Martín Barguilla",
            address: "C. Diana, 21, 40408 Navas de San Antonio, Segovia",
            phone: "921193128"
        )
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
    
    private func createPharmacy(name: String, zbsId: String) -> Pharmacy {
        // Map ZBS ID to display name for schedule info
        let zbsDisplayName = ZBS.availableZBS.first { $0.id == zbsId }?.name ?? zbsId
        
        // Determine schedule type based on ZBS
        let scheduleInfo: String
        switch zbsId {
        case "riaza-sepulveda":
            scheduleInfo = "24h"
        case "la-granja":
            scheduleInfo = "10h-22h"
        default:
            scheduleInfo = "10h-20h"
        }
        
        // Look up pharmacy information using the parsed name as key
        let lookupKey = name.uppercased()
        let info = pharmacyInfo[lookupKey] ?? {
            // Log when no match is found
            if debug {
                print("⚠️ No pharmacy info found for key: '\(lookupKey)' (original: '\(name)')")
                print("📋 Available keys: \(Array(pharmacyInfo.keys).sorted())")
            }
            return (
                name: name, // Use the parsed name as fallback
                address: "Dirección no disponible",
                phone: "No disponible"
            )
        }()
        
        let additionalInfo = "Horario: \(scheduleInfo) - ZBS: \(zbsDisplayName)"
        
        return Pharmacy(
            name: info.name,
            address: info.address,
            phone: info.phone,
            additionalInfo: additionalInfo
        )
    }
    
    
    /// Get cached ZBS schedules
    static func getCachedZBSSchedules() -> [ZBSSchedule] {
        return cachedZBSSchedules
    }
    
    /// Clear cached ZBS schedules
    static func clearZBSCache() {
        cachedZBSSchedules = []
    }
    
    func parseSchedules(from pdfDocument: PDFDocument) -> [PharmacySchedule] {
        var schedules: [PharmacySchedule] = []
        var zbsSchedules: [ZBSSchedule] = []
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
                    
                    // Create a comprehensive ZBS schedule for this date
                    // Include ALL ZBS, even if some have no pharmacy on duty
                    let zbsData = [
                        ("riaza-sepulveda", sanitize(riaza)),
                        ("la-granja", sanitize(laGranja)),
                        ("la-sierra", sanitize(laSierra)),
                        ("fuentidueña", sanitize(fuentidueña)),
                        ("carbonero", sanitize(carbonero)),
                        ("navas-asuncion", sanitize(navasAsuncion)),
                        ("villacastin", sanitize(villacastin))
                    ]
                    
                    var schedulesByZBS: [String: [Pharmacy]] = [:]
                    
                    // Initialize all ZBS with empty arrays
                    for (zbsId, _) in zbsData {
                        schedulesByZBS[zbsId] = []
                    }
                    
                    // Add pharmacies where they exist
                    for (zbsId, pharmacyName) in zbsData {
                        if !pharmacyName.isEmpty {
                            let pharmacy = createPharmacy(name: pharmacyName, zbsId: zbsId)
                            schedulesByZBS[zbsId]?.append(pharmacy)
                        }
                        // If pharmacyName is empty, the ZBS has no pharmacy on duty (already initialized as empty array)
                    }
                    
                    // Create ZBS schedule (always create one for each date)
                    let zbsSchedule = ZBSSchedule(date: date, schedulesByZBS: schedulesByZBS)
                    zbsSchedules.append(zbsSchedule)
                    
                    // Debug: print the ZBS schedule we just created
                    if debug && sanitizedDateStr == "11-ago-25" {
                        print("🔍 DEBUG: Created ZBS schedule for \(sanitizedDateStr):")
                        for (zbsId, pharmacies) in schedulesByZBS {
                            let names = pharmacies.map { $0.name }.joined(separator: ", ")
                            print("  \(zbsId): \(names.isEmpty ? "NO PHARMACY" : names)")
                        }
                    }
                    
                    // For backwards compatibility, also create a regular PharmacySchedule
                    // with all pharmacies that have a name
                    var allPharmacies: [Pharmacy] = []
                    for (_, pharmacies) in schedulesByZBS {
                        allPharmacies.append(contentsOf: pharmacies)
                    }
                    
                    if !allPharmacies.isEmpty {
                        let schedule = PharmacySchedule(
                            date: date,
                            shifts: [.fullDay: allPharmacies]
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
        
        // Store ZBS schedules for later access
        Self.cachedZBSSchedules = zbsSchedules
        if debug { print("📦 Stored \(zbsSchedules.count) ZBS schedules in cache") }
        
        // For now, return empty array until we implement the full parsing logic
        return schedules
    }
}
