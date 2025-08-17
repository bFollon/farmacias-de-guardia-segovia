import Foundation
import PDFKit

/// Protocol defining the contract for PDF schedule parsing strategies.
/// Each region/location should implement its own strategy to handle its specific PDF format.
public protocol PDFParsingStrategy {
    /// Parse a PDF document and extract pharmacy schedules
    /// - Parameter pdf: The PDF document to parse
    /// - Returns: An array of pharmacy schedules extracted from the PDF
    func parseSchedules(from pdf: PDFDocument) -> [PharmacySchedule]
}
