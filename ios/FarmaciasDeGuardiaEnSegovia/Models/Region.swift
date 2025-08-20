/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import Foundation

/// Represents a geographical region or location that has its own pharmacy duty schedule
public struct Region: Equatable {
    /// Unique identifier for the region, used to match with PDF parsing strategies
    public let id: String
    
    /// Display name of the region (e.g., "Segovia Capital", "Cuéllar")
    public let name: String
    
    /// Emoji icon representing the region
    public let icon: String
    
    /// URL to the PDF file containing the schedule for this region
    public let pdfURL: URL
    
    /// Additional metadata about the region
    public let metadata: RegionMetadata
    
    public init(id: String, name: String, icon: String, pdfURL: URL, metadata: RegionMetadata = .init()) {
        self.id = id
        self.name = name
        self.icon = icon
        self.pdfURL = pdfURL
        self.metadata = metadata
    }
    
    public static func == (lhs: Region, rhs: Region) -> Bool {
        return lhs.id == rhs.id
    }
}

/// Additional configuration and metadata for a region
public struct RegionMetadata: Equatable {
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
        icon: "🏙",
        pdfURL: URL(string: "https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf")!,
        metadata: RegionMetadata(
            has24HourPharmacies: false,
            isMonthlySchedule: false,
            notes: "Includes both day and night shifts"
        )
    )
    
    /// Cuéllar region
    public static let cuellar = Region(
        id: "cuellar",
        name: "Cuéllar",
        icon: "🌳",
        pdfURL: URL(string: "https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf")!,
        metadata: RegionMetadata(
            isMonthlySchedule: false,  // Cuéllar uses weekly schedules
            notes: "Servicios semanales excepto primera semana de septiembre"
        )
    )
    
    /// El Espinar region
    public static let elEspinar = Region(
        id: "el-espinar",
        name: "El Espinar / San Rafael",
        icon: "⛰",
        pdfURL: URL(string: "https://cofsegovia.com/wp-content/uploads/2025/01/Guardias-EL-ESPINAR_2025.pdf")!,
        metadata: RegionMetadata(
            isMonthlySchedule: false,  // Uses weekly schedules like Cuéllar
            notes: "Servicios semanales"
        )
    )
    
    /// Segovia Rural region
    public static let segoviaRural = Region(
        id: "segovia-rural",
        name: "Segovia Rural",
        icon: "🚜",
        pdfURL: URL(string: "https://cofsegovia.com/wp-content/uploads/2025/06/SERVICIOS-DE-URGENCIA-RURALES-2025.pdf")!,
        metadata: RegionMetadata(
            isMonthlySchedule: false,
            notes: "Servicios de urgencia rurales"
        )
    )
}
