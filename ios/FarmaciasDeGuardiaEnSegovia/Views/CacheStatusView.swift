import SwiftUI

extension Notification.Name {
    /// Posted after a manual "force sync all" completes so open schedule screens can reload.
    static let pdfCacheForceRefreshed = Notification.Name("pdfCacheForceRefreshed")
}

struct LocationSyncStatus {
    let location: DutyLocation
    let version: Int?
    let lastSyncedAt: Date?

    var isSynced: Bool { version != nil }
}

struct CacheStatusView: View {
    @State private var statuses: [LocationSyncStatus] = []
    @State private var isLoading = true
    @State private var isRefreshing = false
    @State private var refreshStates: [String: RefreshState] = [:]
    @Environment(\.dismiss) private var dismiss

    private var refreshedCount: Int {
        refreshStates.values.filter { state in
            if case .completed = state { return true }
            return false
        }.count
    }

    var body: some View {
        Group {
            if isLoading {
                VStack {
                    ProgressView()
                    Text("Comprobando estado de la sincronización...")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.top, 8)
                }
            } else {
                List {
                    Section {
                        ForEach(statuses, id: \.location.id) { status in
                            LocationSyncStatusRow(status: status, refreshState: refreshStates[status.location.id])
                        }
                    } header: {
                        Text("Estado de sincronización")
                    } footer: {
                        Text("Los datos de guardias se sincronizan con el servidor y se guardan localmente para acceso sin conexión.")
                    }

                    Section {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Image(systemName: "info.circle.fill")
                                    .foregroundColor(.blue)
                                Text("Información")
                                    .font(.headline)
                            }

                            Text("• Verde: Datos sincronizados con el servidor")
                            Text("• Rojo: Sin sincronizar (usando datos incluidos en la app)")
                        }
                        .padding(.vertical, 8)
                    }

                    Section {
                        Button(action: {
                            refreshAll()
                        }) {
                            HStack {
                                Spacer()
                                if isRefreshing {
                                    ProgressView()
                                        .padding(.trailing, 8)
                                    Text("Sincronizando... \(refreshedCount)/\(DutyLocation.allSyncable.count)")
                                } else {
                                    Image(systemName: "arrow.clockwise")
                                    Text("Forzar sincronización")
                                }
                                Spacer()
                            }
                        }
                        .disabled(isRefreshing)
                    } footer: {
                        Text("Esto comprobará el servidor y descargará cualquier dato actualizado.")
                    }
                }
            }
        }
        .navigationTitle("Estado de sincronización")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button("Cerrar") {
                    dismiss()
                }
            }
        }
        .onAppear {
            loadStatus()
        }
    }

    private func loadStatus() {
        isLoading = true
        Task {
            let statuses = buildStatuses()
            await MainActor.run {
                self.statuses = statuses
                self.isLoading = false
            }
        }
    }

    private func buildStatuses() -> [LocationSyncStatus] {
        let cache = ScheduleCacheService.shared
        return DutyLocation.allSyncable.map { location in
            let version = cache.cachedServerVersion(for: location)
            let timestamp = cache.getCacheTimestamp(for: location)
            return LocationSyncStatus(
                location: location,
                version: version,
                lastSyncedAt: timestamp.map { Date(timeIntervalSince1970: $0) }
            )
        }
    }

    private func refreshAll() {
        let locations = DutyLocation.allSyncable

        isRefreshing = true
        refreshStates = Dictionary(uniqueKeysWithValues: locations.map { ($0.id, RefreshState.pending) })

        Task {
            for location in locations {
                await MainActor.run {
                    refreshStates[location.id] = .refreshing
                }

                _ = await ScheduleService.loadSchedules(for: location, forceRefresh: true)
                DebugConfig.debugPrint("✅ Force synced \(location.name)")

                await MainActor.run {
                    refreshStates[location.id] = .completed
                }
            }

            let statuses = buildStatuses()
            await MainActor.run {
                self.statuses = statuses
                self.isRefreshing = false
                self.refreshStates = [:]
                // Notify open schedule screens that fresh data is available
                NotificationCenter.default.post(name: .pdfCacheForceRefreshed, object: nil)
            }
        }
    }
}

struct LocationSyncStatusRow: View {
    let status: LocationSyncStatus
    var refreshState: RefreshState? = nil

    private var formattedLastSynced: String {
        guard let date = status.lastSyncedAt else { return "Nunca" }
        return date.formatted(date: .abbreviated, time: .shortened)
    }

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                HStack(spacing: 8) {
                    Text(status.location.icon)
                        .font(.title2)
                    Text(status.location.name)
                        .font(.headline)
                }

                Spacer()

                if let state = refreshState {
                    HStack(spacing: 6) {
                        switch state {
                        case .pending:
                            Image(systemName: "clock")
                                .foregroundColor(.gray)
                            Text("Pendiente")
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundColor(.gray)
                        case .refreshing:
                            ProgressView()
                                .scaleEffect(0.8)
                            Text("Sincronizando...")
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundColor(.blue)
                        case .completed:
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                            Text("Actualizado")
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundColor(.green)
                        case .error:
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.red)
                            Text("Error")
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundColor(.red)
                        }
                    }
                } else {
                    HStack(spacing: 6) {
                        Image(systemName: status.isSynced ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                            .foregroundColor(status.isSynced ? .green : .red)
                        Text(status.isSynced ? "Sincronizado" : "Sin sincronizar")
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(status.isSynced ? .green : .red)
                    }
                }
            }

            if status.isSynced {
                VStack(spacing: 6) {
                    HStack {
                        Text("Última sincronización:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(formattedLastSynced)
                            .font(.caption)
                            .fontWeight(.medium)
                    }

                    HStack {
                        Text("Versión:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("\(status.version ?? 0)")
                            .font(.caption)
                            .fontWeight(.medium)
                    }
                }
            } else {
                HStack {
                    Image(systemName: "shippingbox")
                        .foregroundColor(.blue)
                        .font(.caption)
                    Text("Usando datos incluidos en la app - se sincronizará cuando haya conexión")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                }
            }
        }
        .padding(.vertical, 4)
    }
}

struct CacheStatusView_Previews: PreviewProvider {
    static var previews: some View {
        CacheStatusView()
    }
}
