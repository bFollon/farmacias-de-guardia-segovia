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
        data class ExtractedFromURL(val original: String) : YearSource()
        data class ExtractedFromPDF(val original: String) : YearSource()
        data class ExtractedFlexible(val original: String) : YearSource()
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
     * Detect year from full PDF text content with multi-layer detection
     * @param text Raw PDF text content
     * @param pdfUrl Optional PDF URL for URL-based detection
     * @return Validated year detection result
     */
    fun detectYear(text: String, pdfUrl: String? = null): YearDetectionResult {
        DebugConfig.debugPrint("🔍 YearDetectionService: Multi-layer detection started")
        DebugConfig.debugPrint("📋 PDF URL: ${pdfUrl ?: "none"}")

        val currentYear = getCurrentYear()

        // STEP 1: Detect year using multi-layer approach
        val detectedYear = detectYearFromSources(text, pdfUrl, currentYear)
            ?: return createInvalidResult("No year could be detected from any source")

        // STEP 2: Apply December adjustment to detected year (for ALL cases)
        return applyDecemberAdjustment(
            detectedYear.year,
            detectedYear.originalString,
            text,
            detectedYear.source
        )
    }

    /**
     * Detect year from multiple sources (URL, text, fallback)
     * Returns the detected year without December adjustment
     */
    private fun detectYearFromSources(text: String, pdfUrl: String?, currentYear: Int): DetectedYearInfo? {
        // LAYER 1: Check PDF URL first
        DebugConfig.debugPrint("\n[LAYER 1] Checking PDF URL...")
        if (pdfUrl != null) {
            extractYearFromURL(pdfUrl)?.let { yearString ->
                val year = yearString.toIntOrNull()
                if (year != null && validateYear(year, currentYear).first) {
                    DebugConfig.debugPrint("✅ Found year in URL: $year")
                    return DetectedYearInfo(year, yearString, YearDetectionResult.YearSource.ExtractedFromURL(yearString))
                }
            }
            DebugConfig.debugPrint("❌ No year found in URL")
        }

        // LAYER 2: Try standard text extraction
        DebugConfig.debugPrint("\n[LAYER 2] Checking standard text pattern...")
        extractYearFromText(text)?.let { yearString ->
            val year = yearString.toIntOrNull()
            if (year != null && validateYear(year, currentYear).first) {
                DebugConfig.debugPrint("✅ Found year in text: $year")
                return DetectedYearInfo(year, yearString, YearDetectionResult.YearSource.ExtractedFromPDF(yearString))
            }
        }
        DebugConfig.debugPrint("❌ No year found with standard pattern")

        // LAYER 3: Try flexible pattern
        DebugConfig.debugPrint("\n[LAYER 3] Checking flexible text pattern...")
        extractYearFlexible(text)?.let { yearString ->
            val year = yearString.toIntOrNull()
            if (year != null && validateYear(year, currentYear).first) {
                DebugConfig.debugPrint("✅ Found year with flexible pattern: $year")
                return DetectedYearInfo(year, yearString, YearDetectionResult.YearSource.ExtractedFlexible(yearString))
            }
        }
        DebugConfig.debugPrint("❌ No year found with flexible pattern")

        // LAYER 4: Fallback to current year
        DebugConfig.debugPrint("\n[LAYER 4] Using current year as fallback...")
        return DetectedYearInfo(currentYear, currentYear.toString(), YearDetectionResult.YearSource.FallbackCurrent)
    }

    /**
     * Helper class to hold detected year information
     */
    private data class DetectedYearInfo(
        val year: Int,
        val originalString: String,
        val source: YearDetectionResult.YearSource
    )

    /**
     * Create an invalid result
     */
    private fun createInvalidResult(reason: String): YearDetectionResult {
        return YearDetectionResult(
            year = getCurrentYear(),
            source = YearDetectionResult.YearSource.Invalid(reason),
            isValid = false,
            warning = reason
        )
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
        val decemberPattern = "\\b\\d{1,2}[‐-]dic\\b".toRegex(RegexOption.IGNORE_CASE)

        DebugConfig.debugPrint("🔎 Checking for December dates in first 500 chars...")
        DebugConfig.debugPrint("📝 Search text preview: ${searchText.take(200)}")
        val hasDecember = decemberPattern.containsMatchIn(searchText)
        DebugConfig.debugPrint("📅 December pattern match: $hasDecember")

        return hasDecember
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
     * Extract year from PDF URL using right-to-left priority
     * Finds all 4-digit years in URL and returns the rightmost valid one
     * Example: /2026/01/RURALES-2025.pdf -> finds [2026, 2025], returns 2025 (rightmost)
     */
    private fun extractYearFromURL(url: String): String? {
        // Find all 4-digit year patterns in URL
        val yearPattern = """(\d{4})""".toRegex()
        val allYears = yearPattern.findAll(url)
            .map { it.value }
            .toList()

        if (allYears.isEmpty()) {
            return null
        }

        // Check years right-to-left (filename year has priority over path year)
        allYears.reversed().forEach { year ->
            if (isYearInValidRange(year)) {
                DebugConfig.debugPrint("🔗 Found year in URL: $year (from right-to-left scan)")
                return year
            }
        }

        return null
    }

    /**
     * Extract year using flexible pattern for malformed text
     * Matches patterns like "2.025", "2-0-2-5", "2 0 2 5"
     */
    private fun extractYearFlexible(text: String): String? {
        // Pattern: 2 + optional separator + 0 + optional separator + [2-3] + optional separator + digit
        val flexiblePattern = """2\D?0\D?([2-3])\D?(\d)""".toRegex()

        flexiblePattern.find(text)?.let { match ->
            val decade = match.groups[1]?.value ?: return null
            val year = match.groups[2]?.value ?: return null
            val reconstructed = "20$decade$year"

            return if (isYearInValidRange(reconstructed)) reconstructed else null
        }

        return null
    }

    /**
     * Check if year string is in valid range (2020-2039)
     */
    private fun isYearInValidRange(yearString: String): Boolean {
        val year = yearString.toIntOrNull() ?: return false
        return year in 2020..2039
    }

    /**
     * Apply December adjustment logic if needed
     * If first dates are December, the PDF likely shows end-of-year schedules
     */
    private fun applyDecemberAdjustment(
        year: Int,
        originalString: String,
        text: String,
        source: YearDetectionResult.YearSource
    ): YearDetectionResult {
        if (isFirstDateDecember(text)) {
            val adjustedYear = year - 1
            DebugConfig.debugPrint("📅 December detected at start of schedule, adjusting year from $year to $adjustedYear")

            // Update source to FallbackDecember when adjustment is applied
            val adjustedSource = when (source) {
                is YearDetectionResult.YearSource.FallbackCurrent -> YearDetectionResult.YearSource.FallbackDecember
                else -> source
            }

            return YearDetectionResult(
                year = adjustedYear,
                source = adjustedSource,
                isValid = true,
                warning = "Found year $year, but adjusted to $adjustedYear due to December dates at start of schedule."
            )
        }

        return YearDetectionResult(year = year, source = source, isValid = true)
    }
}
