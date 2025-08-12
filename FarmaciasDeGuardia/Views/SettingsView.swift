import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var isCheckingForUpdates = false
    @State private var updateCheckMessage = ""
    @State private var showingUpdateResult = false
    @State private var showingCacheStatus = false
    
    var body: some View {
        NavigationView {
            List {
                Section("Caché de PDFs") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("PDFs en Caché")
                            .font(.headline)
                        
                        Text("Los horarios PDF se almacenan localmente para una carga más rápida y acceso sin conexión.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                    
                    Button(action: checkForUpdates) {
                        HStack {
                            Image(systemName: "arrow.clockwise")
                            Text("Buscar Actualizaciones")
                            
                            Spacer()
                            
                            if isCheckingForUpdates {
                                ProgressView()
                                    .scaleEffect(0.8)
                            }
                        }
                    }
                    .disabled(isCheckingForUpdates)
                    
                    Button(action: { showingCacheStatus = true }) {
                        HStack {
                            Image(systemName: "info.circle")
                            Text("Ver Estado del Caché")
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                Section("About") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Farmacias de Guardia")
                            .font(.headline)
                        
                        Text("Consulta las farmacias de guardia de Segovia. Los datos se obtienen de los PDFs oficiales del Colegio de Farmacéuticos.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle("Configuración")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Listo") {
                        dismiss()
                    }
                }
            }
        }
        .alert("Búsqueda de Actualizaciones", isPresented: $showingUpdateResult) {
            Button("OK") { }
        } message: {
            Text(updateCheckMessage)
        }
        .sheet(isPresented: $showingCacheStatus) {
            NavigationView {
                CacheStatusView()
            }
        }
    }
    
    private func checkForUpdates() {
        isCheckingForUpdates = true
        updateCheckMessage = ""
        
        Task {
            do {
                await PDFCacheManager.shared.forceCheckForUpdates()
                
                await MainActor.run {
                    updateCheckMessage = "Búsqueda de actualizaciones completada. Consulta el estado del caché para más detalles."
                    showingUpdateResult = true
                    isCheckingForUpdates = false
                }
            } catch {
                await MainActor.run {
                    updateCheckMessage = "Error al buscar actualizaciones: \(error.localizedDescription)"
                    showingUpdateResult = true
                    isCheckingForUpdates = false
                }
            }
        }
    }
}

struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView()
    }
}
