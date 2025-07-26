import Foundation
import PDFKit

public protocol PDFParsingStrategy {
    func parseSchedules(from pdf: PDFDocument) -> [PharmacySchedule]
}

/// The default parser for Segovia Capital pharmacy schedules.
/// Currently wraps the existing PDFProcessingService parsing logic to maintain compatibility.
public class SegoviaCapitalParser: PDFParsingStrategy {
    public init() {}
    
    public func parseSchedules(from pdf: PDFDocument) -> [PharmacySchedule] {
        // For now, just delegate to the existing service to maintain current functionality
        let service = PDFProcessingService()
        var allSchedules: [PharmacySchedule] = []
        
        // Replicate the existing page-by-page parsing logic
        for pageIndex in 0..<pdf.pageCount {
            if let page = pdf.page(at: pageIndex) {
                let pageSchedules = service.extractTextWithLayout(from: page)
                allSchedules.append(contentsOf: pageSchedules)
            }
        }
        
        return allSchedules
    }
}
