import SwiftUI

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var isCheckingForUpdates = false
    @State private var updateCheckMessage = ""
    @State private var showingUpdateResult = false
    
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
                    
                    Button(action: showCacheStatus) {
                        HStack {
                            Image(systemName: "info.circle")
                            Text("Show Cache Status")
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
    }
    
    private func checkForUpdates() {
        isCheckingForUpdates = true
        updateCheckMessage = ""
        
        Task {
            do {
                await PDFCacheManager.shared.forceCheckForUpdates()
                
                await MainActor.run {
                    updateCheckMessage = "Update check completed. See console for details."
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
    
    private func showCacheStatus() {
        PDFCacheManager.shared.printCacheStatus()
        updateCheckMessage = "Cache status printed to console."
        showingUpdateResult = true
    }
}

struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView()
    }
}
