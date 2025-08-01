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
            // - Very narrow date column just for dd-mm-yy format
            // - Wide pharmacy data column after the sideways text
            let dateColumn = TextColumn(x: 40, width: 45)     // Just wide enough for "dd-mmm-yy"
            let dataColumn = TextColumn(x: 300, width: pageWidth - 350)  // Pharmacy data after sideways text
            
            if debug {
                print("📐 Page dimensions: \(pageWidth) x \(pageHeight)")
                print("📏 Using base height: \(baseHeight)")
            }
            
            // Use same scanning height for both columns since text height is consistent
            let scanHeight = baseHeight * 0.6      // Smaller height to separate adjacent dates
            let scanIncrement = baseHeight * 0.5   // Smaller increment to catch each individual date
            
            if debug { 
                print("\n📍 Scanning date column from x: \(dateColumn.x), width: \(dateColumn.width)")
                print("📍 Scanning data column from x: \(dataColumn.x), width: \(dataColumn.width)")
            }
            
            // Scan both columns with the same height parameters
            let dates = scanColumn(page, column: dateColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            let pharmacyData = scanColumn(page, column: dataColumn, baseHeight: scanHeight, scanIncrement: scanIncrement)
            
            // Print found text for debugging
            if debug {
                print("\n📅 Dates found:")
                for (y, text) in dates {
                    print("📝 At y=\(y): \(text)")
                }
                
                print("\n🏥 Pharmacy data found:")
                for (y, text) in pharmacyData {
                    print("📝 At y=\(y): \(text)")
                }
            }
        }
        
        // For now, return empty array until we implement the full parsing logic
        return schedules
    }
}
