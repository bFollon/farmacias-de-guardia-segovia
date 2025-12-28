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

/// Service responsible for recording PDF URL scraping errors to Signoz (OpenTelemetry)
/// Only sends error events when scraping fails or returns incomplete results
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

        // Only record errors to Signoz (spans track all operations)
        if !success {
            DebugConfig.debugPrint("⚠️ Scraping issue detected: found \(urlCount)/\(expectedCount) URLs")

            // Get list of regions found and missing
            let foundRegions = scrapedData.map { $0.regionName }
            let expectedRegions = ["Segovia Capital", "Cuéllar", "El Espinar", "Segovia Rural"]
            let missingRegions = expectedRegions.filter { !foundRegions.contains($0) }

            // Record error to Signoz (OpenTelemetry)
            recordError(
                urlCount: urlCount,
                expectedCount: expectedCount,
                missingRegions: missingRegions,
                error: error
            )

            DebugConfig.debugPrint("🚨 Recorded scraping error to Signoz")
        } else {
            DebugConfig.debugPrint("✅ Scraping successful: \(urlCount)/\(expectedCount) URLs (no error sent to Signoz)")
        }
    }

    // MARK: - Private Methods

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

        // Build error attributes for OpenTelemetry
        var errorAttributes: [String: AttributeValue] = [
            "urlCount": AttributeValue.int(urlCount),
            "expectedCount": AttributeValue.int(expectedCount),
            "missingRegionsCount": AttributeValue.int(missingRegions.count)
        ]

        if !missingRegions.isEmpty {
            errorAttributes["missingRegions"] = AttributeValue.string(missingRegions.joined(separator: ", "))
        }

        // Record error to Signoz (OpenTelemetry)
        TelemetryService.shared.recordError(scrapingError, attributes: errorAttributes)

        DebugConfig.debugPrint("🚨 Error recorded: \(scrapingError.localizedDescription)")
        if !missingRegions.isEmpty {
            DebugConfig.debugPrint("🚨 Missing regions: \(missingRegions.joined(separator: ", "))")
        }
    }

    /// Check if metrics should be sent (user has opted in to monitoring)
    private func shouldSendMetrics() -> Bool {
        return MonitoringPreferencesService.shared.hasUserOptedIn()
    }
}
