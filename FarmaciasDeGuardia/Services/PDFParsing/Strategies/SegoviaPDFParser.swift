import Foundation
import PDFKit

/// Parser specific to Segovia Capital's PDF format.
/// Handles the three-column layout (dates, day shift, night shift) used in Segovia's schedules.
class SegoviaPDFParser: RowBasedPDFParser {
    /// Segovia Capital PDF layout constants
    private enum Layout {
        /// The estimated margin from PDF edges
        static let pageMargin: CGFloat = 40
        
        /// Width ratio for the date column (just enough for "miércoles, 19 de febrero")
        static let dateColumnWidthRatio: CGFloat = 0.22
        
        /// Gap between columns to prevent overlap
        static let columnGap: CGFloat = 5
    }
    
    /// A structure to hold the three lines of pharmacy information
    struct PharmacyLines {
        let name: String
        let address: String
        let extraInfo: String
    }
    
    /// Extracts text from the three columns in the PDF: dates, day shift, and night shift
    func extractColumnText(from page: PDFPage) -> ([String], [String], [String]) {
        let pageBounds = page.bounds(for: .mediaBox)
        let baseHeight = getSmallestFontSize(from: page)
        
        // Define column layout (same as before)
        let contentWidth = pageBounds.width - (2 * Layout.pageMargin)
        let dateColumnWidth = contentWidth * Layout.dateColumnWidthRatio
        let pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2
        
        // Define cell scan areas for the three columns
        let cellScanAreas = [
            // Date column: single line per cell
            CellScanArea(
                x: Layout.pageMargin,
                width: dateColumnWidth,
                increment: baseHeight * 3, // Taller to capture whole date
                rows: 1
            ),
            // Day pharmacy column: three lines per cell  
            CellScanArea(
                x: Layout.pageMargin + dateColumnWidth + Layout.columnGap,
                width: pharmacyColumnWidth,
                increment: baseHeight,
                rows: 3
            ),
            // Night pharmacy column: three lines per cell
            CellScanArea(
                x: Layout.pageMargin + dateColumnWidth + Layout.columnGap + pharmacyColumnWidth + Layout.columnGap,
                width: pharmacyColumnWidth,
                increment: baseHeight,
                rows: 3
            )
        ]
        
        // Find the first coherent row
        guard let firstRowY = findFirstCoherentRow(
            page,
            cellScanAreas: cellScanAreas,
            startY: 0,
            endY: pageBounds.height * 0.5, // Search in upper half of page
            searchIncrement: baseHeight / 2,
            validator: { rowData in
                return self.isCoherentSegoviaRow(rowData)
            }
        ) else {
            print("Could not find first coherent row")
            return ([], [], [])
        }
        
        // Now scan all rows starting from the first coherent one
        var allDates: [String] = []
        var allDayPharmacies: [String] = []
        var allNightPharmacies: [String] = []
        
        let rowHeight = baseHeight * 3 // Approximate height of each row
        var currentY = firstRowY
        
        // Continue scanning until we reach the end of the page
        while currentY < pageBounds.height {
            let rowData = scanRow(page, cellScanAreas: cellScanAreas, rowY: currentY)
            
            // If we have coherent data, process it and move to next row
            if isCoherentSegoviaRow(rowData) {
                if let processedRow = processSegoviaRow(rowData) {
                    allDates.append(processedRow.date)
                    allDayPharmacies.append(processedRow.dayPharmacy)
                    allNightPharmacies.append(processedRow.nightPharmacy)
                }
                currentY += rowHeight
            } else {
                // No coherent data, move by small increment and try again
                currentY += baseHeight / 2
            }
        }
        
        return (allDates, allDayPharmacies, allNightPharmacies)
    }
    
    /// Validates if a scanned row contains coherent Segovia Capital data
    private func isCoherentSegoviaRow(_ rowData: [[String]]) -> Bool {
        // We expect 3 cells: date, day pharmacy, night pharmacy
        guard rowData.count == 3 else { return false }
        
        let dateCell = rowData[0]
        let dayCell = rowData[1] 
        let nightCell = rowData[2]
        
        // Date cell should have exactly one line with a valid Spanish date format
        guard dateCell.count == 1,
              let dateText = dateCell.first,
              DutyDate.parse(dateText) != nil else {
            return false
        }
        
        // Pharmacy cells should have exactly 3 lines each
        guard dayCell.count == 3, nightCell.count == 3 else {
            return false
        }
        
        // First line of pharmacy cells should contain "FARMACIA"
        guard dayCell[2].contains("FARMACIA"), nightCell[2].contains("FARMACIA") else {
            return false
        }
        
        return true
    }
    
