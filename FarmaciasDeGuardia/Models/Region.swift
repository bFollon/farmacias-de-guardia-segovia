import Foundation

/// Represents a geographical region or location that has its own pharmacy duty schedule
public struct Region {
    /// Unique identifier for the region, used to match with PDF parsing strategies
    public let id: String
    
    /// Display name of the region (e.g., "Segovia Capital", "Cuéllar")
    public let name: String
    
    /// URL to the PDF file containing the schedule for this region
    public let pdfURL: URL
    
    public init(id: String, name: String, pdfURL: URL) {
        self.id = id
        self.name = name
        self.pdfURL = pdfURL
    }
}

extension Region {
    /// The default region (Segovia Capital)
    public static let segoviaCapital = Region(
        id: "segovia-capital",
        name: "Segovia Capital",
        pdfURL: Bundle.main.url(forResource: "CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025", withExtension: "pdf")!
    )
}
