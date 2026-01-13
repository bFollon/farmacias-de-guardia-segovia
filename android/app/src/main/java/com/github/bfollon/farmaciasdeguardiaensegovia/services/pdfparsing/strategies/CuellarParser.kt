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

package com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.strategies

import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyDate
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Pharmacy
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import com.github.bfollon.farmaciasdeguardiaensegovia.services.YearDetectionService
import com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.PDFParsingStrategy
import com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.PDFParsingUtils
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.File
import kotlin.collections.plus

/**
 * Parser implementation for Cuéllar pharmacy schedules.
 * Android equivalent of iOS CuellarParser with enhanced special case handling.
 *
 * Handles both regular format (dd-mmm) and special transition format
 * (e.g., "DOMINGO 31 DE AGOSTO Y LUNES 1 DE SEPTIEMBRE").
 */
class CuellarParser : PDFParsingStrategy {

    /** Current year being processed, incremented when January 1st is found */
    private var startingYear: Int = -1

    override fun getStrategyName(): String = "CuellarParser"

    override fun parseSchedules(
        pdfFile: File,
        pdfUrl: String?
    ): Map<DutyLocation, List<PharmacySchedule>> {
        DebugConfig.debugPrint("\n=== Cuéllar Pharmacy Schedules ===")

        // Open PDF once and reuse across all pages
        val reader = PdfReader(pdfFile)
        val pdfDoc = PdfDocument(reader)

        return try {
            val pageCount = pdfDoc.numberOfPages
            DebugConfig.debugPrint("📄 Processing $pageCount pages of Cuéllar PDF...")

            // Detect year from first page if not already set
            if (startingYear == -1) {
                val firstPageContent = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(1))
                val yearResult = YearDetectionService.detectYear(firstPageContent, pdfUrl)
                startingYear = yearResult.year

                yearResult.warning?.let {
                    DebugConfig.debugPrint("⚠️ Year detection warning: $it")
                }

                DebugConfig.debugPrint("📅 Detected starting year for Cuéllar: ${yearResult.year} (source: ${yearResult.source})")
            }

            // iText uses 1-based indexing
            val (allSchedules, _) = (1..pageCount).fold(Pair(emptyList<PharmacySchedule>(), startingYear)) { (acc, year), pageIndex ->
                DebugConfig.debugPrint("\n📃 Processing page $pageIndex of $pageCount")

                val content = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(pageIndex))
                val lines = content.split('\n')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (lines.isNotEmpty()) {
                    DebugConfig.debugPrint("\n📊 Page content structure:")
                    lines.forEachIndexed { index, line ->
                        DebugConfig.debugPrint("Line $index: '$line'")
                    }
                }

                // Process the table structure for this page
                val (pageSchedules, processedYear) = processPageTable(lines, year)
                Pair(acc + pageSchedules, processedYear)
            }

            // Sort schedules by date efficiently
            val sortedSchedules = allSchedules.sortedWith(dateComparator)

