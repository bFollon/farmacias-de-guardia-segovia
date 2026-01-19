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

/// Result of year detection from PDF text
public struct YearDetectionResult {
    public let year: Int
    public let source: YearSource
    public let isValid: Bool
    public let warning: String?

    public enum YearSource {
        case extractedFromURL(original: String)
        case extractedFromPDF(original: String)
        case extractedFlexible(original: String)
        case fallbackDecember
        case fallbackCurrent
        case invalid(reason: String)
    }

    public init(year: Int, source: YearSource, isValid: Bool, warning: String? = nil) {
        self.year = year
        self.source = source
        self.isValid = isValid
        self.warning = warning
    }
}

/// Service for detecting and validating years in PDF schedule documents
public class YearDetectionService {

    // Shared singleton instance
    public static let shared = YearDetectionService()

    // Private initializer for singleton
    private init() {}

    /// Detect year from full PDF text content
    /// - Parameters:
    ///   - text: Raw PDF text content
    ///   - pdfUrl: Optional PDF URL for URL-based year detection
    /// - Returns: Validated year detection result
    public func detectYear(from text: String, pdfUrl: String? = nil) -> YearDetectionResult {
        DebugConfig.debugPrint("🔍 YearDetectionService: Multi-layer detection started")
        DebugConfig.debugPrint("📋 PDF URL: \(pdfUrl ?? "none")")

        let currentYear = getCurrentYear()

        // STEP 1: Detect year using multi-layer approach
        guard let detectedYear = detectYearFromSources(text: text, pdfUrl: pdfUrl, currentYear: currentYear) else {
            return createInvalidResult(reason: "No year could be detected from any source")
        }

        // STEP 2: Apply December adjustment to detected year (for ALL cases)
        return applyDecemberAdjustment(
            year: detectedYear.year,
            originalString: detectedYear.originalString,
            text: text,
            source: detectedYear.source
        )
    }

    /// Detect year from multiple sources (URL, text, fallback)
    /// Returns the detected year without December adjustment
    private func detectYearFromSources(text: String, pdfUrl: String?, currentYear: Int) -> DetectedYearInfo? {
        // LAYER 1: Check PDF URL first
        DebugConfig.debugPrint("\n[LAYER 1] Checking PDF URL...")
        if let url = pdfUrl, let yearString = extractYearFromURL(url), let year = Int(yearString) {
            if validateYear(year, currentYear: currentYear).isValid {
                DebugConfig.debugPrint("✅ Found year in URL: \(year)")
                return DetectedYearInfo(year: year, originalString: yearString, source: .extractedFromURL(original: yearString))
            }
        }
        if pdfUrl != nil {
            DebugConfig.debugPrint("❌ No year found in URL")
        }

        // LAYER 2: Try standard text extraction
        DebugConfig.debugPrint("\n[LAYER 2] Checking standard text pattern...")
        if let yearString = extractYearFromText(text), let year = Int(yearString) {
            if validateYear(year, currentYear: currentYear).isValid {
                DebugConfig.debugPrint("✅ Found year in text: \(year)")
                return DetectedYearInfo(year: year, originalString: yearString, source: .extractedFromPDF(original: yearString))
            }
        }
        DebugConfig.debugPrint("❌ No year found with standard pattern")

        // LAYER 3: Try flexible pattern
        DebugConfig.debugPrint("\n[LAYER 3] Checking flexible text pattern...")
        if let yearString = extractYearFlexible(text), let year = Int(yearString) {
            if validateYear(year, currentYear: currentYear).isValid {
                DebugConfig.debugPrint("✅ Found year with flexible pattern: \(year)")
                return DetectedYearInfo(year: year, originalString: yearString, source: .extractedFlexible(original: yearString))
            }
        }
        DebugConfig.debugPrint("❌ No year found with flexible pattern")

        // LAYER 4: Fallback to current year
        DebugConfig.debugPrint("\n[LAYER 4] Using current year as fallback...")
        return DetectedYearInfo(year: currentYear, originalString: String(currentYear), source: .fallbackCurrent)
    }

    /// Helper struct to hold detected year information
    private struct DetectedYearInfo {
        let year: Int
        let originalString: String
        let source: YearDetectionResult.YearSource
    }

    /// Create an invalid result
    private func createInvalidResult(reason: String) -> YearDetectionResult {
        return YearDetectionResult(
            year: getCurrentYear(),
            source: .invalid(reason: reason),
            isValid: false,
            warning: reason
        )
    }

    /// Get current calendar year
    public func getCurrentYear() -> Int {
        Calendar.current.component(.year, from: Date())
    }

    // MARK: - Private Methods

    /// Extract first year from text using regex
    /// Handles both single years (2025) and spans (2024-2025), returning first year
    private func extractYearFromText(_ text: String) -> String? {
        // Pattern: Match 4-digit years (2020-2039)
        // Handles span format like "2024-2025" or "2024 - 2025" (with spaces) by capturing first year
        let pattern = "\\b(20[2-3]\\d)(?:\\s*-\\s*20[2-3]\\d)?\\b"

        guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else {
            DebugConfig.debugPrint("⚠️ Could not compile year regex pattern")
            return nil
        }

        let range = NSRange(text.startIndex..., in: text)
        guard let match = regex.firstMatch(in: text, options: [], range: range),
              let yearRange = Range(match.range(at: 1), in: text) else {
            return nil
        }

        return String(text[yearRange])
    }

