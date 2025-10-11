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

import SwiftUI

struct NoScheduleView: View {
    var isOffline: Bool = false

    // Observe network status
    @ObservedObject private var networkMonitor = NetworkMonitor.shared

    var body: some View {
        VStack(spacing: 20) {
            if !networkMonitor.isOnline || isOffline {
                // Offline + no cache scenario
                Image(systemName: "icloud.slash.fill")
                    .font(.system(size: 64))
                    .foregroundColor(Color.orange)

                Text("Sin conexión y sin datos almacenados")
                    .font(.title3)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text("No hay conexión a Internet y no hay datos descargados para esta región.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)

                Text("Conecte a Internet e intente refrescar para descargar los horarios.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            } else {
                // Normal empty state (no schedules in cache)
                Image(systemName: "calendar.badge.exclamationmark")
                    .font(.system(size: 64))
                    .foregroundColor(.secondary)

                Text("No hay farmacias de guardia programadas")
                    .font(.title3)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text("Intente refrescar o seleccione una fecha diferente")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(32)
    }
}