            DebugConfig.debugPrint("✅ Successfully parsed ${sortedSchedules.size} schedules for Cuéllar")
            mapOf(DutyLocation.Companion.fromRegion(Region.Companion.cuellar) to sortedSchedules)

        } catch (e: Exception) {
            DebugConfig.debugError("❌ Error parsing Cuéllar PDF: ${e.message}", e)
            emptyMap()
        } finally {
            pdfDoc.close()
        }
    }

    private fun processPageTable(
        lines: List<String>,
        year: Int
    ): Pair<List<PharmacySchedule>, Int> {
        val (schedules, metadata) = lines
            .fold(
                Pair(
                    emptyList<PharmacySchedule>(),
                    Triple(
                        null as String?,
                        emptyList<String>(),
                        year
                    )
                )
            ) {
                (acc, metadata), line ->
                val (pharmacyKey, dates, transientYear) = metadata
                DebugConfig.debugPrint("\n🔍 Processing line: '$line'")

                val (parsedDates, parsedPharmacy) = when {
                    hasDates(line) -> processCompositeLine(line)

                    hasPharmacy(line) -> {
                        val maybePharmacy = extractPharmacyFromLine(line)
                        Pair(dates, maybePharmacy)
                    }

                    else -> {
                        DebugConfig.debugPrint("⏭️ Skipping unsupported line: [$line]")
                        Pair(dates, pharmacyKey)
                    }
                }

                if (parsedPharmacy != null && parsedDates.isNotEmpty()) {
                    val (schedules, processedYear) = processDateSet(parsedDates, parsedPharmacy, transientYear)
                    Pair(
                        acc + schedules,
                        Triple(
                            null,
                            emptyList(),
                            processedYear
                        )
                    )
                } else Pair(
                    acc,
                    Triple(parsedPharmacy, parsedDates, transientYear)
                )
            }

        return Pair(schedules, metadata.third)
    }

    private fun hasDates(line: String): Boolean = REGULAR_DATE_REGEX.containsMatchIn(line)

    private fun hasPharmacy(line: String): Boolean =
        normalizeWhitespace(line).let { normalizedLine ->
            PHARMACY_INFO.keys.find { pharmacyKey ->
                normalizedLine.contains(pharmacyKey, ignoreCase = true)
            } != null
        }

    private fun processCompositeLine(line: String): Pair<List<String>, String?> {
        val dates = REGULAR_DATE_REGEX.findAll(line).map { it.value }.toList()

        val maybePharmacy = extractPharmacyFromLine(line)

        if (maybePharmacy == null) {
            DebugConfig.debugPrint("Composite line had no pharmacy information: [$line]")
            DebugConfig.debugPrint("Will try to find pharmacy information next line")
        }
        return Pair(dates, maybePharmacy)
    }

    private fun extractPharmacyFromLine(line: String): String? =
        normalizeWhitespace(line).let { normalizedLine ->
            PHARMACY_INFO.keys.find { pharmacyKey ->
                normalizedLine.contains(pharmacyKey, ignoreCase = true)
            }
        }

    /**
     * Normalize whitespace by replacing all types of whitespace characters with single regular spaces
     * This handles NBSP (non-breaking space), tabs, multiple spaces, etc.
     */
    private fun normalizeWhitespace(text: String): String {
        // Replace all Unicode whitespace characters with regular spaces, then collapse multiple spaces
        return text.replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Process a set of dates with a pharmacy to create schedules
     */
    private fun processDateSet(
        dates: List<String>,
        pharmacyKey: String,
        year: Int
    ): Pair<List<PharmacySchedule>, Int> {
        DebugConfig.debugPrint("📋 Processing date set:")
        DebugConfig.debugPrint("📅 Dates: $dates")
        DebugConfig.debugPrint("🏠 Pharmacy: $pharmacyKey")
        DebugConfig.debugPrint("📆 Current year: $year")

        return dates.fold(Pair(emptyList(), year)) { (acc, year), date ->
            val transientYear = if (date.matches(Regex("01[‐-]ene"))) {
                DebugConfig.debugPrint("🎊 New year detected! Now processing year ${year + 1}")
                year + 1
            } else year

            DebugConfig.debugPrint("📆 Processing date: $date (year: $transientYear)")
            val dutyDate = parseDutyDate(date, transientYear)

            dutyDate?.let { dutyDate ->
                val pharmacyInfo = PHARMACY_INFO[pharmacyKey] ?: PharmacyInfo(
                    name = "Farmacia $pharmacyKey",
                    address = "Dirección no disponible",
                    phone = "No disponible"
                )

                val pharmacyInstance = Pharmacy(
                    name = pharmacyInfo.name,
                    address = pharmacyInfo.address,
                    phone = pharmacyInfo.phone,
                    additionalInfo = null
                )

                DebugConfig.debugPrint("💊 Adding schedule for ${pharmacyInstance.name} on ${dutyDate.day}-${dutyDate.month}-${dutyDate.year ?: PDFParsingUtils.getCurrentYear()}")

                Pair(
                    acc + PharmacySchedule(
                        date = dutyDate,
                        shifts = mapOf(
                            DutyTimeSpan.Companion.FullDay to listOf(pharmacyInstance)
                        )
                    ), transientYear
                )
            } ?: Pair(acc, year).also {
                DebugConfig.debugPrint("⚠️ Could not parse duty date for: $date")
            }
        }
    }

    /**
     * Parse a date string like "01-ene" to a DutyDate
     */
    private fun parseDutyDate(dateString: String, year: Int): DutyDate? {
        val match = REGULAR_DATE_REGEX.matchEntire(dateString) ?: return null

        val dayStr = match.groupValues[1]
        val monthStr = match.groupValues[2]

        val day = dayStr.toIntOrNull() ?: return null
        val month = PDFParsingUtils.monthAbbrToNumber(monthStr) ?: return null

        return DutyDate(
            dayOfWeek = PDFParsingUtils.getDayOfWeek(day, month, year),
            day = day,
            month = PDFParsingUtils.getMonthName(month),
            year = year
        )
    }

    /**
     * Data class for pharmacy information
     */
    private data class PharmacyInfo(
        val name: String,
        val address: String,
        val phone: String
    )

    companion object {
        // Pre-compiled regex patterns for performance
        private val REGULAR_DATE_REGEX by lazy {
            Regex("""(\d{1,2})[‐-](\w{3})""")
        }

        // Regex to match all types of whitespace characters (NBSP, regular space, tab, etc.)
        private val WHITESPACE_REGEX by lazy {
            Regex("""[\s\u00A0\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]+""")
        }

        // Pharmacy information lookup table (matching iOS implementation)
        private val PHARMACY_INFO = mapOf(
            "Av C.J. CELA" to PharmacyInfo(
                name = "Farmacia Fernando Redondo",
                address = "Av. Camilo Jose Cela, 46, 40200 Cuéllar, Segovia",
                phone = "No disponible"
            ),
            "Ctra. BAHABON" to PharmacyInfo(
                name = "Farmacia San Andrés",
                address = "Ctra. Bahabón, 9, 40200 Cuéllar, Segovia",
                phone = "921144794"
            ),
            "C/ RESINA" to PharmacyInfo(
                name = "Farmacia Ldo. Fco. Javier Alcaraz García de la Barrera",
                address = "C. Resina, 14, 40200 Cuéllar, Segovia",
                phone = "921144812"
            ),
            "STA. MARINA" to PharmacyInfo(
                name = "Farmacia Ldo. César Cabrerizo Izquierdo",
                address = "Calle Sta. Marina, 5, 40200 Cuéllar, Segovia",
                phone = "921140606"
            )
        )

        // Cached date comparator to avoid lambda creation overhead
        private val dateComparator = Comparator<PharmacySchedule> { first, second ->
            compareSchedulesByDate(first, second)
        }

        /**
         * Compare two pharmacy schedules by date for sorting
         */
        private fun compareSchedulesByDate(first: PharmacySchedule, second: PharmacySchedule): Int {
            val currentYear = PDFParsingUtils.getCurrentYear()

            // Extract year, month, day from dates
            val firstYear = first.date.year ?: currentYear
            val secondYear = second.date.year ?: currentYear

            if (firstYear != secondYear) {
                return firstYear.compareTo(secondYear)
            }

            val firstMonth = PDFParsingUtils.monthToNumber(first.date.month) ?: 0
            val secondMonth = PDFParsingUtils.monthToNumber(second.date.month) ?: 0

            if (firstMonth != secondMonth) {
                return firstMonth.compareTo(secondMonth)
            }

            val firstDay = first.date.day
            val secondDay = second.date.day

            return firstDay.compareTo(secondDay)
        }
    }
}
