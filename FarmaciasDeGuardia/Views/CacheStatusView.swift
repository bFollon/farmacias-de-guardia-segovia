import SwiftUI

struct CacheStatusView: View {
    @State private var cacheStatuses: [RegionCacheStatus] = []
    @State private var isLoading = true
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            Group {
                if isLoading {
                    VStack {
                        ProgressView()
                        Text("Checking cache status...")
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
                            Text("PDF Cache Status")
                        } footer: {
                            Text("PDFs are cached locally for faster loading and offline access.")
                        }
                        
                        Section {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Image(systemName: "info.circle.fill")
                                        .foregroundColor(.blue)
                                    Text("Cache Information")
                                        .font(.headline)
                                }
                                
                                Text("• Green: PDF is downloaded and up to date")
                                Text("• Orange: PDF update is available")
                                Text("• Red: PDF is not downloaded")
                                
                                if let lastChecked = cacheStatuses.first?.lastChecked {
                                    Text("Last checked: \(lastChecked.formatted(date: .abbreviated, time: .shortened))")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                        .padding(.top, 4)
                                } else {
                                    Text("Cache has never been checked")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                        .padding(.top, 4)
                                }
                            }
                            .padding(.vertical, 8)
                        }
                    }
                }
            }
            .navigationTitle("Cache Status")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Close") {
                        dismiss()
                    }
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Refresh") {
                        loadCacheStatus()
                    }
                    .disabled(isLoading)
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
                        Text("Downloaded:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(status.formattedDownloadDate)
                            .font(.caption)
                            .fontWeight(.medium)
                    }
                    
                    HStack {
                        Text("File Size:")
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
                            Text("An update is available for this PDF")
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
                    Text("PDF not downloaded - will be fetched when needed")
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
