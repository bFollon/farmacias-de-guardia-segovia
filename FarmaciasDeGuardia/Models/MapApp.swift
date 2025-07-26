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
