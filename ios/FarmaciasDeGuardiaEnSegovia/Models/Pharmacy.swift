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
    /// From the server's pharmacy catalog. Absent for pharmacies the catalog doesn't cover
    /// yet, and in schedules cached by older app versions.
    public let latitude: Double?
    public let longitude: Double?

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

    public init(id: String, name: String, address: String, phone: String, additionalInfo: String?, latitude: Double? = nil, longitude: Double? = nil) {
        self.id = id
        self.name = name
        self.address = address
        self.phone = phone
        self.additionalInfo = additionalInfo
        self.latitude = latitude
        self.longitude = longitude
    }
}

// MARK: - Location Extensions
extension Pharmacy {
    /// The pharmacy's position, or nil when the server's catalog has no coordinates for it.
    var location: CLLocation? {
        guard let latitude, let longitude else { return nil }
        return CLLocation(latitude: latitude, longitude: longitude)
    }
}
