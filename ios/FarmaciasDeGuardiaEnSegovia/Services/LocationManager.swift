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
    @Published var isRequestingLocation = false
    @Published var error: LocationError?
    
    // Track location request state
    private var currentRequestId: UUID?
    private var locationRequestTimeout: Timer?
    
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
        
        // Cancel any existing request
        cancelCurrentRequest()
        
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
    
    private func cancelCurrentRequest() {
        currentRequestId = nil
        isRequestingLocation = false
        locationRequestTimeout?.invalidate()
        locationRequestTimeout = nil
    }
    
    private func requestCurrentLocation() {
        // Generate unique request ID
        currentRequestId = UUID()
        isLoading = true
        isRequestingLocation = true
        
        DebugConfig.debugPrint("📍 Starting location request: \(currentRequestId?.uuidString ?? "unknown")")
        
        manager.requestLocation()
        
        // Set a timeout with proper cleanup
        locationRequestTimeout = Timer.scheduledTimer(withTimeInterval: 10.0, repeats: false) { [weak self] _ in
            guard let self = self else { return }
            
            if self.isRequestingLocation {
                DebugConfig.debugPrint("⏰ Location request timeout: \(self.currentRequestId?.uuidString ?? "unknown")")
                self.cancelCurrentRequest()
                self.isLoading = false
                self.error = .timeout
            }
        }
    }
}

extension LocationManager: CLLocationManagerDelegate {
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard isRequestingLocation else { return }
        
        if let location = locations.first {
            let requestId = currentRequestId?.uuidString ?? "unknown"
            DebugConfig.debugPrint("📍 Location obtained for request \(requestId): \(location.coordinate.latitude), \(location.coordinate.longitude)")
            
            // Clear request state
            cancelCurrentRequest()
            isLoading = false
            userLocation = location
        }
    }
    
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard isRequestingLocation else { return }
        
        let requestId = currentRequestId?.uuidString ?? "unknown"
        DebugConfig.debugPrint("❌ Location error for request \(requestId): \(error.localizedDescription)")
        
        // Clear request state
        cancelCurrentRequest()
        isLoading = false
        self.error = .failed
    }
    
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        
        switch authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            if isRequestingLocation {
                requestCurrentLocation()
            }
        case .denied:
            cancelCurrentRequest()
            isLoading = false
            error = .denied
        case .restricted:
            cancelCurrentRequest()
            isLoading = false
            error = .restricted
        default:
            break
        }
    }
}
