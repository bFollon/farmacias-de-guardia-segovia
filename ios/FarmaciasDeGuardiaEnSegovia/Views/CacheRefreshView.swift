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

struct CacheRefreshView: View {
    @State private var refreshStates: [String: RefreshState] = [:]
    @State private var isCompleted = false
    @Environment(\.dismiss) private var dismiss

    let locations = DutyLocation.allSyncable

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                locationsList

                if isCompleted {
                    completionView
                        .background(Color(UIColor.systemGroupedBackground))
                }

                Spacer()
            }
            .background(Color(UIColor.systemGroupedBackground))
            .navigationTitle("Sincronizar datos")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cerrar") {
                        dismiss()
                    }
                    .disabled(!isCompleted)
                }
            }
        }
        .onAppear {
            startRefresh()
        }
    }

    private var locationsList: some View {
        List {
            Section {
                ForEach(locations, id: \.id) { location in
                    CacheRefreshRow(
                        location: location,
                        state: refreshStates[location.id] ?? .pending
                    )
                }
            } header: {
                Text("Estado de Sincronización")
            }
        }
    }

    private var headerView: some View {
        VStack(spacing: 16) {
            Text("Sincronizando datos de guardias")
                .font(.headline)

            Text("Comprobando actualizaciones para cada ubicación...")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 40)
        .opacity(isCompleted ? 0 : 1)
        .animation(.easeInOut(duration: 0.3), value: isCompleted)
    }

    private var completionView: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 48))
                .foregroundColor(.green)

            Text("¡Sincronización Completada!")
                .font(.headline)

            Text("Todas las ubicaciones han sido comprobadas")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }

    private func startRefresh() {
        AnalyticsService.shared.track("cache_refresh_triggered", with: ["location_count": locations.count])

        // Initialize all locations as pending
        for location in locations {
            refreshStates[location.id] = .pending
        }

        // Clear coordinate caches at the start of refresh
        GeocodingService.clearAllCaches()

        for location in locations {
            refreshStates[location.id] = .refreshing
        }

        Task {
            let cacheService = ScheduleCacheService.shared
            let syncService = ScheduleSyncService.shared

            // Empty knownVersions forces a real conditional GET for every location, bypassing
            // the manifest-version pre-check - this is an explicit "check everything now" action.
            let summary = await syncService.syncAll(locationIds: locations.map(\.id), knownVersions: [:]) { locationId, schedule in
                guard let location = locations.first(where: { $0.id == locationId }) else { return }
                cacheService.saveSchedulesToCache(for: location, schedules: schedule.schedules, version: schedule.version)
            }

            await MainActor.run {
                for location in locations {
                    refreshStates[location.id] = summary.failed[location.id] != nil ? .error("Error de sincronización") : .completed
                }
                isCompleted = true
                NotificationCenter.default.post(name: .pdfCacheForceRefreshed, object: nil)
            }
        }
    }
}

struct CacheRefreshRow: View {
    let location: DutyLocation
    let state: RefreshState

    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 8) {
                Text(location.icon)
                    .font(.title2)
                Text(location.name)
                    .font(.headline)
            }

            Spacer()
            
            HStack(spacing: 8) {
                switch state {
                case .pending:
                    Image(systemName: "clock")
                        .foregroundColor(.gray)
                    Text("Pendiente")
                        .font(.caption)
                        .foregroundColor(.gray)
                    
                case .refreshing:
                    ProgressView()
                        .scaleEffect(0.8)
                    Text("Comprobando...")
                        .font(.caption)
                        .foregroundColor(.blue)
                    
                case .completed:
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                    Text("Actualizado")
                        .font(.caption)
                        .foregroundColor(.green)
                    
                case .error(_):
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.red)
                    Text("Error")
                        .font(.caption)
                        .foregroundColor(.red)
                }
            }
        }
        .padding(.vertical, 4)
    }
}

enum RefreshState {
    case pending
    case refreshing
    case completed
    case error(String)
}

struct CacheRefreshView_Previews: PreviewProvider {
    static var previews: some View {
        CacheRefreshView()
    }
}
