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
import Combine

class LocationManager: NSObject, ObservableObject {
    private let manager = CLLocationManager()
    
    @Published var userLocation: CLLocation?
    @Published var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published var isLoading = false
    @Published var error: LocationError?
    
    enum LocationError: LocalizedError {
        case denied
        case restricted
        case failed
        case timeout
        
        var errorDescription: String? {
            switch self {
            case .denied:
                return "Acceso a la ubicación denegado"
            case .restricted:
                return "Acceso a la ubicación restringido"
            case .failed:
                return "No se pudo obtener la ubicación"
            case .timeout:
                return "Tiempo de espera agotado"
            }
        }
    }
    
    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        authorizationStatus = manager.authorizationStatus
    }
    
    func requestLocationOnce() {
        error = nil
        
        switch authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            requestCurrentLocation()
        case .denied:
            error = .denied
        case .restricted:
            error = .restricted
        @unknown default:
            error = .failed
        }
    }
    
    private func requestCurrentLocation() {
        isLoading = true
        manager.requestLocation()
        
        // Set a timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + 10) { [weak self] in
            if self?.isLoading == true {
                self?.isLoading = false
                self?.error = .timeout
            }
        }
    }
}

extension LocationManager: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        isLoading = false
        if let location = locations.first {
            userLocation = location
            DebugConfig.debugPrint("📍 Location obtained: \(location.coordinate.latitude), \(location.coordinate.longitude)")
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        isLoading = false
        self.error = .failed
        DebugConfig.debugPrint("❌ Location error: \(error.localizedDescription)")
    }
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        
        switch authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            if isLoading {
                requestCurrentLocation()
            }
        case .denied:
            isLoading = false
            error = .denied
        case .restricted:
            isLoading = false
            error = .restricted
        default:
            break
        }
    }
}
