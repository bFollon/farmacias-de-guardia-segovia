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
import OpenTelemetryApi

/// Service for scraping PDF URLs from the stable cofsegovia.com page
/// This prevents PDF URLs from becoming stale by fetching the latest links at app startup
/// Uses URLSession and regex patterns - lightweight and simple!
class PDFURLScrapingService {
    static let shared = PDFURLScrapingService()
    
    private let baseURL = "https://cofsegovia.com/farmacias-de-guardia/"
    private let session: URLSession
    
    /// Cache for scraped PDF URLs by region name
    private var scrapedURLs: [String: URL] = [:]
    
    /// Flag to track if scraping has completed
    private var scrapingCompleted = false
    
    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10.0
        config.timeoutIntervalForResource = 10.0
        self.session = URLSession(configuration: config)
    }
    
    /// Scraped PDF URL data for each region
    struct ScrapedPDFData {
        let regionName: String
        let pdfURL: URL
        let lastUpdated: String?
    }
    
    /// Scrape the cofsegovia.com page to extract current PDF URLs
    /// This runs asynchronously to avoid blocking the main thread
    func scrapePDFURLs() async -> [ScrapedPDFData] {
        // Start performance span
        let span = TelemetryService.shared.startSpan(
            name: "pdf.url.scraping",
            kind: .client  // HTTP client operation
        )
        span.setAttribute(key: "url", value: baseURL)
        span.setAttribute(key: "source", value: "web_scraping")

        let startTime = Date()
        var scrapedData: [ScrapedPDFData] = []
        var scrapingError: Error? = nil

        do {
            DebugConfig.debugPrint("PDFURLScrapingService: Starting PDF URL scraping from \(baseURL)")

            guard let url = URL(string: baseURL) else {
                let error = ScrapingError.networkError("Invalid URL")
                DebugConfig.debugPrint("PDFURLScrapingService: Invalid URL")
                scrapingError = error
                recordMetrics(scrapedData: [], startTime: startTime, error: error)

                span.setAttribute(key: "error_message", value: "Invalid URL")
                span.setAttribute(key: "urls_found", value: "0")
                span.setAttribute(key: "status", value: "failed")
                span.setAttribute(key: "error.type", value: "invalid_argument")
                span.status = .error(description: "Invalid URL")
                span.end()
                return []
            }

            var request = URLRequest(url: url)
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15", forHTTPHeaderField: "User-Agent")

            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse,
                  httpResponse.statusCode == 200 else {
                let statusCode = (response as? HTTPURLResponse)?.statusCode ?? -1
                let error = ScrapingError.networkError("HTTP status \(statusCode)")
                DebugConfig.debugPrint("PDFURLScrapingService: HTTP request failed with status: \(statusCode)")
                scrapingError = error
                recordMetrics(scrapedData: [], startTime: startTime, error: error)

                span.setAttribute(key: "http_status", value: statusCode)
                span.setAttribute(key: "error_message", value: "HTTP \(statusCode)")
                span.setAttribute(key: "urls_found", value: "0")
                span.setAttribute(key: "status", value: "failed")
                span.setAttribute(key: "error.type", value: "unavailable")
                span.status = .error(description: "HTTP \(statusCode)")
                span.end()
                return []
            }

            guard let htmlContent = String(data: data, encoding: .utf8) else {
                let error = ScrapingError.networkError("Failed to decode HTML content")
                DebugConfig.debugPrint("PDFURLScrapingService: Failed to decode HTML content")
                scrapingError = error
                recordMetrics(scrapedData: [], startTime: startTime, error: error)

                span.setAttribute(key: "error_message", value: "Failed to decode HTML")
                span.setAttribute(key: "urls_found", value: "0")
                span.setAttribute(key: "status", value: "failed")
                span.setAttribute(key: "error.type", value: "data_loss")
                span.status = .error(description: "Failed to decode HTML")
                span.end()
                return []
            }

            DebugConfig.debugPrint("PDFURLScrapingService: Successfully fetched HTML content (\(htmlContent.count) chars)")

            // Extract PDF links using regex patterns
            scrapedData = extractPDFDataFromHTML(htmlContent)

            DebugConfig.debugPrint("PDFURLScrapingService: Successfully scraped \(scrapedData.count) PDF URLs")

            // Cache the scraped URLs for later use
            for data in scrapedData {
                scrapedURLs[data.regionName] = data.pdfURL
                DebugConfig.debugPrint("PDFURLScrapingService: Found PDF for \(data.regionName): \(data.pdfURL.absoluteString)")
                if let lastUpdated = data.lastUpdated {
                    DebugConfig.debugPrint("PDFURLScrapingService: Last updated: \(lastUpdated)")
                }
            }

            // Mark scraping as completed
            scrapingCompleted = true
            DebugConfig.debugPrint("PDFURLScrapingService: Scraping completed, \(scrapedURLs.count) URLs cached")

            // Record metrics with the scraped data
            recordMetrics(scrapedData: scrapedData, startTime: startTime, error: nil)

            // Finish span successfully
            span.setAttribute(key: "urls_found", value: "\(scrapedData.count)")
            span.setAttribute(key: "status", value: scrapedData.count == 4 ? "success" : "partial")
            span.status = .ok
            span.end()

            return scrapedData

        } catch {
            DebugConfig.debugPrint("PDFURLScrapingService: Error scraping PDF URLs: \(error)")
            scrapingError = ScrapingError.networkError(error.localizedDescription)
            recordMetrics(scrapedData: [], startTime: startTime, error: scrapingError)

            // Finish span with error
            span.setAttribute(key: "error_message", value: error.localizedDescription)
            span.setAttribute(key: "urls_found", value: "0")
            span.setAttribute(key: "status", value: "failed")
            span.setAttribute(key: "error.type", value: "internal_error")
            span.status = .error(description: error.localizedDescription)
            span.end()

            return []
        }
    }

    /// Record scraping metrics to New Relic
    private func recordMetrics(scrapedData: [ScrapedPDFData], startTime: Date, error: Error?) {
        let duration = Date().timeIntervalSince(startTime)
        ScrapingMetricsService.shared.recordScrapingMetrics(
            scrapedData: scrapedData,
            expectedCount: 4, // We expect 4 regions: Segovia Capital, Cuéllar, El Espinar, Segovia Rural
            duration: duration,
            error: error
        )
    }
    
    /// Extract PDF data from HTML content using regex patterns
    /// Much simpler and lighter than DOM parsing!
    private func extractPDFDataFromHTML(_ htmlContent: String) -> [ScrapedPDFData] {
        var scrapedData: [ScrapedPDFData] = []
        
        do {
            // Use regex to find PDF links: href="([^"]*\.pdf)"
            let pattern = #"href="([^"]*\.pdf)""#
            let regex = try NSRegularExpression(pattern: pattern, options: [.caseInsensitive])
            let matches = regex.matches(in: htmlContent, range: NSRange(htmlContent.startIndex..., in: htmlContent))
            
            DebugConfig.debugPrint("PDFURLScrapingService: Found \(matches.count) PDF links in HTML")
            
            // Extract URLs and remove duplicates
            var uniquePdfURLs: Set<String> = []
            for match in matches {
                if let range = Range(match.range(at: 1), in: htmlContent) {
                    let pdfURL = String(htmlContent[range])
                    uniquePdfURLs.insert(pdfURL)
                }
            }
            
            DebugConfig.debugPrint("PDFURLScrapingService: After removing duplicates: \(uniquePdfURLs.count) unique PDF URLs")
            
            // Process each unique PDF URL
            for pdfURLString in uniquePdfURLs {
                // Convert relative URLs to absolute
                let absoluteURLString: String
                if pdfURLString.hasPrefix("http") {
                    absoluteURLString = pdfURLString
                } else {
                    absoluteURLString = "https://cofsegovia.com\(pdfURLString)"
                }
                
                guard let absoluteURL = URL(string: absoluteURLString) else {
                    DebugConfig.debugPrint("PDFURLScrapingService: Invalid URL: \(absoluteURLString)")
                    continue
                }
                
                // Try to determine the region name from context
                let regionName = determineRegionNameFromURL(absoluteURLString, htmlContent)
                
                if !regionName.isEmpty {
                    scrapedData.append(ScrapedPDFData(
                        regionName: regionName,
                        pdfURL: absoluteURL,
                        lastUpdated: extractLastUpdatedDateFromHTML(htmlContent)
                    ))
                } else {
                    DebugConfig.debugPrint("PDFURLScrapingService: Could not determine region for PDF: \(absoluteURLString)")
                }
            }
            
        } catch {
            DebugConfig.debugPrint("PDFURLScrapingService: Error extracting PDF data from HTML: \(error)")
        }
        
        return scrapedData
    }
    
    /// Determine the region name based on PDF URL and surrounding context
    private func determineRegionNameFromURL(_ pdfURL: String, _ htmlContent: String) -> String {
        let urlLower = pdfURL.lowercased()
        
        // First try to determine from the URL itself
        if urlLower.contains("segovia") && urlLower.contains("capital") {
            return "Segovia Capital"
        } else if urlLower.contains("cuellar") || urlLower.contains("cuéllar") {
            return "Cuéllar"
        } else if urlLower.contains("espinar") {
            return "El Espinar"
        } else if urlLower.contains("rural") {
            return "Segovia Rural"
        }
        
        // If URL doesn't contain region info, look in surrounding context
        if let urlRange = htmlContent.range(of: pdfURL) {
            let contextStart = max(htmlContent.startIndex, htmlContent.index(urlRange.lowerBound, offsetBy: -200))
            let contextEnd = min(htmlContent.endIndex, htmlContent.index(urlRange.upperBound, offsetBy: 200))
            let context = String(htmlContent[contextStart..<contextEnd]).lowercased()
            
            let regionKeywords: [(String, String)] = [
                ("segovia", "Segovia Capital"),
                ("capital", "Segovia Capital"),
                ("cuellar", "Cuéllar"),
                ("cuéllar", "Cuéllar"),
                ("espinar", "El Espinar"),
                ("san rafael", "El Espinar"),
                ("rural", "Segovia Rural")
            ]
            
            for (keyword, regionName) in regionKeywords {
                if context.contains(keyword) {
                    return regionName
                }
            }
        }
        
        return ""
    }
    
    /// Extract the last updated date from the HTML content using regex
    private func extractLastUpdatedDateFromHTML(_ htmlContent: String) -> String? {
        let updatePatterns = [
            "actualización",
            "última actualización",
            "fecha de actualización",
            "updated"
        ]
        
        for pattern in updatePatterns {
            do {
                let regex = try NSRegularExpression(pattern: "\(pattern)[^\\d]*(\\d{1,2}[^\\d]*\\d{4})", options: [.caseInsensitive])
                let matches = regex.matches(in: htmlContent, range: NSRange(htmlContent.startIndex..., in: htmlContent))
                
                if let match = matches.first,
                   let range = Range(match.range(at: 1), in: htmlContent) {
                    return String(htmlContent[range]).trimmingCharacters(in: .whitespacesAndNewlines)
                }
            } catch {
                // Continue with next pattern
            }
        }
        
        return nil
    }
    
    /// Get the scraped URL for a specific region
    /// Returns the scraped URL if available, otherwise returns nil
    func getScrapedURL(for regionName: String) -> URL? {
        return scrapingCompleted ? scrapedURLs[regionName] : nil
    }
    
    /// Check if scraping has completed
    func isScrapingCompleted() -> Bool {
        return scrapingCompleted
    }
    
    /// Print scraped data to console for debugging
    func printScrapedData(_ data: [ScrapedPDFData]) {
        DebugConfig.debugPrint("PDFURLScrapingService: ===== SCRAPED PDF URLS =====")
        if data.isEmpty {
            DebugConfig.debugPrint("PDFURLScrapingService: No PDF URLs found")
        } else {
            for (index, pdfData) in data.enumerated() {
                DebugConfig.debugPrint("PDFURLScrapingService: \(index + 1). \(pdfData.regionName)")
                DebugConfig.debugPrint("PDFURLScrapingService:    URL: \(pdfData.pdfURL.absoluteString)")
                if let lastUpdated = pdfData.lastUpdated {
                    DebugConfig.debugPrint("PDFURLScrapingService:    Last Updated: \(lastUpdated)")
                }
            }
        }
        DebugConfig.debugPrint("PDFURLScrapingService: ============================")
    }
}
