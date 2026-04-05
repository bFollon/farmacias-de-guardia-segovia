import SwiftUI

extension Notification.Name {
    /// Posted after a manual "force update all PDFs" completes so open PDFViewScreens can reload.
    static let pdfCacheForceRefreshed = Notification.Name("pdfCacheForceRefreshed")
}

struct CacheStatusView: View {
    @State private var cacheStatuses: [RegionCacheStatus] = []
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
                    Text("Comprobando estado de la caché...")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.top, 8)
                }
            } else {
                List {
                    Section {
                        ForEach(cacheStatuses, id: \.region.id) { status in
                            CacheStatusRow(status: status, refreshState: refreshStates[status.region.id])
                        }
                    } header: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Estado de la caché de PDFs")
                            if let lastChecked = cacheStatuses.first?.lastChecked {
                                Text("Última búsqueda de actualizaciones: \(lastChecked.formatted(date: .abbreviated, time: .shortened))")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .textCase(nil)
                            } else {
                                Text("La caché nunca se ha comprobado para actualizaciones")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .textCase(nil)
                            }
                        }
                    } footer: {
                        Text("Los PDFs se almacenan localmente para una carga más rápida y acceso sin conexión.")
                    }
                    
                    Section {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Image(systemName: "info.circle.fill")
                                    .foregroundColor(.blue)
                                Text("Información de la caché")
                                    .font(.headline)
                            }

                            Text("• Verde: PDF descargado y actualizado")
                            Text("• Naranja: Actualización del PDF disponible")
                            Text("• Rojo: PDF no descargado")
                        }
                        .padding(.vertical, 8)
                    }

                    Section {
                        Button(action: {
                            refreshAllCaches()
                        }) {
                            HStack {
                                Spacer()
                                if isRefreshing {
                                    ProgressView()
                                        .padding(.trailing, 8)
                                    Text("Actualizando PDFs... \(refreshedCount)/4")
                                } else {
                                    Image(systemName: "arrow.clockwise")
                                    Text("Forzar actualización de todos los PDFs")
                                }
                                Spacer()
                            }
                        }
                        .disabled(isRefreshing)
                    } footer: {
                        Text("Esto descargará y procesará nuevamente todos los PDFs de guardias, actualizando la caché.")
                    }
                }
            }
        }
        .navigationTitle("Estado de la caché")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button("Cerrar") {
                    dismiss()
                }
            }
        }
        .onAppear {
            loadCacheStatus()
        }
    }
    
    private func loadCacheStatus() {
        isLoading = true

        Task {
            let statuses = await PDFCacheManager.shared.getCacheStatus()

            await MainActor.run {
                self.cacheStatuses = statuses
                self.isLoading = false
            }
        }
    }

    private func refreshAllCaches() {
        let allRegions: [Region] = [.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]

        isRefreshing = true
        refreshStates = Dictionary(uniqueKeysWithValues: allRegions.map { ($0.id, RefreshState.pending) })

        Task {
            // Force re-parse all PDFs, updating state per region
            for region in allRegions {
                await MainActor.run {
                    refreshStates[region.id] = .refreshing
                }

                let location = DutyLocation.fromRegion(region)
                _ = await ScheduleService.loadSchedules(for: location, forceRefresh: true)
                DebugConfig.debugPrint("✅ Force refreshed cache for \(region.name)")

                await MainActor.run {
                    refreshStates[region.id] = .completed
                }
            }

            // Reload cache status to reflect updates
            let statuses = await PDFCacheManager.shared.getCacheStatus()

            await MainActor.run {
                self.cacheStatuses = statuses
                self.isRefreshing = false
                self.refreshStates = [:]
                // Notify open PDFViewScreens that fresh data is available
                NotificationCenter.default.post(name: .pdfCacheForceRefreshed, object: nil)
            }
        }
    }
}

struct CacheStatusRow: View {
    let status: RegionCacheStatus
    var refreshState: RefreshState? = nil

    var body: some View {
        VStack(spacing: 12) {
            // Header with region and status
            HStack {
                HStack(spacing: 8) {
                    Text(status.region.icon)
                        .font(.title2)
                    Text(status.region.name)
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
                            Text("Procesando...")
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
                        Image(systemName: status.statusIcon)
                            .foregroundColor(status.statusColor)
                        Text(status.statusText)
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(status.statusColor)
                    }
                }
            }
            
            // Details
            if status.isCached {
                VStack(spacing: 6) {
                    HStack {
                        Text("Descargado:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(status.formattedDownloadDate)
                            .font(.caption)
                            .fontWeight(.medium)
                    }
                    
                    HStack {
                        Text("Tamaño:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(status.formattedFileSize)
                            .font(.caption)
                            .fontWeight(.medium)
                    }
                    
                    if status.needsUpdate {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.orange)
                                .font(.caption)
                            Text("Hay una actualización disponible para este PDF")
                                .font(.caption)
                                .foregroundColor(.orange)
                            Spacer()
                        }
                        .padding(.top, 4)
                    }
                }
            } else {
                HStack {
                    Image(systemName: "arrow.down.circle")
                        .foregroundColor(.blue)
                        .font(.caption)
                    Text("PDF no descargado - se obtendrá cuando sea necesario")
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
