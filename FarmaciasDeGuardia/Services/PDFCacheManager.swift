import Foundation

/// PDF version information for tracking updates
struct PDFVersion: Codable {
    let url: URL
    let lastModified: Date?
    let contentLength: Int64?
    let etag: String?
    let downloadDate: Date
    
    init(url: URL, lastModified: Date? = nil, contentLength: Int64? = nil, etag: String? = nil) {
        self.url = url
        self.lastModified = lastModified
        self.contentLength = contentLength
        self.etag = etag
        self.downloadDate = Date()
    }
}

/// Manages local caching and updating of PDF files
class PDFCacheManager {
    static let shared = PDFCacheManager()
    
    private let fileManager = FileManager.default
    private let urlSession = URLSession.shared
    private let userDefaults = UserDefaults.standard
    
    // Version storage key
    private let versionStorageKey = "PDFCacheVersions"
    
    // MARK: - Storage Locations
    
    private var documentsDirectory: URL {
        fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
    }
    
    private var cacheDirectory: URL {
        documentsDirectory.appendingPathComponent("PDFCache", isDirectory: true)
    }
    
    // MARK: - Initialization
    
    private init() {
        createCacheDirectoryIfNeeded()
    }
    
    private func createCacheDirectoryIfNeeded() {
        do {
            try fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
            print("📁 PDFCacheManager: Cache directory ready at \(cacheDirectory.path)")
        } catch {
        }
    }
    
    // MARK: - Version Management
    
    /// Get stored version info for a region
    private func getStoredVersion(for region: Region) -> PDFVersion? {
        guard let data = userDefaults.data(forKey: versionStorageKey) else { return nil }
        
        do {
            let versions = try JSONDecoder().decode([String: PDFVersion].self, from: data)
            return versions[region.name]
        } catch {
            print("❌ PDFCacheManager: Failed to decode version data: \(error)")
            return nil
        }
    }
    
    /// Store version info for a region
    private func storeVersion(_ version: PDFVersion, for region: Region) {
        var versions: [String: PDFVersion] = [:]
        
        // Load existing versions
        if let data = userDefaults.data(forKey: versionStorageKey) {
            do {
                versions = try JSONDecoder().decode([String: PDFVersion].self, from: data)
            } catch {
                print("⚠️ PDFCacheManager: Failed to decode existing versions, starting fresh")
            }
        }
        
        // Update with new version
        versions[region.name] = version
        
        // Save back to UserDefaults
        do {
            let data = try JSONEncoder().encode(versions)
            userDefaults.set(data, forKey: versionStorageKey)
            print("💾 PDFCacheManager: Stored version info for \(region.name)")
        } catch {
            print("❌ PDFCacheManager: Failed to store version data: \(error)")
        }
    }
    
    // MARK: - File Management
    
    /// Get the local cache file URL for a region
    func cachedFileURL(for region: Region) -> URL? {
        let filename = cacheFileName(for: region)
        let fileURL = cacheDirectory.appendingPathComponent(filename)
        
        return fileManager.fileExists(atPath: fileURL.path) ? fileURL : nil
    }
    
    /// Generate cache filename for a region
    private func cacheFileName(for region: Region) -> String {
        switch region {
        case .segoviaCapital:
            return "segovia-capital.pdf"
        case .cuellar:
            return "cuellar.pdf"
        case .elEspinar:
            return "el-espinar.pdf"
        case .segoviaRural:
            return "segovia-rural.pdf"
        default:
            return "\(region.name.lowercased().replacingOccurrences(of: " ", with: "-")).pdf"
        }
    }
    
    // MARK: - Version Checking
    
    /// Check remote PDF version without downloading the full file
    func checkRemoteVersion(for region: Region) async throws -> PDFVersion {
        let url = region.remotePDFURL
        var request = URLRequest(url: url)
        request.httpMethod = "HEAD"
        request.timeoutInterval = 10.0
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw NSError(domain: "PDFCacheManager", code: 1, 
                         userInfo: [NSLocalizedDescriptionKey: "Failed to get remote PDF info"])
        }
        
