import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var showingCacheStatus = false
    @State private var showingCacheRefresh = false
    @State private var showingAbout = false
    
    var body: some View {
        NavigationView {
            List {
                Section("Caché de PDFs") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("PDFs en caché")
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
                            Text("Ver Estado de la caché")
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                Section("Información") {
                    Button(action: { showingAbout = true }) {
                        HStack {
                            Image(systemName: "info.circle")
                            Text("Acerca de")
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .foregroundColor(.primary)
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
        .sheet(isPresented: $showingAbout) {
            AboutView()
        }
    }
    
    struct SettingsView_Previews: PreviewProvider {
        static var previews: some View {
            SettingsView()
        }
    }
}
