import Foundation
import PDFKit

public class PDFProcessingService {
    /// Current active region. For now, always Segovia Capital.
    private var region: Region
    
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
        // Register Cuellar parser
        register(parser: CuellarParser(), for: "cuellar")
        // Register El Espinar parser
        register(parser: ElEspinarParser(), for: "el-espinar")
        // Register Segovia Rural parser
        register(parser: SegoviaRuralParser(), for: "segovia-rural")
    }
    
    /// Registers a parsing strategy for a specific region
    /// - Parameters:
    ///   - parser: The parser to register
    ///   - regionId: The region identifier this parser handles
    public func register(parser: PDFParsingStrategy, for regionId: String) {
        parsingStrategies[regionId] = parser
    }
    
    /// Loads pharmacy schedules for the current region
    public func loadPharmacies(forceRefresh: Bool = false) async -> [PharmacySchedule] {
        // Get the effective PDF URL (cached or remote, force download if requested)
        let effectiveURL: URL
        if forceRefresh {
            do {
                effectiveURL = try await PDFCacheManager.shared.forceDownload(for: region)
                DebugConfig.debugPrint("✅ Force downloaded fresh PDF for \(region.name)")
            } catch {
                DebugConfig.debugPrint("❌ Failed to force download PDF for \(region.name): \(error)")
                // Fallback to regular effective URL
                effectiveURL = await region.getEffectivePDFURL()
            }
        } else {
            effectiveURL = await region.getEffectivePDFURL()
        }
        
        guard let pdfDocument = PDFDocument(url: effectiveURL) else {
            DebugConfig.debugPrint("Failed to load PDF from \(effectiveURL)")
            return []
        }

        guard let parser = parsingStrategies[region.id] else {
            DebugConfig.debugPrint("No parser found for region: \(region.name) (id: \(region.id))")
            return []
        }

        DebugConfig.debugPrint("Loading schedules for \(region.name) from \(effectiveURL)")
        return parser.parseSchedules(from: pdfDocument)
    }

    /// Updates the current region and returns schedules for that region
    /// - Parameter newRegion: The new region to update to
    /// - Parameter forceRefresh: Whether to force re-download the PDF
    /// - Returns: An array of `PharmacySchedule` for the new region
    public func loadPharmacies(for newRegion: Region, forceRefresh: Bool = false) async -> [PharmacySchedule] {
        self.region = newRegion
        return await loadPharmacies(forceRefresh: forceRefresh)
    }    /// Internal method, kept for backward compatibility and testing
    func loadPharmacies(from url: URL) -> [PharmacySchedule] {
        guard let pdfDocument = PDFDocument(url: url) else {
            DebugConfig.debugPrint("Failed to load PDF from \(url)")
            return []
        }
        
        // For direct URL loading, use Segovia parser as default
        let parser = parsingStrategies["segovia-capital"] ?? SegoviaCapitalParser()
        DebugConfig.debugPrint("Loading schedules from \(url)")
        return parser.parseSchedules(from: pdfDocument)
    }
}
