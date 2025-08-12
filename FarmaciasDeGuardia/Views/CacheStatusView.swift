import SwiftUI

struct CacheStatusView: View {
    @State private var cacheStatuses: [RegionCacheStatus] = []
    @State private var isLoading = true
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        Group {
            if isLoading {
                VStack {
                    ProgressView()
                    Text("Comprobando estado del caché...")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.top, 8)
                }
            } else {
                List {
                    Section {
                        ForEach(cacheStatuses, id: \.region.id) { status in
                            CacheStatusRow(status: status)
                        }
                    } header: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Estado del Caché de PDFs")
                            if let lastChecked = cacheStatuses.first?.lastChecked {
                                Text("Última búsqueda de actualizaciones: \(lastChecked.formatted(date: .abbreviated, time: .shortened))")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .textCase(nil)
                            } else {
                                Text("El caché nunca se ha comprobado para actualizaciones")
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
                                Text("Información del Caché")
                                    .font(.headline)
                            }
                            
                            Text("• Verde: PDF descargado y actualizado")
                            Text("• Naranja: Actualización del PDF disponible")
                            Text("• Rojo: PDF no descargado")
                        }
                        .padding(.vertical, 8)
                    }
                }
            }
        }
        .navigationTitle("Estado del Caché")
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
}

struct CacheStatusRow: View {
    let status: RegionCacheStatus
    
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
                
                HStack(spacing: 6) {
                    Image(systemName: status.statusIcon)
                        .foregroundColor(status.statusColor)
                    Text(status.statusText)
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(status.statusColor)
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
