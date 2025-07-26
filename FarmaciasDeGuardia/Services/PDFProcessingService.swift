import Foundation
import PDFKit

public class PDFProcessingService {
    /// Current active region. For now, always Segovia Capital.
    private let region: Region
    private let parser: PDFParsingStrategy
    
    public init(region: Region = .segoviaCapital) {
        self.region = region
        // For now, always use SegoviaCapitalParser. In the future, we'll select based on region.id
        self.parser = SegoviaCapitalParser()
    }
    
    /// Loads pharmacy schedules for the current region
    public func loadPharmacies() -> [PharmacySchedule] {
        guard let pdfDocument = PDFDocument(url: region.pdfURL) else {
            print("Failed to load PDF from \(region.pdfURL)")
            return []
        }
        
        print("Loading schedules for \(region.name)")
        return parser.parseSchedules(from: pdfDocument)
    }
    
    /// Internal method, kept for backward compatibility and testing
    func loadPharmacies(from url: URL) -> [PharmacySchedule] {
        guard let pdfDocument = PDFDocument(url: url) else {
            print("Failed to load PDF from \(url)")
            return []
        }
        
        print("Loading schedules from \(url)")
        return parser.parseSchedules(from: pdfDocument)
    }
}
