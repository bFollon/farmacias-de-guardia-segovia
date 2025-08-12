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
                Section("PDF Cache") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Cached PDFs")
                            .font(.headline)
                        
                        Text("PDF schedules are cached locally for faster loading and offline access.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                    
                    Button(action: checkForUpdates) {
                        HStack {
                            Image(systemName: "arrow.clockwise")
                            Text("Check for Updates")
                            
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
                            Text("View Cache Status")
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
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .alert("Update Check", isPresented: $showingUpdateResult) {
            Button("OK") { }
        } message: {
            Text(updateCheckMessage)
        }
        .sheet(isPresented: $showingCacheStatus) {
            NavigationView {
                CacheStatusView()
                    .navigationTitle("Cache Status")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("Done") {
                                showingCacheStatus = false
                            }
                        }
                    }
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
                    updateCheckMessage = "Update check completed. Check cache status for details."
                    showingUpdateResult = true
                    isCheckingForUpdates = false
                }
            } catch {
                await MainActor.run {
                    updateCheckMessage = "Error checking for updates: \(error.localizedDescription)"
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
