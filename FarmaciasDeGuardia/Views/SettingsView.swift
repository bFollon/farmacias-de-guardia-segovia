import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var showingCacheStatus = false
    @State private var showingCacheRefresh = false
    
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
                    
                    Button(action: { showingCacheRefresh = true }) {
                        HStack {
                            Image(systemName: "arrow.clockwise")
                            Text("Buscar Actualizaciones")
                            
                            Spacer()
                        }
                    }
                    
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
        .sheet(isPresented: $showingCacheStatus) {
            NavigationView {
                CacheStatusView()
            }
        }
        .sheet(isPresented: $showingCacheRefresh) {
            CacheRefreshView()
        }
    }
    
    struct SettingsView_Previews: PreviewProvider {
        static var previews: some View {
            SettingsView()
        }
    }
}
