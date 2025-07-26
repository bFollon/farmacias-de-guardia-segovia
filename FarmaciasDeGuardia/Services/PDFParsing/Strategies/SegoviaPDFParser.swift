import Foundation
import PDFKit

/// Parser specific to Segovia Capital's PDF format.
/// Handles the three-column layout (dates, day shift, night shift) used in Segovia's schedules.
class SegoviaPDFParser: ColumnBasedPDFParser {
    /// Segovia Capital PDF layout constants
    private enum Layout {
        /// The estimated margin from PDF edges
        static let pageMargin: CGFloat = 40
        
        /// Width ratio for the date column (just enough for "miércoles, 19 de febrero")
        static let dateColumnWidthRatio: CGFloat = 0.22
        
        /// Gap between columns to prevent overlap
        static let columnGap: CGFloat = 5
    }
    
    /// Extracts text from the three columns in the PDF: dates, day shift, and night shift
    func extractColumnText(from page: PDFPage) -> ([String], [String], [String]) {
        let pageBounds = page.bounds(for: .mediaBox)
        
        // Use smallest font size as our base scanning height
        let baseHeight = getSmallestFontSize(from: page)
        let scanIncrement = baseHeight / 2 // Use slightly smaller increment to ensure we don't miss anything
        
        // Define column layout
        let contentWidth = pageBounds.width - (2 * Layout.pageMargin)
        let dateColumnWidth = contentWidth * Layout.dateColumnWidthRatio
        let pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2
        
        // Define columns
        let dateColumn = TextColumn(
            x: Layout.pageMargin,
            width: dateColumnWidth
        )
        
        let dayColumn = TextColumn(
            x: dateColumn.x + dateColumn.width + Layout.columnGap,
            width: pharmacyColumnWidth
        )
        
        let nightColumn = TextColumn(
            x: dayColumn.x + dayColumn.width + Layout.columnGap,
            width: pharmacyColumnWidth
        )
        
        // Scan columns
        let dateTexts = scanColumn(page, column: dateColumn, baseHeight: baseHeight, scanIncrement: scanIncrement)
        let dayTexts = scanColumn(page, column: dayColumn, baseHeight: baseHeight, scanIncrement: scanIncrement)
        let nightTexts = scanColumn(page, column: nightColumn, baseHeight: baseHeight, scanIncrement: scanIncrement)
        
        // Process date column for years
        let currentYear = DutyDate.getCurrentYear()
        let nextYear = currentYear + 1
        var pendingYear: String? = nil
        
        let dates = dateTexts.compactMap { text -> String? in
            if text.text == String(currentYear) || text.text == String(nextYear) {
                pendingYear = text.text
                return nil
            }
            
            if text.text.contains(",") {
                if let year = pendingYear {
                    pendingYear = nil
                    return "\(text.text) \(year)"
                }
                return text.text
            }
            
            return text.text
        }.filter { $0 != String(currentYear) && $0 != String(nextYear) }
        
        let dayShifts = dayTexts.map { $0.text }
        let nightShifts = nightTexts.map { $0.text }
        
        return (dates, dayShifts, nightShifts)
    }
}
