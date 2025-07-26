import Foundation
import PDFKit

public class PDFProcessingService {
    /// Current active region. For now, always Segovia Capital.
    private let region: Region
    
    /// Registry of parsing strategies for each region
    private var parsingStrategies: [String: PDFParsingStrategy] = [:]
    
    public init(region: Region = .segoviaCapital) {
        self.region = region
        registerDefaultParsers()
    }
    
    /// Registers the default parsing strategies
    private func registerDefaultParsers() {
        // Register Segovia Capital parser
        register(parser: SegoviaCapitalParser(), for: "segovia-capital")
    }
    
    /// Registers a parsing strategy for a specific region
    /// - Parameters:
    ///   - parser: The parser to register
    ///   - regionId: The region identifier this parser handles
    public func register(parser: PDFParsingStrategy, for regionId: String) {
        parsingStrategies[regionId] = parser
    }
    
    /// Loads pharmacy schedules for the current region
    public func loadPharmacies() -> [PharmacySchedule] {
        guard let pdfDocument = PDFDocument(url: region.pdfURL) else {
            print("Failed to load PDF from \(region.pdfURL)")
            return []
        }
        
        guard let parser = parsingStrategies[region.id] else {
            print("No parser found for region: \(region.name) (id: \(region.id))")
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
        
        // For direct URL loading, use Segovia parser as default
        let parser = parsingStrategies["segovia-capital"] ?? SegoviaCapitalParser()
        print("Loading schedules from \(url)")
        return parser.parseSchedules(from: pdfDocument)
    }
}
