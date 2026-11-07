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
import UIKit

enum MapApp: String, CaseIterable {
    case apple = "Apple Maps"
    case google = "Google Maps"
    case waze = "Waze"
    
    var urlScheme: String {
        switch self {
        case .apple: return "maps://"
        case .google: return "comgooglemaps://"
        case .waze: return "waze://"
        }
    }
    
    var icon: String {
        switch self {
        case .apple: return "map"
        case .google: return "map.fill"
        case .waze: return "arrow.triangle.turn.up.right.diamond.fill"
        }
    }
    
    /// Deep link that opens this app on the pharmacy. Uses the catalog coordinates when the
    /// pharmacy has them, so the pin lands exactly where it was placed; otherwise falls back
    /// to a text search for name + address and lets the maps app resolve it.
    func url(for pharmacy: Pharmacy) -> URL? {
        let allowed = CharacterSet.urlQueryAllowed

        if let latitude = pharmacy.latitude, let longitude = pharmacy.longitude {
            let label = pharmacy.name.trimmingCharacters(in: .whitespaces)
                .addingPercentEncoding(withAllowedCharacters: allowed) ?? ""
            switch self {
            case .apple: return URL(string: "maps://?ll=\(latitude),\(longitude)&q=\(label)")
            case .google: return URL(string: "comgooglemaps://?q=\(latitude),\(longitude)")
            case .waze: return URL(string: "waze://?ll=\(latitude),\(longitude)&navigate=yes")
            }
        }

        let query = "\(pharmacy.name), \(pharmacy.address), Segovia, Spain"
            .addingPercentEncoding(withAllowedCharacters: allowed) ?? ""
        switch self {
        case .apple: return URL(string: "maps://?q=\(query)")
        case .google: return URL(string: "comgooglemaps://?q=\(query)")
        case .waze: return URL(string: "waze://?q=\(query)")
        }
    }

    static func availableApps() -> [MapApp] {
        return MapApp.allCases.filter { app in
            guard let url = URL(string: app.urlScheme) else { return false }
            return UIApplication.shared.canOpenURL(url)
        }
    }
}