    /// Check if first date in text is December
    /// Uses heuristic: if December appears in first 500 characters, likely at start
    private func isFirstDateDecember(in text: String) -> Bool {
        // Search for December date pattern in first portion of text
        let searchText = String(text.prefix(500))

        // Pattern: Match dates like "01-dic", "31-dic", etc.
        let decemberPattern = "\\b\\d{1,2}[‐-]dic\\b"

        guard let regex = try? NSRegularExpression(pattern: decemberPattern, options: .caseInsensitive) else {
            return false
        }

        DebugConfig.debugPrint("🔎 Checking for December dates in first 500 chars...")
        DebugConfig.debugPrint("📝 Search text preview: \(String(searchText.prefix(200)))")

        let range = NSRange(searchText.startIndex..., in: searchText)
        let hasDecember = regex.firstMatch(in: searchText, options: [], range: range) != nil
        DebugConfig.debugPrint("📅 December pattern match: \(hasDecember)")

        return hasDecember
    }

    /// Validate year is within reasonable range
    /// Returns tuple: (isValid, warning message if applicable)
    private func validateYear(_ year: Int, currentYear: Int) -> (isValid: Bool, warning: String?) {
        let difference = abs(year - currentYear)

        if difference <= 2 {
            // Year is valid
            if difference == 2 {
                return (true, "Year \(year) is at the edge of valid range (±2 years from \(currentYear))")
            }
            return (true, nil)
        } else {
            // Year is outside valid range
            return (false, "Year \(year) is outside valid range (±2 years from \(currentYear)). PDF may be outdated or incorrect.")
        }
    }


    /// Extract year from PDF URL using right-to-left priority
    /// Finds all 4-digit years in URL and returns the rightmost valid one
    /// Example: /2026/01/RURALES-2025.pdf -> finds [2026, 2025], returns 2025 (rightmost)
    private func extractYearFromURL(_ url: String) -> String? {
        // Find all 4-digit year patterns in URL
        let yearPattern = "(\\d{4})"
        guard let regex = try? NSRegularExpression(pattern: yearPattern, options: []) else {
            return nil
        }

        let range = NSRange(url.startIndex..., in: url)
        let matches = regex.matches(in: url, options: [], range: range)

        if matches.isEmpty {
            return nil
        }

        // Extract all years and check right-to-left (filename year has priority over path year)
        let allYears = matches.compactMap { match -> String? in
            guard let yearRange = Range(match.range, in: url) else { return nil }
            return String(url[yearRange])
        }

        for year in allYears.reversed() {
            if isYearInValidRange(year) {
                DebugConfig.debugPrint("🔗 Found year in URL: \(year) (from right-to-left scan)")
                return year
            }
        }

        return nil
    }

    /// Extract year using flexible pattern for malformed text
    /// Matches patterns like "2 0 2 5" or "2.0.2.5"
    private func extractYearFlexible(_ text: String) -> String? {
        let flexiblePattern = "2\\D?0\\D?([2-3])\\D?(\\d)"

        guard let regex = try? NSRegularExpression(pattern: flexiblePattern, options: []),
              let match = regex.firstMatch(in: text, options: [], range: NSRange(text.startIndex..., in: text)),
              let decadeRange = Range(match.range(at: 1), in: text),
              let yearRange = Range(match.range(at: 2), in: text) else {
            return nil
        }

        let decade = String(text[decadeRange])
        let year = String(text[yearRange])
        let reconstructed = "20\(decade)\(year)"

        return isYearInValidRange(reconstructed) ? reconstructed : nil
    }

    /// Check if year string is in valid range (±20 years from current year)
    /// Future-proof: dynamically adjusts based on current year
    private func isYearInValidRange(_ yearString: String) -> Bool {
        guard let year = Int(yearString) else { return false }
        let currentYear = getCurrentYear()
        return year >= (currentYear - 20) && year <= (currentYear + 20)
    }

    /// Apply December adjustment if first date is December
    /// Returns adjusted year detection result
    private func applyDecemberAdjustment(
        year: Int,
        originalString: String,
        text: String,
        source: YearDetectionResult.YearSource
    ) -> YearDetectionResult {
        if isFirstDateDecember(in: text) {
            let adjustedYear = year - 1
            DebugConfig.debugPrint("📅 December detected at start of schedule, adjusting year from \(year) to \(adjustedYear)")

            // Update source to fallbackDecember when adjustment is applied
            let adjustedSource: YearDetectionResult.YearSource
            switch source {
            case .fallbackCurrent:
                adjustedSource = .fallbackDecember
            default:
                adjustedSource = source
            }

            return YearDetectionResult(
                year: adjustedYear,
                source: adjustedSource,
                isValid: true,
                warning: "Found year \(year), but adjusted to \(adjustedYear) due to December dates at start of schedule."
            )
        }

        return YearDetectionResult(year: year, source: source, isValid: true)
    }
}
