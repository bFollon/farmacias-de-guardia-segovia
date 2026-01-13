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

package com.github.bfollon.farmaciasdeguardiaensegovia.services

import java.util.Calendar
import kotlin.math.abs

/**
 * Result of year detection from PDF text
 */
data class YearDetectionResult(
    val year: Int,
    val source: YearSource,
    val isValid: Boolean,
    val warning: String? = null
) {
    sealed class YearSource {
        data class ExtractedFromPDF(val original: String) : YearSource()
        object FallbackDecember : YearSource()
        object FallbackCurrent : YearSource()
        data class Invalid(val reason: String) : YearSource()
    }
}

/**
 * Service for detecting and validating years in PDF schedule documents
 */
object YearDetectionService {

    /**
     * Detect year from full PDF text content
     * @param text Raw PDF text content
     * @return Validated year detection result
     */
    fun detectYear(text: String): YearDetectionResult {
        DebugConfig.debugPrint("🔍 YearDetectionService: Analyzing PDF text...")
        DebugConfig.debugPrint("📝 Analyzing text (first 200 chars): ${text.take(200)}...")
        DebugConfig.debugPrint("🔎 Using regex pattern: \\b(20[2-3]\\d)(?:\\s*-\\s*20[2-3]\\d)?\\b")

        val currentYear = getCurrentYear()

        // Step 1: Try to extract year from PDF text
        val extractedYearString = extractYearFromText(text)
        if (extractedYearString != null) {
            val extractedYear = extractedYearString.toIntOrNull()
            if (extractedYear == null) {
                DebugConfig.debugPrint("⚠️ Could not parse extracted year string: '$extractedYearString'")
                return fallbackDetection(text, currentYear)
            }

            DebugConfig.debugPrint("✅ Found year in PDF: $extractedYear from '$extractedYearString'")

            // Validate extracted year
            val (isValid, warning) = validateYear(extractedYear, currentYear)

            if (isValid) {
                // Step 2: Check if first dates are December - if so, adjust year
                if (isFirstDateDecember(text)) {
                    val adjustedYear = extractedYear - 1
                    DebugConfig.debugPrint("📅 December detected at start of schedule, adjusting year from $extractedYear to $adjustedYear")

                    return YearDetectionResult(
                        year = adjustedYear,
                        source = YearDetectionResult.YearSource.ExtractedFromPDF(extractedYearString),
                        isValid = true,
                        warning = "Found year $extractedYear in PDF, but adjusted to $adjustedYear due to December dates at start of schedule."
                    )
                }

                return YearDetectionResult(
                    year = extractedYear,
                    source = YearDetectionResult.YearSource.ExtractedFromPDF(extractedYearString),
                    isValid = true,
                    warning = warning
                )
            } else {
                DebugConfig.debugPrint("⚠️ Extracted year $extractedYear is invalid: ${warning ?: "unknown reason"}")
                // Fall through to fallback detection
            }
        }

        // Step 3: Fallback detection if extraction failed or was invalid
        DebugConfig.debugPrint("⚠️ No valid year found in PDF text, using fallback heuristic")
        return fallbackDetection(text, currentYear)
    }

    /**
     * Get current calendar year
     */
    fun getCurrentYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR)
    }

    // MARK: - Private Methods

    /**
     * Extract first year from text using regex
     * Handles both single years (2025) and spans (2024-2025), returning first year
     */
    private fun extractYearFromText(text: String): String? {
        DebugConfig.debugPrint("🔎 Searching for year pattern in text...")
        // Pattern: Match 4-digit years (2020-2039)
        // Handles span format like "2024-2025" or "2024 - 2025" (with spaces) by capturing first year
        val pattern = "\\b(20[2-3]\\d)(?:\\s*-\\s*20[2-3]\\d)?\\b".toRegex()

        val match = pattern.find(text)
        if (match == null) {
            DebugConfig.debugPrint("❌ No year pattern found in text")
            return null
        } else {
            DebugConfig.debugPrint("✅ Found year match: '${match.value}' -> extracted: '${match.groups[1]?.value}'")
            return match.groups[1]?.value
        }
    }

    /**
     * Check if first date in text is December
     * Uses heuristic: if December appears in first 500 characters, likely at start
     */
    private fun isFirstDateDecember(text: String): Boolean {
        // Search for December date pattern in first portion of text
        val searchText = text.take(500)

        // Pattern: Match dates like "01-dic", "31-dic", etc.
        val decemberPattern = """\\b\\d{1,2}[‐-]dic\\b""".toRegex(RegexOption.IGNORE_CASE)

        return decemberPattern.containsMatchIn(searchText)
    }

    /**
     * Validate year is within reasonable range
     * Returns pair: (isValid, warning message if applicable)
     */
    private fun validateYear(year: Int, currentYear: Int): Pair<Boolean, String?> {
        val difference = abs(year - currentYear)

        return when {
            difference <= 2 -> {
                // Year is valid
                if (difference == 2) {
                    Pair(
                        true,
                        "Year $year is at the edge of valid range (±2 years from $currentYear)"
                    )
                } else {
                    Pair(true, null)
                }
            }
            else -> {
                // Year is outside valid range
                Pair(
                    false,
                    "Year $year is outside valid range (±2 years from $currentYear). PDF may be outdated or incorrect."
                )
            }
        }
    }

    /**
     * Fallback detection when no year found in PDF
     * Uses heuristic: December dates likely indicate previous year's schedule
     */
    private fun fallbackDetection(text: String, currentYear: Int): YearDetectionResult {
        return if (isFirstDateDecember(text)) {
            val year = currentYear - 1
            DebugConfig.debugPrint("📅 December detected early in PDF, using year $year")

            YearDetectionResult(
                year = year,
                source = YearDetectionResult.YearSource.FallbackDecember,
                isValid = true,
                warning = "No explicit year found in PDF. Inferred $year from December dates."
            )
        } else {
            DebugConfig.debugPrint("📅 Using current year as fallback: $currentYear")

            YearDetectionResult(
                year = currentYear,
                source = YearDetectionResult.YearSource.FallbackCurrent,
                isValid = true,
                warning = "No explicit year found in PDF. Using current year $currentYear as default."
            )
        }
    }
}
