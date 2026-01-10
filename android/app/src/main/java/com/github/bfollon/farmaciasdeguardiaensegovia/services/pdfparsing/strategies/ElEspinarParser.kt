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
import com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.PDFParsingStrategy
import com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.PDFParsingUtils
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.File
import kotlin.collections.plus

/**
 * Parser implementation for El Espinar pharmacy schedules.
 * Android equivalent of iOS ElEspinarParser based on Cuéllar implementation pattern.
 *
 * Handles the specific El Espinar PDF format with three pharmacy locations:
 * - AV. HONTANILLA 18
 * - C/ MARQUES PERALES
 * - SAN RAFAEL
 */
class ElEspinarParser : PDFParsingStrategy {

    /** Current year being processed, incremented when January 1st is found */
    private val startingYear = PDFParsingUtils.getCurrentYear() - 1

    override fun getStrategyName(): String = "ElEspinarParser"

    override fun parseSchedules(pdfFile: File): Map<DutyLocation, List<PharmacySchedule>> {

        DebugConfig.debugPrint("\n=== El Espinar Pharmacy Schedules ===")

        // Open PDF once and reuse across all pages
        val reader = PdfReader(pdfFile)
        val pdfDoc = PdfDocument(reader)

        return try {
            val pageCount = pdfDoc.numberOfPages
            DebugConfig.debugPrint("📄 Processing $pageCount pages of El Espinar PDF...")

            // Process each page
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

                // Process the page content using El Espinar-specific logic
                val (pageSchedules, processedYear) = processPageContent(lines, year)
                Pair(acc + pageSchedules, processedYear)
            }

            // Sort schedules by date efficiently
            val sortedSchedules = allSchedules.sortedWith(dateComparator)

            DebugConfig.debugPrint("✅ Successfully parsed ${sortedSchedules.size} schedules for El Espinar")
            mapOf(DutyLocation.Companion.fromRegion(Region.Companion.elEspinar) to sortedSchedules)

        } catch (e: Exception) {
            DebugConfig.debugError("❌ Error parsing El Espinar PDF: ${e.message}", e)
            emptyMap()
        } finally {
            pdfDoc.close()
        }
    }


    private fun processPageContent(
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
            ) { (acc, metadata), line ->
                val (pharmacyKey, dates, transientYear) = metadata
                DebugConfig.debugPrint("🔍 Processing line: '$line'")

                val (parsedPharmacy, parsedDates) = when {
                    hasPharmacy(line) -> {
                        Pair(identifyPharmacyFromLine(line), dates)
                    }

                    hasDates(line) -> {
                        Pair(pharmacyKey, dates + extractDatesFromLine(line))
                    }

                    else -> {
                        DebugConfig.debugPrint("⏭️ Skipping unsupported line: [$line]")
                        Pair(pharmacyKey, dates)
                    }
                }

                if (parsedPharmacy != null && parsedDates.isNotEmpty()) {
                    val (schedules, processedYear) = processDateSet(
                        parsedDates,
                        parsedPharmacy,
                        transientYear
                    )
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

    /**
     * Check if line has dates
     */
    private fun hasDates(line: String): Boolean {
        return DATE_REGEX.containsMatchIn(line)
    }

    /**
     * Extract dates from a line using regex patterns
     */
    private fun extractDatesFromLine(line: String): List<String> {
        return DATE_REGEX.findAll(line).map { it.value }.toList()
    }

    /**
     * Check if line has pharmacy information
     */
    private fun hasPharmacy(line: String): Boolean {
        return identifyPharmacyFromLine(line) != null
    }

    /**
     * Identify pharmacy from line content based on iOS logic
     */
    private fun identifyPharmacyFromLine(line: String): String? {
        return when {
            line.contains("HONTANILLA", ignoreCase = true) -> "AV. HONTANILLA 18"
            line.contains("MARQUES PERALES", ignoreCase = true) -> "C/ MARQUES PERALES"
            line.endsWith("SAN RAFAEL", ignoreCase = true) -> "SAN RAFAEL"
            else -> null
        }
    }

    /**
     * Process a set of dates with a pharmacy (following Cuéllar pattern)
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

        return dates.toSet().fold(Pair(emptyList(), year)) { (acc, year), date ->
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
     * Parse a date string like "01-ene" to a DutyDate (following Cuéllar pattern)
     */
    private fun parseDutyDate(dateString: String, year: Int): DutyDate? {
        val match = DATE_REGEX.matchEntire(dateString) ?: return null

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
     * Data class for pharmacy information (following Cuéllar pattern)
     */
    private data class PharmacyInfo(
        val name: String,
        val address: String,
        val phone: String
    )

    companion object {
        // Pre-compiled regex patterns for performance (following Cuéllar pattern)
        private val DATE_REGEX by lazy {
            Regex("""(\d{1,2})[‐-](\w{3})""")
        }

        // Pharmacy information lookup table (matching iOS implementation exactly)
        private val PHARMACY_INFO = mapOf(
            "AV. HONTANILLA 18" to PharmacyInfo(
                name = "FARMACIA ANA MARÍA APARICIO HERNAN",
                address = "Av. Hontanilla, 18, 40400 El Espinar, Segovia",
                phone = "921 181 011"
            ),
            "C/ MARQUES PERALES" to PharmacyInfo(
                name = "Farmacia Lda M J. Bartolomé Sánchez",
                address = "Calle del, C. Marqués de Perales, 2, 40400, Segovia",
                phone = "921 181 171"
            ),
            "SAN RAFAEL" to PharmacyInfo(
                name = "Farmacia San Rafael",
                address = "Tr.ª Alto del León, 19, 40410 San Rafael, Segovia",
                phone = "921 171 105"
            )
        )

        // Cached date comparator to avoid lambda creation overhead (following Cuéllar pattern)
        private val dateComparator = Comparator<PharmacySchedule> { first, second ->
            compareSchedulesByDate(first, second)
        }

        /**
         * Compare two pharmacy schedules by date for sorting (following Cuéllar pattern)
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
