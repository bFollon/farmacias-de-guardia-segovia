import Foundation

/// Progress state for update operations
enum UpdateProgressState {
    case checking
    case upToDate
    case downloading
    case downloaded
    case error(String)
}

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

/// Cache status information for a specific region
struct RegionCacheStatus {
    let region: Region
    let isCached: Bool
    let downloadDate: Date?
    let fileSize: Int64?
    let lastChecked: Date?
    let needsUpdate: Bool
    
    var formattedFileSize: String {
        guard let fileSize = fileSize else { return "Desconocido" }
        return ByteCountFormatter.string(fromByteCount: fileSize, countStyle: .file)
    }
    
    var formattedDownloadDate: String {
        guard let downloadDate = downloadDate else { return "Nunca" }
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter.string(from: downloadDate)
    }
    
    var formattedLastChecked: String {
        guard let lastChecked = lastChecked else { return "Nunca" }
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter.string(from: lastChecked)
    }
    
    var statusIcon: String {
        if !isCached {
            return "xmark.circle.fill"
        } else if needsUpdate {
            return "arrow.triangle.2.circlepath"
        } else {
            return "checkmark.circle.fill"
        }
    }
    
    var statusColor: Color {
        if !isCached {
            return .red
        } else if needsUpdate {
            return .orange
        } else {
            return .green
        }
    }
    
    var statusText: String {
        if !isCached {
            return "No Descargado"
        } else if needsUpdate {
            return "Actualización Disponible"
        } else {
            return "Actualizado"
        }
    }
}

import SwiftUI

/// Manages local caching and updating of PDF files
class PDFCacheManager {
    static let shared = PDFCacheManager()
    
    private let fileManager = FileManager.default
    private let urlSession = URLSession.shared
    private let userDefaults = UserDefaults.standard
    
