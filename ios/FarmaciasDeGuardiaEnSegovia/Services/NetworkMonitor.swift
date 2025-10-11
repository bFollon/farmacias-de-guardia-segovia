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

/// Network monitoring utility to check online/offline state
/// Uses Apple's Network framework (NWPathMonitor) for real-time connectivity detection
/// Observable for SwiftUI integration
class NetworkMonitor: ObservableObject {

    /// Shared singleton instance
    static let shared = NetworkMonitor()

    /// Published property for SwiftUI observation - true if online, false if offline
    @Published private(set) var isOnline: Bool = true

    private let monitor: NWPathMonitor
    private let queue = DispatchQueue(label: "NetworkMonitor")

    private init() {
        monitor = NWPathMonitor()

        monitor.pathUpdateHandler = { [weak self] path in
            let wasOnline = self?.isOnline ?? true
            let nowOnline = path.status == .satisfied

            DispatchQueue.main.async {
                self?.isOnline = nowOnline

                // Log status changes
                if wasOnline != nowOnline {
                    if nowOnline {
                        DebugConfig.debugPrint("📡 NetworkMonitor: Connection restored")
                    } else {
                        DebugConfig.debugPrint("📡 NetworkMonitor: Connection lost")
                    }
                }

                // Detailed logging
                self?.logNetworkState(path)
            }
        }

        monitor.start(queue: queue)
        DebugConfig.debugPrint("📡 NetworkMonitor: Initialized and monitoring")
    }

    deinit {
        monitor.cancel()
    }

    /// Get a human-readable description of the current network state
    /// Useful for debugging and UI display
    func getNetworkStateDescription() -> String {
        let path = monitor.currentPath

        guard path.status == .satisfied else {
            return "Sin conexión"
        }

        let transportType: String
        if path.usesInterfaceType(.wifi) {
            transportType = "WiFi"
        } else if path.usesInterfaceType(.cellular) {
            transportType = "Datos móviles"
        } else if path.usesInterfaceType(.wiredEthernet) {
            transportType = "Ethernet"
        } else {
            transportType = "Desconocido"
        }

        let validated = path.status == .satisfied ? "conectado" : "sin internet"

        return "\(transportType) (\(validated))"
    }

    /// Log detailed network state for debugging
    private func logNetworkState(_ path: NWPath) {
        let status = path.status == .satisfied ? "Online" : "Offline"
        let interface = getInterfaceType(path)
        let expensive = path.isExpensive ? "expensive" : "not expensive"
        let constrained = path.isConstrained ? "constrained" : "not constrained"

        DebugConfig.debugPrint("📡 NetworkMonitor: Status=\(status), Interface=\(interface), \(expensive), \(constrained)")
    }

    /// Get the current interface type
    private func getInterfaceType(_ path: NWPath) -> String {
        if path.usesInterfaceType(.wifi) {
            return "WiFi"
        } else if path.usesInterfaceType(.cellular) {
            return "Cellular"
        } else if path.usesInterfaceType(.wiredEthernet) {
            return "Ethernet"
        } else if path.usesInterfaceType(.loopback) {
            return "Loopback"
        } else {
            return "Unknown"
        }
    }
}