    /// Processes a coherent Segovia row into the expected format
    private func processSegoviaRow(_ rowData: [[String]]) -> (date: String, dayPharmacy: String, nightPharmacy: String)? {
        guard rowData.count == 3 else { return nil }
        
        let dateText = rowData[0].first ?? ""
        let dayPharmacy = PharmacyLines(
            name: rowData[1][0],
            address: rowData[1][1], 
            extraInfo: rowData[1][2]
        )
        let nightPharmacy = PharmacyLines(
            name: rowData[2][0],
            address: rowData[2][1],
            extraInfo: rowData[2][2] 
        )
        
        // Format the pharmacy data as multi-line strings
        let dayPharmacyString = "\(dayPharmacy.name)\n\(dayPharmacy.address)\n\(dayPharmacy.extraInfo)"
        let nightPharmacyString = "\(nightPharmacy.name)\n\(nightPharmacy.address)\n\(nightPharmacy.extraInfo)"
        
        return (dateText, dayPharmacyString, nightPharmacyString)
    }
}

extension SegoviaPDFParser {
    /// Extracts text from the three columns and returns flattened arrays for compatibility
    func extractColumnTextFlattened(from page: PDFPage) -> ([String], [String], [String]) {
        let pageBounds = page.bounds(for: .mediaBox)
        let baseHeight = getSmallestFontSize(from: page)
        
        // Define column layout (same as before)
        let contentWidth = pageBounds.width - (2 * Layout.pageMargin)
        let dateColumnWidth = contentWidth * Layout.dateColumnWidthRatio
        let pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2
        
        // Define cell scan areas for the three columns
        let cellScanAreas = [
            // Date column: single line per cell
            CellScanArea(
                x: Layout.pageMargin,
                width: dateColumnWidth,
                increment: baseHeight * 3, // Taller to capture whole date
                rows: 1
            ),
            // Day pharmacy column: three lines per cell
            CellScanArea(
                x: Layout.pageMargin + dateColumnWidth + Layout.columnGap,
                width: pharmacyColumnWidth,
                increment: baseHeight,
                rows: 3
            ),
            // Night pharmacy column: three lines per cell
            CellScanArea(
                x: Layout.pageMargin + dateColumnWidth + Layout.columnGap + pharmacyColumnWidth + Layout.columnGap,
                width: pharmacyColumnWidth,
                increment: baseHeight,
                rows: 3
            )
        ]
        
        // Find the first coherent row
        guard let firstRowY = findFirstCoherentRow(
            page,
            cellScanAreas: cellScanAreas,
            startY: 0,
            endY: pageBounds.height * 0.5, // Search in upper half of page
            searchIncrement: baseHeight / 2,
            validator: { rowData in
                return self.isCoherentSegoviaRow(rowData)
            }
        ) else {
            print("Could not find first coherent row")
            return ([], [], [])
        }
        
        // Now scan all rows starting from the first coherent one
        var allDates: [String] = []
        var allDayPharmacyLines: [String] = []
        var allNightPharmacyLines: [String] = []
        
        let rowHeight = baseHeight * 3 // Approximate height of each row
        var currentY = firstRowY
        
        // Continue scanning until we reach the end of the page
        while currentY < pageBounds.height {
            let rowData = scanRow(page, cellScanAreas: cellScanAreas, rowY: currentY)
            
            // If we have coherent data, process it and move to next row
            if isCoherentSegoviaRow(rowData) {
                if let processedRow = processSegoviaRowFlattened(rowData) {
                    allDates.append(processedRow.date)
                    allDayPharmacyLines.append(contentsOf: processedRow.dayPharmacyLines)
                    allNightPharmacyLines.append(contentsOf: processedRow.nightPharmacyLines)
                }
                currentY += rowHeight
            } else {
                // No coherent data, move by small increment and try again
                currentY += baseHeight / 2
            }
        }
        
        return (allDates, allDayPharmacyLines, allNightPharmacyLines)
    }
    
    /// Processes a coherent Segovia row into flattened arrays for parseBatch compatibility
    private func processSegoviaRowFlattened(_ rowData: [[String]]) -> (date: String, dayPharmacyLines: [String], nightPharmacyLines: [String])? {
        guard rowData.count == 3 else { return nil }
        
        let dateText = rowData[0].first ?? ""
        let dayPharmacyLines = rowData[1] // [name, address, extraInfo]
        let nightPharmacyLines = rowData[2] // [name, address, extraInfo]
        
        return (dateText, dayPharmacyLines, nightPharmacyLines)
    }
}