    // Version storage key
    private let versionStorageKey = "PDFCacheVersions"
    private let lastUpdateCheckKey = "PDFCacheLastUpdateCheck"
    
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
    func isCacheUpToDate(for region: Region, debugMode: Bool = false) async -> Bool {
        guard hasCachedFile(for: region),
              let cachedVersion = getStoredVersion(for: region) else {
            print("🔍 PDFCacheManager: No cached file or version for \(region.name)")
            return false
        }
        
        do {
            let remoteVersion = try await checkRemoteVersion(for: region)
            
            print("🔍 PDFCacheManager: Comparing versions for \(region.name):")
            if debugMode {
                print("   Cached ETag: \(cachedVersion.etag ?? "nil")")
                print("   Remote ETag: \(remoteVersion.etag ?? "nil")")
                print("   Cached Last-Modified: \(cachedVersion.lastModified?.description ?? "nil")")
                print("   Remote Last-Modified: \(remoteVersion.lastModified?.description ?? "nil")")
                print("   Cached Size: \(cachedVersion.contentLength?.description ?? "nil")")
                print("   Remote Size: \(remoteVersion.contentLength?.description ?? "nil")")
            }
            
            // 1. First try Last-Modified (most reliable for this server)
            if let cachedLastModified = cachedVersion.lastModified,
               let remoteLastModified = remoteVersion.lastModified {
                let isMatch = cachedLastModified == remoteLastModified
                if debugMode {
                    print("   ✅ Last-Modified comparison: \(isMatch ? "MATCH" : "DIFFERENT")")
                }
                return isMatch
            }
            
            // 2. Then try Content-Length as backup
            if let cachedLength = cachedVersion.contentLength,
               let remoteLength = remoteVersion.contentLength {
                let isMatch = cachedLength == remoteLength
                if debugMode {
                    print("   ✅ Content-Length comparison: \(isMatch ? "MATCH" : "DIFFERENT")")
                }
                return isMatch
            }
            
            // 3. Finally try ETag (least reliable for this server)
            if let cachedEtag = cachedVersion.etag,
               let remoteEtag = remoteVersion.etag {
                let isMatch = cachedEtag == remoteEtag
                if debugMode {
                    print("   ✅ ETag comparison: \(isMatch ? "MATCH" : "DIFFERENT")")
                }
                return isMatch
            }
            
            // If no comparison criteria available, consider outdated
            print("   ❌ No comparison criteria available, assuming outdated")
            return false
        } catch {
            print("❌ PDFCacheManager: Failed to check remote version for \(region.name): \(error)")
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
    
    /// Clear both PDF cache and parsing cache for a region (complete refresh)
    func clearAllCaches(for region: Region) {
        // Clear PDF cache
        clearCache(for: region)
        
        // Clear parsing cache from ScheduleService
        ScheduleService.clearCache()
        
        print("🗑️ PDFCacheManager: Cleared all caches for \(region.name)")
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
    
    // MARK: - Automatic Update Checking
    
    /// Check if we should perform automatic PDF update check
    private func shouldCheckForUpdates() -> Bool {
        guard let lastCheck = userDefaults.object(forKey: lastUpdateCheckKey) as? Date else {
            return true // Never checked before
        }
        
        // Check once per day
        let oneDayAgo = Date().addingTimeInterval(-24 * 60 * 60)
        return lastCheck < oneDayAgo
    }
    
    /// Record that we performed an update check
    private func recordUpdateCheck() {
        userDefaults.set(Date(), forKey: lastUpdateCheckKey)
    }
    
    /// Check all regions for PDF updates and download if needed
    func checkForUpdatesIfNeeded() async {
        guard shouldCheckForUpdates() else {
            print("📅 PDFCacheManager: Skipping update check - already checked today")
            return
        }
        
        print("🔍 PDFCacheManager: Checking for PDF updates...")
        recordUpdateCheck()
        
        let allRegions = [Region.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
        
        for region in allRegions {
            await checkAndUpdateIfNeeded(region: region)
        }
        
        print("✅ PDFCacheManager: Update check completed")
    }
    
    /// Check a specific region and update if needed
    private func checkAndUpdateIfNeeded(region: Region, debugMode: Bool = false) async {
        let isCacheValid = await isCacheUpToDate(for: region, debugMode: debugMode)
        
        if !isCacheValid {
            do {
                let _ = try await downloadAndCache(region: region)
                print("📥 PDFCacheManager: Updated PDF for \(region.name)")
            } catch {
                print("❌ PDFCacheManager: Failed to update PDF for \(region.name): \(error)")
            }
        } else {
            print("✅ PDFCacheManager: PDF for \(region.name) is up to date")
        }
    }
    
    /// Force check for updates (ignores daily limit)
    func forceCheckForUpdates() async {
        print("🔄 PDFCacheManager: Force checking for PDF updates...")
        recordUpdateCheck() // Update the timestamp
        
        let allRegions = [Region.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
        
        for region in allRegions {
            await checkAndUpdateIfNeeded(region: region, debugMode: true)
        }
        
        print("✅ PDFCacheManager: Force update check completed")
    }
    
    /// Force check for updates with progress callbacks for UI
    func forceCheckForUpdatesWithProgress(progressCallback: @escaping (Region, UpdateProgressState) async -> Void) async {
        print("🔄 PDFCacheManager: Force checking for PDF updates with progress...")
        recordUpdateCheck() // Update the timestamp
        
        let allRegions = [Region.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
        
        for region in allRegions {
            await checkAndUpdateIfNeededWithProgress(region: region, progressCallback: progressCallback)
        }
        
        print("✅ PDFCacheManager: Force update check with progress completed")
    }
    
    /// Check a specific region and update if needed with progress callbacks
    private func checkAndUpdateIfNeededWithProgress(region: Region, progressCallback: @escaping (Region, UpdateProgressState) async -> Void) async {
        // Notify checking started
        await progressCallback(region, .checking)
        
        let isCacheValid = await isCacheUpToDate(for: region, debugMode: true)
        
        if !isCacheValid {
            // Notify download starting
            await progressCallback(region, .downloading)
            
            do {
                let _ = try await downloadAndCache(region: region)
                print("📥 PDFCacheManager: Updated PDF for \(region.name)")
                await progressCallback(region, .downloaded)
            } catch {
                print("❌ PDFCacheManager: Failed to update PDF for \(region.name): \(error)")
                await progressCallback(region, .error(error.localizedDescription))
            }
        } else {
            print("✅ PDFCacheManager: PDF for \(region.name) is up to date")
            await progressCallback(region, .upToDate)
        }
    }
    
    /// Print current cache status for all regions
    func printCacheStatus() {
        print("\nPDFCacheManager Status:")
        print("Cache Directory: \(cacheDirectory.path)")
        print("")
        
        let allRegions = [Region.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
        
        for region in allRegions {
            let filename = cacheFileName(for: region)
            let localURL = cacheDirectory.appendingPathComponent(filename)
            
            if fileManager.fileExists(atPath: localURL.path) {
                do {
                    let attributes = try fileManager.attributesOfItem(atPath: localURL.path)
                    let fileSize = attributes[FileAttributeKey.size] as? Int64 ?? 0
                    let modificationDate = attributes[FileAttributeKey.modificationDate] as? Date ?? Date()
                    
                    let formatter = DateFormatter()
                    formatter.dateStyle = .short
                    formatter.timeStyle = .short
                    
                    print("📄 \(region.name): ✅")
                    print("   Downloaded: \(formatter.string(from: modificationDate))")
                    print("   Size: \(ByteCountFormatter.string(fromByteCount: fileSize, countStyle: .file))")
                    print("")
                } catch {
                    print("📄 \(region.name): ❌ Error reading file info")
                    print("")
                }
            } else {
                print("📄 \(region.name): ❌ Not cached")
                print("")
            }
        }
    }
    
    /// Get structured cache status for all regions
    func getCacheStatus() async -> [RegionCacheStatus] {
        let allRegions = [Region.segoviaCapital, .cuellar, .elEspinar, .segoviaRural]
        let lastUpdateCheck = userDefaults.object(forKey: lastUpdateCheckKey) as? Date
        
        var statuses: [RegionCacheStatus] = []
        
        for region in allRegions {
            let filename = cacheFileName(for: region)
            let localURL = cacheDirectory.appendingPathComponent(filename)
            let storedVersion = getStoredVersion(for: region)
            
            var isCached = false
            var downloadDate: Date?
            var fileSize: Int64?
            var needsUpdate = false
            
            if fileManager.fileExists(atPath: localURL.path) {
                isCached = true
                
                do {
                    let attributes = try fileManager.attributesOfItem(atPath: localURL.path)
                    fileSize = attributes[FileAttributeKey.size] as? Int64
                    downloadDate = storedVersion?.downloadDate ?? (attributes[FileAttributeKey.modificationDate] as? Date)
                    
                    // Check if update is needed (simplified check)
                    needsUpdate = await checkIfUpdateNeeded(for: region, storedVersion: storedVersion)
                    
                } catch {
                    print("❌ PDFCacheManager: Error reading file attributes for \(region.name): \(error)")
                }
            }
            
            let status = RegionCacheStatus(
                region: region,
                isCached: isCached,
                downloadDate: downloadDate,
                fileSize: fileSize,
                lastChecked: lastUpdateCheck,
                needsUpdate: needsUpdate
            )
            
            statuses.append(status)
        }
        
        return statuses
    }
    
    /// Check if a region needs an update (lightweight version)
    private func checkIfUpdateNeeded(for region: Region, storedVersion: PDFVersion?) async -> Bool {
        guard let storedVersion = storedVersion else { return true }
        
        do {
            var request = URLRequest(url: region.remotePDFURL)
            request.httpMethod = "HEAD"  // Use HEAD request for lightweight check
            request.cachePolicy = .reloadIgnoringLocalCacheData
            
            let (_, response) = try await urlSession.data(for: request)
            
            if let httpResponse = response as? HTTPURLResponse {
                // Quick check using Last-Modified header
                if let lastModifiedString = httpResponse.value(forHTTPHeaderField: "Last-Modified"),
                   let serverLastModified = parseHTTPDate(lastModifiedString),
                   let cachedLastModified = storedVersion.lastModified {
                    return serverLastModified > cachedLastModified
                }
                
                // Fallback to ETag if available
                if let serverETag = httpResponse.value(forHTTPHeaderField: "ETag"),
                   let cachedETag = storedVersion.etag {
                    return serverETag != cachedETag
                }
            }
            
            return false // Assume up-to-date if we can't determine
        } catch {
            print("⚠️ PDFCacheManager: Could not check update status for \(region.name): \(error)")
            return false // Don't assume update needed on network error
        }
    }
    
    /// Clear the last update check timestamp (for debugging)
    func clearLastUpdateCheck() {
        userDefaults.removeObject(forKey: lastUpdateCheckKey)
        print("🗑️ PDFCacheManager: Cleared last update check timestamp")
    }
    
    /// Parse HTTP date string to Date
    private func parseHTTPDate(_ dateString: String) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(abbreviation: "GMT")
        
        // Try common HTTP date formats
        let formats = [
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",     // RFC 1123
            "EEEE, dd-MMM-yy HH:mm:ss 'GMT'",     // RFC 850
            "EEE MMM d HH:mm:ss yyyy"             // ANSI C asctime()
        ]
        
        for format in formats {
            formatter.dateFormat = format
            if let date = formatter.date(from: dateString) {
                return date
            }
        }
        
        return nil
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
