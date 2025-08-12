import SwiftUI

struct CacheRefreshView: View {
    @State private var refreshStates: [String: RefreshState] = [:]
    @State private var isCompleted = false
    @Environment(\.dismiss) private var dismiss
    
    let regions = [Region.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                regionsList
                
                if isCompleted {
                    completionView
                        .background(Color(UIColor.systemGroupedBackground))
                }
                
                Spacer()
            }
            .background(Color(UIColor.systemGroupedBackground))
            .navigationTitle("Actualizar caché")
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
    
    private var regionsList: some View {
        List {
            Section {
                ForEach(regions, id: \.id) { region in
                    CacheRefreshRow(
                        region: region,
                        state: refreshStates[region.id] ?? .pending
                    )
                }
            } header: {
                Text("Estado de Actualización")
            }
        }
    }
    
    private var headerView: some View {
        VStack(spacing: 16) {
            Text("Actualizando caché de PDFs")
                .font(.headline)
            
            Text("Comprobando actualizaciones para cada región...")
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
            
            Text("¡Actualización Completada!")
                .font(.headline)
            
            Text("Todos los PDFs han sido comprobados")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }
    
    private func startRefresh() {
        // Initialize all regions as pending
        for region in regions {
            refreshStates[region.id] = .pending
        }
        
        Task {
            // Use the existing forceCheckForUpdates logic but with visual progress
            await PDFCacheManager.shared.forceCheckForUpdatesWithProgress { region, state in
                await MainActor.run {
                    switch state {
                    case .checking:
                        refreshStates[region.id] = .refreshing
                    case .upToDate:
                        refreshStates[region.id] = .completed
                    case .downloading:
                        refreshStates[region.id] = .refreshing
                    case .downloaded:
                        refreshStates[region.id] = .completed
                    case .error(let message):
                        refreshStates[region.id] = .error(message)
                    }
                }
            }
            
            await MainActor.run {
                isCompleted = true
            }
        }
    }
}

struct CacheRefreshRow: View {
    let region: Region
    let state: RefreshState
    
    var body: some View {
        HStack(spacing: 12) {
            HStack(spacing: 8) {
                Text(region.icon)
                    .font(.title2)
                Text(region.name)
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
                    
                case .error(let message):
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