        let lastModified = httpResponse.value(forHTTPHeaderField: "Last-Modified")
        let contentLength = httpResponse.expectedContentLength > 0 ? httpResponse.expectedContentLength : nil
        let etag = httpResponse.value(forHTTPHeaderField: "ETag")
        
        // Parse last modified date if available
        var lastModifiedDate: Date? = nil
        if let lastModifiedString = lastModified {
            let formatter = DateFormatter()
            formatter.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
            formatter.locale = Locale(identifier: "en_US_POSIX")
            lastModifiedDate = formatter.date(from: lastModifiedString)
        }
        
        return PDFVersion(
            url: url,
            lastModified: lastModifiedDate,
            contentLength: contentLength,
            etag: etag
        )
    }
    
    /// Check if cached version is up to date
    func isCacheUpToDate(for region: Region) async -> Bool {
        guard hasCachedFile(for: region),
              let cachedVersion = getStoredVersion(for: region) else {
            return false
        }
        
        do {
            let remoteVersion = try await checkRemoteVersion(for: region)
            
            // Compare versions using multiple criteria
            if let cachedEtag = cachedVersion.etag,
               let remoteEtag = remoteVersion.etag {
                return cachedEtag == remoteEtag
            }
            
            if let cachedLastModified = cachedVersion.lastModified,
               let remoteLastModified = remoteVersion.lastModified {
                return cachedLastModified == remoteLastModified
            }
            
            if let cachedLength = cachedVersion.contentLength,
               let remoteLength = remoteVersion.contentLength {
                return cachedLength == remoteLength
            }
            
            // If no comparison criteria available, consider outdated
            return false
        } catch {
            print("PDFCacheManager: Failed to check remote version for \(region.name): \(error)")
            // If we can't check remote, assume cache is valid for now
            return true
        }
    }
    
    // MARK: - Download and Cache
    
    /// Download and cache a PDF file
    func downloadAndCache(region: Region, progressHandler: ((Double) -> Void)? = nil) async throws -> URL {
        let url = region.remotePDFURL
        let cacheFileURL = cacheDirectory.appendingPathComponent(cacheFileName(for: region))
        
        // Create a download task with progress tracking
        let (tempURL, response) = try await URLSession.shared.download(from: url)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw NSError(domain: "PDFCacheManager", code: 2,
                         userInfo: [NSLocalizedDescriptionKey: "Failed to download PDF"])
        }
        
        // Move downloaded file to cache location
        if fileManager.fileExists(atPath: cacheFileURL.path) {
            try fileManager.removeItem(at: cacheFileURL)
        }
        
        try fileManager.moveItem(at: tempURL, to: cacheFileURL)
        
        // Store version information
        let lastModified = httpResponse.value(forHTTPHeaderField: "Last-Modified")
        let contentLength = httpResponse.expectedContentLength > 0 ? httpResponse.expectedContentLength : nil
        let etag = httpResponse.value(forHTTPHeaderField: "ETag")
        
        // Parse last modified date if available
        var lastModifiedDate: Date? = nil
        if let lastModifiedString = lastModified {
            let formatter = DateFormatter()
            formatter.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
            formatter.locale = Locale(identifier: "en_US_POSIX")
            lastModifiedDate = formatter.date(from: lastModifiedString)
        }
        
        let version = PDFVersion(
            url: url,
            lastModified: lastModifiedDate,
            contentLength: contentLength,
            etag: etag
        )
        
        storeVersion(version, for: region)
        
        print("✅ PDFCacheManager: Successfully cached PDF for \(region.name)")
        return cacheFileURL
    }
    
    /// Get the effective PDF URL (cached if available and up-to-date, otherwise remote)
    func getEffectivePDFURL(for region: Region) async -> URL {
        // Check if we have a valid cached version
        let isCacheValid = await isCacheUpToDate(for: region)
        
        if isCacheValid {
            return cachedFileURL(for: region) ?? region.remotePDFURL
        } else {
            // Cache is outdated or doesn't exist, try to download
            do {
                return try await downloadAndCache(region: region)
            } catch {
                print("❌ PDFCacheManager: Failed to download PDF for \(region.name): \(error)")
                // Fall back to remote URL
                return region.remotePDFURL
            }
        }
    }
    
    // MARK: - Cache Management
    
    /// Clear all cached PDF files
    func clearCache() {
        let allRegions = [Region.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
        
        for region in allRegions {
            if let cachedURL = cachedFileURL(for: region) {
                try? fileManager.removeItem(at: cachedURL)
                print("🗑️ PDFCacheManager: Removed cached file for \(region.name)")
            }
        }
        
        // Clear version info
        userDefaults.removeObject(forKey: versionStorageKey)
        print("🗑️ PDFCacheManager: Cleared all version info")
    }
    
    /// Clear cache for a specific region
    func clearCache(for region: Region) {
        if let cachedURL = cachedFileURL(for: region) {
            try? fileManager.removeItem(at: cachedURL)
            print("🗑️ PDFCacheManager: Removed cached file for \(region.name)")
        }
        
        // Remove version info for this region
        if let data = userDefaults.data(forKey: versionStorageKey),
           var versions = try? JSONDecoder().decode([String: PDFVersion].self, from: data) {
            versions.removeValue(forKey: region.name)
            
            if let updatedData = try? JSONEncoder().encode(versions) {
                userDefaults.set(updatedData, forKey: versionStorageKey)
            }
        }
    }
    
    /// Force download for a region (bypasses cache check)
    func forceDownload(for region: Region) async throws -> URL {
        // Clear existing cache first
        clearCache(for: region)
        
        // Download fresh copy
        return try await downloadAndCache(region: region)
    }
    
    // MARK: - Public Interface (Non-breaking)
    
    /// Initialize cache manager (call on app launch)
    func initialize() {
        print("🚀 PDFCacheManager: Initialized")
        print(getCacheInfo())
    }
    
    /// Check if a cached file exists for the region
    func hasCachedFile(for region: Region) -> Bool {
        return cachedFileURL(for: region) != nil
    }
    
    /// Get cache info for debugging
    func getCacheInfo() -> String {
        let allRegions = [Region.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
        var info = "PDFCacheManager Status:\n"
        info += "Cache Directory: \(cacheDirectory.path)\n\n"
        
        for region in allRegions {
            let cached = hasCachedFile(for: region) ? "✅" : "❌"
            info += "📄 \(region.name): \(cached)\n"
            
            if let version = getStoredVersion(for: region) {
                let formatter = DateFormatter()
                formatter.dateStyle = .short
                formatter.timeStyle = .short
                info += "   Downloaded: \(formatter.string(from: version.downloadDate))\n"
                if let size = version.contentLength {
                    info += "   Size: \(ByteCountFormatter.string(fromByteCount: size, countStyle: .file))\n"
                }
            } else {
                info += "   No version info\n"
            }
            info += "\n"
        }
        
        return info
    }
}

// MARK: - Region Extension (Additive Only)

extension Region {
    /// Get the original remote PDF URL (unchanged)
    var remotePDFURL: URL {
        return self.pdfURL // Use existing property
    }
    
    /// Get the cached PDF URL if available
    var cachedPDFURL: URL? {
        return PDFCacheManager.shared.cachedFileURL(for: self)
    }
    
    /// Get the effective PDF URL (cached first, fallback to remote)
    var effectivePDFURL: URL {
        return cachedPDFURL ?? remotePDFURL
    }
    
    /// Get the best available PDF URL asynchronously (preferred method)
    func getEffectivePDFURL() async -> URL {
        return await PDFCacheManager.shared.getEffectivePDFURL(for: self)
    }
}
