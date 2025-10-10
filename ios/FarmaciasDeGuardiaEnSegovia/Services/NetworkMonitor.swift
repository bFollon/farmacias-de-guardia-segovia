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
import Network
import Combine

/// Network monitoring service to check online/offline state
/// Uses Apple's Network framework for real-time connectivity detection
class NetworkMonitor: ObservableObject {
    static let shared = NetworkMonitor()
    
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "NetworkMonitor")
    
    /// Published property that views can observe for real-time updates
    @Published private(set) var isOnline: Bool = true
    
    /// Published property for network state description
    @Published private(set) var networkStateDescription: String = "Comprobando..."
    
    private init() {
        startMonitoring()
    }
    
    /// Start monitoring network connectivity
    private func startMonitoring() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self = self else { return }
            
            let online = path.status == .satisfied
            let description = self.getNetworkDescription(from: path)
            
            DispatchQueue.main.async {
                self.isOnline = online
                self.networkStateDescription = description
            }
            
            DebugConfig.debugPrint("NetworkMonitor: Online status = \(online) (\(description))")
        }
        
        monitor.start(queue: queue)
        DebugConfig.debugPrint("NetworkMonitor: Initialized and monitoring")
    }
    
    /// Get human-readable description of network state
    private func getNetworkDescription(from path: NWPath) -> String {
        guard path.status == .satisfied else {
            return "Sin conexión"
        }
        
        if path.usesInterfaceType(.wifi) {
            return "WiFi (conectado)"
        } else if path.usesInterfaceType(.cellular) {
            return "Datos móviles (conectado)"
        } else if path.usesInterfaceType(.wiredEthernet) {
            return "Ethernet (conectado)"
        } else {
            return "Conectado"
        }
    }
    
    /// Stop monitoring (for cleanup if needed)
    func stopMonitoring() {
        monitor.cancel()
        DebugConfig.debugPrint("NetworkMonitor: Stopped monitoring")
    }
    
    deinit {
        monitor.cancel()
    }
}

