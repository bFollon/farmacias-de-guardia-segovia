import Foundation

/// Represents a geographical region or location that has its own pharmacy duty schedule
public struct Region {
    /// Unique identifier for the region, used to match with PDF parsing strategies
    public let id: String
    
    /// Display name of the region (e.g., "Segovia Capital", "Cuéllar")
    public let name: String
    
    /// URL to the PDF file containing the schedule for this region
    public let pdfURL: URL
    
    /// Additional metadata about the region
    public let metadata: RegionMetadata
    
    public init(id: String, name: String, pdfURL: URL, metadata: RegionMetadata = .init()) {
        self.id = id
        self.name = name
        self.pdfURL = pdfURL
        self.metadata = metadata
    }
}

/// Additional configuration and metadata for a region
public struct RegionMetadata {
    /// Whether this region has 24-hour pharmacies that are always open
    public let has24HourPharmacies: Bool
    
    /// Whether this region's schedule changes monthly
    public let isMonthlySchedule: Bool
    
    /// Any special notes about this region's pharmacy schedule
    public let notes: String?
    
    public init(
        has24HourPharmacies: Bool = false,
        isMonthlySchedule: Bool = false,
        notes: String? = nil
    ) {
        self.has24HourPharmacies = has24HourPharmacies
        self.isMonthlySchedule = isMonthlySchedule
        self.notes = notes
    }
}

extension Region {
    /// The default region (Segovia Capital)
    public static let segoviaCapital = Region(
        id: "segovia-capital",
        name: "Segovia Capital",
        pdfURL: Bundle.main.url(forResource: "CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025", withExtension: "pdf")!,
        metadata: RegionMetadata(
            has24HourPharmacies: false,
            isMonthlySchedule: false,
            notes: "Includes both day and night shifts"
        )
    )
    
    /// Example: Cuéllar region (to be implemented)
    public static let cuellar = Region(
        id: "cuellar",
        name: "Cuéllar",
        pdfURL: Bundle.main.url(forResource: "CALENDARIO-GUARDIAS-CUELLAR-2025", withExtension: "pdf") ?? URL(string: "https://example.com/cuellar-schedule.pdf")!,
        metadata: RegionMetadata(
            has24HourPharmacies: false,
            isMonthlySchedule: true,
            notes: "Monthly schedule with single daily shift"
        )
    )
}
