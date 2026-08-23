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
import CoreLocation

public struct Pharmacy: Identifiable, Codable {
    public let id: String
    public let name: String
    public let address: String
    public let phone: String
    public let additionalInfo: String?

    public var formattedPhone: String {
        let cleanNumber = phone.replacingOccurrences(of: " ", with: "")
        var result = ""
        var count = 0

        for char in cleanNumber {
            if count > 0 && count % 3 == 0 {
                result += " "
            }
            result += String(char)
            count += 1
        }

        return result.trimmingCharacters(in: .whitespaces)
    }

    public init(id: String, name: String, address: String, phone: String, additionalInfo: String?) {
        self.id = id
        self.name = name
        self.address = address
        self.phone = phone
        self.additionalInfo = additionalInfo
    }
}

// MARK: - Location Extensions
extension Pharmacy {
    /// Calculate distance from user location to this pharmacy
    func distance(from userLocation: CLLocation) async -> CLLocationDistance? {
        guard let pharmacyLocation = await GeocodingService.getCoordinatesForPharmacy(self) else {
            DebugConfig.debugPrint("❌ Could not geocode pharmacy address: \(self.address)")
            return nil
        }
        
        let distance = userLocation.distance(from: pharmacyLocation)
        DebugConfig.debugPrint("📏 Distance to \(self.name): \(Int(distance))m")
        return distance
    }
    
    /// Get coordinates for this pharmacy
    func coordinates() async -> CLLocation? {
        return await GeocodingService.getCoordinatesForPharmacy(self)
    }
}
