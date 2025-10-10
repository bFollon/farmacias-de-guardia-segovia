/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import Foundation

/// Repository for managing PDF URLs with scraping, persistence, and validation
///
/// Responsibilities:
/// - Scrape PDF URLs from website
/// - Persist scraped URLs to UserDefaults
/// - Validate URLs with HEAD requests
/// - Provide self-healing URL resolution
class PDFURLRepository {
    static let shared = PDFURLRepository()
    
    private let userDefaults = UserDefaults.standard
    private let urlSession: URLSession
    
    // In-memory cache for HEAD request results (until app restart)
    private var validationCache: [String: URLValidationResult] = [:]
    
    // Storage keys
    private let scrapedURLsKey = "scraped_urls"
    private let lastScrapeKey = "last_scrape_timestamp"
    
    // Hardcoded fallback URLs
    private let fallbackURLs: [String: String] = [
        "segovia-capital": "https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf",
        "cuellar": "https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf",
        "el-espinar": "https://cofsegovia.com/wp-content/uploads/2025/01/Guardias-EL-ESPINAR_2025.pdf",
        "segovia-rural": "https://cofsegovia.com/wp-content/uploads/2025/06/SERVICIOS-DE-URGENCIA-RURALES-2025.pdf"
    ]
    
    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10.0
        config.timeoutIntervalForResource = 10.0
        urlSession = URLSession(configuration: config)
    }
    
    // MARK: - URL Validation
    
    /// Result of URL validation
    enum URLValidationResult {
        case valid(URL)
        case invalid(URL, Int) // URL and status code
        case error(URL, String) // URL and error message
    }
    
    /// Validate a URL with a HEAD request
    /// Returns validation result and caches it in memory
    func validateURL(_ url: URL, useCache: Bool = true) async -> URLValidationResult {
        // Check in-memory cache first
        if useCache, let cached = validationCache[url.absoluteString] {
            DebugConfig.debugPrint("✅ PDFURLRepository: Using cached validation for \(url.absoluteString)")
            return cached
        }
        
        do {
            DebugConfig.debugPrint("🔍 PDFURLRepository: Validating URL with HEAD request: \(url.absoluteString)")
            
            var request = URLRequest(url: url)
            request.httpMethod = "HEAD"
            request.addValue("FarmaciasDeGuardia-iOS/1.0", forHTTPHeaderField: "User-Agent")
            
            let (_, response) = try await urlSession.data(for: request)
            
            guard let httpResponse = response as? HTTPURLResponse else {
                let result = URLValidationResult.error(url, "No HTTP response")
                validationCache[url.absoluteString] = result
                return result
            }
            
            let result: URLValidationResult
            if httpResponse.statusCode >= 200 && httpResponse.statusCode < 300 {
                DebugConfig.debugPrint("✅ PDFURLRepository: URL is valid (\(httpResponse.statusCode))")
                result = .valid(url)
            } else {
                DebugConfig.debugPrint("❌ PDFURLRepository: URL returned \(httpResponse.statusCode)")
                result = .invalid(url, httpResponse.statusCode)
            }
            
            // Cache the result
            validationCache[url.absoluteString] = result
            return result
            
        } catch {
            DebugConfig.debugPrint("❌ PDFURLRepository: Error validating URL: \(error.localizedDescription)")
            let result = URLValidationResult.error(url, error.localizedDescription)
            validationCache[url.absoluteString] = result
            return result
        }
    }
    
    // MARK: - URL Persistence
    
    /// Data structure for persisted scraped URLs
    private struct ScrapedURLData: Codable {
        let urls: [String: String]
        let timestamp: TimeInterval
    }
    
    /// Load persisted scraped URLs from UserDefaults
    private func loadPersistedURLs() -> [String: String] {
        guard let data = userDefaults.data(forKey: scrapedURLsKey) else {
            return [:]
        }
        
        do {
            let decoder = JSONDecoder()
            let scrapedData = try decoder.decode(ScrapedURLData.self, from: data)
            DebugConfig.debugPrint("📂 PDFURLRepository: Loaded \(scrapedData.urls.count) persisted URLs")
            return scrapedData.urls
        } catch {
            DebugConfig.debugPrint("❌ Failed to load persisted URLs: \(error)")
            return [:]
        }
    }
    
    /// Persist scraped URLs to UserDefaults
    private func persistURLs(_ urls: [String: String]) {
        do {
            let scrapedData = ScrapedURLData(
                urls: urls,
                timestamp: Date().timeIntervalSince1970
            )
            
            let encoder = JSONEncoder()
            let data = try encoder.encode(scrapedData)
            
            userDefaults.set(data, forKey: scrapedURLsKey)
            userDefaults.set(Date().timeIntervalSince1970, forKey: lastScrapeKey)
            
            DebugConfig.debugPrint("💾 PDFURLRepository: Persisted \(urls.count) URLs to storage")
        } catch {
            DebugConfig.debugPrint("❌ Failed to persist URLs: \(error)")
        }
    }
    
    // MARK: - URL Scraping
    
    /// Scrape fresh URLs from website
    func scrapeURLs() async -> [String: String] {
        DebugConfig.debugPrint("🌐 PDFURLRepository: Starting fresh URL scraping...")
        
        let scrapedData = await PDFURLScrapingService.shared.scrapePDFURLs()
        
        if scrapedData.isEmpty {
            DebugConfig.debugPrint("⚠️ PDFURLRepository: Scraping returned no results")
            return [:]
        }
        
        var urlMap: [String: String] = [:]
        for regionData in scrapedData {
            // Normalize region name to ID
            let regionId = normalizeRegionName(regionData.regionName)
            urlMap[regionId] = regionData.pdfURL.absoluteString
        }
        
        // Persist the scraped URLs
        persistURLs(urlMap)
        
        DebugConfig.debugPrint("✅ PDFURLRepository: Scraped and persisted \(urlMap.count) URLs")
        return urlMap
    }
    
    /// Validate all persisted URLs with HEAD requests
    /// Returns map of valid URLs
    func validatePersistedURLs() async -> [String: String] {
        let persistedURLs = loadPersistedURLs()
        
        if persistedURLs.isEmpty {
            DebugConfig.debugPrint("📭 PDFURLRepository: No persisted URLs to validate")
            return [:]
        }
        
        DebugConfig.debugPrint("🔍 PDFURLRepository: Validating \(persistedURLs.count) persisted URLs...")
        
        var validURLs: [String: String] = [:]
        
        for (regionId, urlString) in persistedURLs {
            guard let url = URL(string: urlString) else {
                DebugConfig.debugPrint("❌ \(regionId): Invalid URL string")
                continue
            }
            
            let result = await validateURL(url, useCache: false)
            switch result {
            case .valid:
                validURLs[regionId] = urlString
                DebugConfig.debugPrint("✅ \(regionId): Valid")
            case .invalid(_, let statusCode):
                DebugConfig.debugPrint("❌ \(regionId): Invalid (\(statusCode))")
            case .error(_, let message):
                DebugConfig.debugPrint("⚠️ \(regionId): Error (\(message))")
            }
        }
        
        DebugConfig.debugPrint("✅ PDFURLRepository: \(validURLs.count)/\(persistedURLs.count) URLs are valid")
        return validURLs
    }
    
    // MARK: - URL Resolution
    
    /// Result of URL resolution with self-healing
    enum URLResolutionResult {
        case success(URL)
        case updated(oldURL: URL, newURL: URL)
        case failed(String)
    }
    
    /// Get the best URL for a region (persisted > fallback)
    func getURL(for region: Region) -> URL {
        let persistedURLs = loadPersistedURLs()
        
        // Try persisted URL first
        if let urlString = persistedURLs[region.id], let url = URL(string: urlString) {
            return url
        }
        
        // Fallback to hardcoded URL
        if let urlString = fallbackURLs[region.id], let url = URL(string: urlString) {
            return url
        }
        
        // Last resort: use region's original pdfURL
        DebugConfig.debugPrint("⚠️ No stored URL found for \(region.name), using original")
        return region.pdfURL
    }
    
    /// Resolve URL with self-healing:
    /// 1. Check persisted URL is valid (HEAD request)
    /// 2. If invalid (404), scrape fresh URLs
    /// 3. Return new URL or fail
    func resolveURLWithHealing(for region: Region) async -> URLResolutionResult {
        // Check if we're online
        if !NetworkMonitor.shared.isOnline {
            let url = getURL(for: region)
            DebugConfig.debugPrint("📡 PDFURLRepository: Offline, using stored URL for \(region.name)")
            return .success(url)
        }
        
        let currentURL = getURL(for: region)
        
        DebugConfig.debugPrint("🔄 PDFURLRepository: Resolving URL for \(region.name) with self-healing")
        
        // Validate current URL
        let validation = await validateURL(currentURL)
        
        switch validation {
        case .valid:
            DebugConfig.debugPrint("✅ PDFURLRepository: Current URL is valid")
            return .success(currentURL)
            
        case .invalid(_, let statusCode):
            if statusCode == 404 {
                DebugConfig.debugPrint("🔄 PDFURLRepository: URL returned 404, attempting self-healing...")
                
                // Scrape fresh URLs
                let freshURLs = await scrapeURLs()
                
                if let newURLString = freshURLs[region.id], let newURL = URL(string: newURLString) {
                    if newURL != currentURL {
                        DebugConfig.debugPrint("✅ PDFURLRepository: Found new URL: \(newURL)")
                        return .updated(oldURL: currentURL, newURL: newURL)
                    } else {
                        DebugConfig.debugPrint("⚠️ PDFURLRepository: Scraped URL is same as old URL")
                        return .failed("No se puede acceder al PDF en este momento. Inténtalo más tarde.")
                    }
                } else {
                    DebugConfig.debugPrint("❌ PDFURLRepository: Could not find new URL for \(region.name)")
                    return .failed("No se puede acceder al PDF en este momento. Inténtalo más tarde.")
                }
            } else {
                DebugConfig.debugPrint("⚠️ PDFURLRepository: URL returned \(statusCode)")
                return .failed("No se puede acceder al PDF (Error \(statusCode))")
            }
            
        case .error(_, let message):
            DebugConfig.debugPrint("❌ PDFURLRepository: Error validating URL: \(message)")
            return .failed("Error de red. Inténtalo más tarde.")
        }
    }
    
    // MARK: - Initialization
    
    /// Initialize repository - scrape and validate URLs
    /// Called during splash screen
    func initializeURLs() async -> Bool {
        DebugConfig.debugPrint("🚀 PDFURLRepository: Initializing URLs...")
        
        // Check if we're online
        if !NetworkMonitor.shared.isOnline {
            DebugConfig.debugPrint("📡 PDFURLRepository: Offline, skipping initialization")
            return true // Success (will use persisted/fallback URLs)
        }
        
        // First, validate persisted URLs
        let validPersistedURLs = await validatePersistedURLs()
        
        // Check if all regions have valid persisted URLs
        let allRegionIds = Set(fallbackURLs.keys)
        if Set(validPersistedURLs.keys).isSuperset(of: allRegionIds) {
            DebugConfig.debugPrint("✅ PDFURLRepository: All persisted URLs are valid, no scraping needed")
            return true
        }
        
        // Otherwise, scrape fresh URLs
        DebugConfig.debugPrint("🌐 PDFURLRepository: Some URLs are invalid, scraping fresh URLs...")
        let scrapedURLs = await scrapeURLs()
        
        if scrapedURLs.isEmpty {
            DebugConfig.debugPrint("⚠️ PDFURLRepository: Scraping failed, will use fallback URLs")
            return false
        }
        
        DebugConfig.debugPrint("✅ PDFURLRepository: Initialization complete")
        return true
    }
    
    // MARK: - Utilities
    
    /// Clear all cached data (for debugging)
    func clearCache() {
        userDefaults.removeObject(forKey: scrapedURLsKey)
        userDefaults.removeObject(forKey: lastScrapeKey)
        validationCache.removeAll()
        DebugConfig.debugPrint("🗑️ PDFURLRepository: Cleared all cached data")
    }
    
    /// Get repository status for debugging
    func getStatus() -> String {
        let persistedURLs = loadPersistedURLs()
        let lastScrape = userDefaults.double(forKey: lastScrapeKey)
        
        let lastScrapeDate: String
        if lastScrape > 0 {
            let date = Date(timeIntervalSince1970: lastScrape)
            let formatter = DateFormatter()
            formatter.dateStyle = .short
            formatter.timeStyle = .short
            lastScrapeDate = formatter.string(from: date)
        } else {
            lastScrapeDate = "Never"
        }
        
        var status = "PDFURLRepository Status:\n"
        status += "Persisted URLs: \(persistedURLs.count)\n"
        status += "Last scrape: \(lastScrapeDate)\n"
        status += "Validation cache: \(validationCache.count) entries\n"
        status += "\n"
        
        for (regionId, url) in persistedURLs {
            let filename = URL(string: url)?.lastPathComponent ?? "unknown"
            status += "\(regionId): \(filename)\n"
        }
        
        return status
    }
    
    /// Normalize region name to lookup key
    /// Handles display names vs storage keys
    private func normalizeRegionName(_ regionName: String) -> String {
        let normalized = regionName.lowercased()
        
        if normalized.contains("espinar") {
            return "el-espinar"
        } else if normalized.contains("capital") {
            return "segovia-capital"
        } else if normalized.contains("cuéllar") || normalized.contains("cuellar") {
            return "cuellar"
        } else if normalized.contains("rural") {
            return "segovia-rural"
        } else {
            // Default: convert to kebab-case
            return normalized
                .replacingOccurrences(of: " ", with: "-")
                .folding(options: .diacriticInsensitive, locale: .current)
        }
    }
}

