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
        case extractedFromPDF(original: String)
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
    /// - Parameter text: Raw PDF text content
    /// - Returns: Validated year detection result
    public func detectYear(from text: String) -> YearDetectionResult {
        DebugConfig.debugPrint("🔍 YearDetectionService: Analyzing PDF text...")

        let currentYear = getCurrentYear()

        // Step 1: Try to extract year from PDF text
        if let extractedYearString = extractYearFromText(text) {
            guard let extractedYear = Int(extractedYearString) else {
                DebugConfig.debugPrint("⚠️ Could not parse extracted year string: '\(extractedYearString)'")
                return fallbackDetection(text: text, currentYear: currentYear)
            }

            DebugConfig.debugPrint("✅ Found year in PDF: \(extractedYear) from '\(extractedYearString)'")

            // Validate extracted year
            let (isValid, warning) = validateYear(extractedYear, currentYear: currentYear)

            if isValid {
                // Step 2: Check if first dates are December - if so, adjust year
                if isFirstDateDecember(in: text) {
                    let adjustedYear = extractedYear - 1
                    DebugConfig.debugPrint("📅 December detected at start of schedule, adjusting year from \(extractedYear) to \(adjustedYear)")

                    return YearDetectionResult(
                        year: adjustedYear,
                        source: .extractedFromPDF(original: extractedYearString),
                        isValid: true,
                        warning: "Found year \(extractedYear) in PDF, but adjusted to \(adjustedYear) due to December dates at start of schedule."
                    )
                }

                return YearDetectionResult(
                    year: extractedYear,
                    source: .extractedFromPDF(original: extractedYearString),
                    isValid: true,
                    warning: warning
                )
            } else {
                DebugConfig.debugPrint("⚠️ Extracted year \(extractedYear) is invalid: \(warning ?? "unknown reason")")
                // Fall through to fallback detection
            }
        }

        // Step 3: Fallback detection if extraction failed or was invalid
        DebugConfig.debugPrint("⚠️ No valid year found in PDF text, using fallback heuristic")
        return fallbackDetection(text: text, currentYear: currentYear)
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

        let range = NSRange(searchText.startIndex..., in: searchText)
        return regex.firstMatch(in: searchText, options: [], range: range) != nil
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

    /// Fallback detection when no year found in PDF
    /// Uses heuristic: December dates likely indicate previous year's schedule
    private func fallbackDetection(text: String, currentYear: Int) -> YearDetectionResult {
        if isFirstDateDecember(in: text) {
            let year = currentYear - 1
            DebugConfig.debugPrint("📅 December detected early in PDF, using year \(year)")

            return YearDetectionResult(
                year: year,
                source: .fallbackDecember,
                isValid: true,
                warning: "No explicit year found in PDF. Inferred \(year) from December dates."
            )
        } else {
            DebugConfig.debugPrint("📅 Using current year as fallback: \(currentYear)")

            return YearDetectionResult(
                year: currentYear,
                source: .fallbackCurrent,
                isValid: true,
                warning: "No explicit year found in PDF. Using current year \(currentYear) as default."
            )
        }
    }
}
