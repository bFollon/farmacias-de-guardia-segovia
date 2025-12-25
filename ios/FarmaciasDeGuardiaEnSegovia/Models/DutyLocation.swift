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

/// Represents a duty location, which can be either a Region or a ZBS (healthcare zone).
/// This unified concept allows treating all duty pharmacy locations consistently,
/// whether they're main regions (Segovia Capital, Cuéllar, etc.) or ZBS subdivisions.
public struct DutyLocation: Codable, Hashable, Identifiable {
    /// Unique identifier for the location (e.g., "segovia-capital", "cantalejo")
    public let id: String

    /// Display name of the location
    public let name: String

    /// Emoji icon for the location
    public let icon: String

    /// Optional notes (e.g., special instructions for Cantalejo)
    public let notes: String?

    /// The ID of the parent region this location belongs to
    private let associatedRegionId: String

    /// The parent region this location belongs to
    public var associatedRegion: Region {
        switch associatedRegionId {
        case "segovia-capital":
            return .segoviaCapital
        case "cuellar":
            return .cuellar
        case "el-espinar":
            return .elEspinar
        case "segovia-rural":
            return .segoviaRural
        default:
            return .segoviaCapital // Default fallback
        }
    }

    /// Create a DutyLocation from a Region
    public static func fromRegion(_ region: Region) -> DutyLocation {
        return DutyLocation(
            id: region.id,
            name: region.name,
            icon: region.icon,
            notes: nil,
            associatedRegionId: region.id
        )
    }

    /// Create a DutyLocation from a ZBS (healthcare zone)
    public static func fromZBS(_ zbs: ZBS, region: Region = .segoviaRural) -> DutyLocation {
        return DutyLocation(
            id: zbs.id,
            name: zbs.name,
            icon: zbs.icon,
            notes: zbs.notes,
            associatedRegionId: region.id
        )
    }
}
