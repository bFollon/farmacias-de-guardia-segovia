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
import NewRelic

/// Error types for PDF URL scraping failures
enum ScrapingError: Error, LocalizedError {
    case incompleteResults(found: Int, expected: Int)
    case noResults
    case networkError(String)

    var errorDescription: String? {
        switch self {
        case .incompleteResults(let found, let expected):
            return "PDF URL scraping incomplete: found \(found) URLs, expected \(expected)"
        case .noResults:
            return "PDF URL scraping failed: no URLs found"
        case .networkError(let message):
            return "PDF URL scraping network error: \(message)"
        }
    }
}

/// Service responsible for recording PDF URL scraping metrics to New Relic
/// Tracks scraping success/failure rates and alerts on incomplete results
class ScrapingMetricsService {
    static let shared = ScrapingMetricsService()

    private init() {}

    // MARK: - Public Methods

    /// Record scraping metrics after a scraping attempt
    /// - Parameters:
    ///   - scrapedData: Array of scraped PDF data
    ///   - expectedCount: Expected number of URLs (default: 4 for all regions)
    ///   - duration: Time taken to complete scraping in seconds
    ///   - error: Optional error if scraping failed
    func recordScrapingMetrics(
        scrapedData: [PDFURLScrapingService.ScrapedPDFData],
        expectedCount: Int = 4,
        duration: TimeInterval,
        error: Error? = nil
    ) {
        // Check user consent first
        guard shouldSendMetrics() else {
            DebugConfig.debugPrint("⚠️ Skipping scraping metrics - user has not opted in to monitoring")
            return
        }

        let urlCount = scrapedData.count
        let success = urlCount == expectedCount && error == nil

        DebugConfig.debugPrint("📊 Recording scraping metrics: found \(urlCount)/\(expectedCount) URLs, success: \(success)")

        // Get app version info
        let versionInfo = getAppVersionInfo()

        // Get list of regions found and missing
        let foundRegions = scrapedData.map { $0.regionName }
        let expectedRegions = ["Segovia Capital", "Cuéllar", "El Espinar", "Segovia Rural"]
        let missingRegions = expectedRegions.filter { !foundRegions.contains($0) }

        // Record custom event for trending/analytics
        recordCustomEvent(
            urlCount: urlCount,
            expectedCount: expectedCount,
            success: success,
            duration: duration,
            foundRegions: foundRegions,
            missingRegions: missingRegions,
            appVersion: versionInfo.version,
            buildNumber: versionInfo.build,
            error: error
        )

        // Record error to New Relic if scraping was unsuccessful
        if !success {
            recordError(
                urlCount: urlCount,
                expectedCount: expectedCount,
                missingRegions: missingRegions,
                error: error
            )
        }

        DebugConfig.debugPrint("✅ Recorded scraping metrics to New Relic")
    }

    // MARK: - Private Methods

    /// Record a custom event for scraping results
    private func recordCustomEvent(
        urlCount: Int,
        expectedCount: Int,
        success: Bool,
        duration: TimeInterval,
        foundRegions: [String],
        missingRegions: [String],
        appVersion: String,
        buildNumber: String,
        error: Error?
    ) {
        var attributes: [String: Any] = [
            "urlCount": urlCount,
            "expectedCount": expectedCount,
            "success": success,
            "scrapingDuration": duration,
            "appVersion": appVersion,
            "buildNumber": buildNumber,
            "foundRegionsCount": foundRegions.count,
            "missingRegionsCount": missingRegions.count
        ]

        // Add found regions as comma-separated string
        if !foundRegions.isEmpty {
            attributes["foundRegions"] = foundRegions.joined(separator: ", ")
        }

        // Add missing regions if any
        if !missingRegions.isEmpty {
            attributes["missingRegions"] = missingRegions.joined(separator: ", ")
        }

        // Add error message if present
        if let error = error {
            attributes["errorMessage"] = error.localizedDescription
        }

        NewRelic.recordCustomEvent(
            "PDFURLScrapingCompleted",
            attributes: attributes
        )

        DebugConfig.debugPrint("📊 Event: PDFURLScrapingCompleted - \(urlCount)/\(expectedCount) URLs, duration: \(String(format: "%.2f", duration))s")
    }

    /// Record an error when scraping fails or returns incomplete results
    private func recordError(
        urlCount: Int,
        expectedCount: Int,
        missingRegions: [String],
        error: Error?
    ) {
        let scrapingError: Error

        if let existingError = error {
            // Use the provided error
            scrapingError = existingError
        } else if urlCount == 0 {
            // Complete failure - no URLs found
            scrapingError = ScrapingError.noResults
        } else {
            // Partial failure - some URLs missing
            scrapingError = ScrapingError.incompleteResults(found: urlCount, expected: expectedCount)
        }

        // Build error attributes
        var errorAttributes: [String: Any] = [
            "urlCount": urlCount,
            "expectedCount": expectedCount,
            "missingRegionsCount": missingRegions.count
        ]

        if !missingRegions.isEmpty {
            errorAttributes["missingRegions"] = missingRegions.joined(separator: ", ")
        }

        // Record error to New Relic
        NewRelic.recordError(scrapingError, attributes: errorAttributes)

        DebugConfig.debugPrint("🚨 Error recorded: \(scrapingError.localizedDescription)")
        if !missingRegions.isEmpty {
            DebugConfig.debugPrint("🚨 Missing regions: \(missingRegions.joined(separator: ", "))")
        }
    }

    /// Get app version information from Bundle
    private func getAppVersionInfo() -> (version: String, build: String) {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "unknown"
        return (version, build)
    }

    /// Check if metrics should be sent (user has opted in to monitoring)
    private func shouldSendMetrics() -> Bool {
        return MonitoringPreferencesService.shared.hasUserOptedIn()
    }
}
