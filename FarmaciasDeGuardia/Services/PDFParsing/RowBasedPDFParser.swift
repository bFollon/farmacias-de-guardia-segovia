import Foundation
import PDFKit

/// Represents how a cell should be scanned in a row-based PDF parser
struct CellScanArea {
    let x: CGFloat
    let width: CGFloat
    let increment: CGFloat
    let rows: Int
}

/// Base class for parsing PDFs that use a row-based layout.
/// This allows reading complete rows at once, eliminating index-based matching issues.
public class RowBasedPDFParser {
    
    /// Scans a row across multiple cells with different scanning configurations
    /// - Parameters:
    ///   - page: The PDF page to scan
    ///   - cellScanAreas: Array of CellScanArea defining how each cell should be scanned
    ///   - rowY: The Y position to start scanning this row
    /// - Returns: Array of arrays of strings, where each inner array represents the lines found in each cell
    func scanRow(_ page: PDFPage, cellScanAreas: [CellScanArea], rowY: CGFloat) -> [[String]] {
        var cellResults: [[String]] = []
        
        for cellArea in cellScanAreas {
            var cellLines: [String] = []
            
            // Scan this cell with the specified number of rows
            for rowIndex in 0..<cellArea.rows {
                let scanY = rowY + (CGFloat(rowIndex) * cellArea.increment)
                
                let rect = CGRect(
                    x: cellArea.x,
                    y: scanY,
                    width: cellArea.width,
                    height: cellArea.increment
                )
                
                if let selection = page.selection(for: rect),
                   let text = selection.string?.trimmingCharacters(in: .whitespacesAndNewlines),
                   !text.isEmpty {
                    cellLines.append(text)
                }
            }
            
            cellResults.append(cellLines)
        }
        
        return cellResults
    }
    
    /// Detects row boundaries by scanning for content changes
    /// - Parameters:
    ///   - page: The PDF page to scan
    ///   - startY: Y position to start scanning from
    ///   - endY: Y position to stop scanning at
    ///   - scanHeight: Height of each scan area
    ///   - increment: How much to move between scans
    /// - Returns: Array of Y positions where rows start
    func detectRowBoundaries(_ page: PDFPage, startY: CGFloat, endY: CGFloat, scanHeight: CGFloat, increment: CGFloat) -> [CGFloat] {
        var rowStarts: [CGFloat] = []
        var lastHadContent = false
        
        for y in stride(from: startY, to: endY, by: increment) {
            let pageBounds = page.bounds(for: .mediaBox)
            let rect = CGRect(x: 0, y: y, width: pageBounds.width, height: scanHeight)
            
            let hasContent = page.selection(for: rect)?.string?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false
            
            // Detect transition from no content to content (start of new row)
            if hasContent && !lastHadContent {
                rowStarts.append(y)
            }
            
            lastHadContent = hasContent
        }
        
        return rowStarts
    }
    
    /// Gets the smallest font size in the page for determining appropriate scan heights
    func getSmallestFontSize(from page: PDFPage) -> CGFloat {
        guard page.string != nil else { return 12.0 }
        
        // This is a simplified approach - in practice you might want to
        // analyze the PDF's font information more thoroughly
        let pageBounds = page.bounds(for: .mediaBox)
        
        // Test with different heights to find the smallest that captures text
        for height in stride(from: 8.0, to: 20.0, by: 1.0) {
            let rect = CGRect(x: 0, y: 0, width: pageBounds.width, height: height)
            if let selection = page.selection(for: rect),
               let text = selection.string?.trimmingCharacters(in: .whitespacesAndNewlines),
               !text.isEmpty {
                return height
            }
        }
        
        return 12.0 // Default fallback
    }
    
    /// Finds the first row that contains coherent data by scanning overlapping zones
    /// - Parameters:
    ///   - page: The PDF page to scan
    ///   - cellScanAreas: Array of CellScanArea defining how each cell should be scanned
    ///   - startY: Y position to start searching from
    ///   - endY: Y position to stop searching at
    ///   - searchIncrement: How much to move between search attempts (should be smaller than row height)
    ///   - validator: Function that validates if the scanned row data is coherent
    /// - Returns: The Y position of the first coherent row, or nil if not found
    func findFirstCoherentRow(
        _ page: PDFPage, 
        cellScanAreas: [CellScanArea], 
        startY: CGFloat, 
        endY: CGFloat, 
        searchIncrement: CGFloat,
        validator: ([[String]]) -> Bool
    ) -> CGFloat? {
        
        for y in stride(from: startY, to: endY, by: searchIncrement) {
            let rowData = scanRow(page, cellScanAreas: cellScanAreas, rowY: y)
            
            if validator(rowData) {
                return y
            }
        }
        
        return nil
    }
}
