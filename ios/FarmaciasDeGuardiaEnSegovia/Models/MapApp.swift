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
    
    static func availableApps() -> [MapApp] {
        return MapApp.allCases.filter { app in
            guard let url = URL(string: app.urlScheme) else { return false }
            return UIApplication.shared.canOpenURL(url)
        }
    }
}
