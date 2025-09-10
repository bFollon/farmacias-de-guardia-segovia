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

package com.example.farmaciasdeguardiaensegovia.services.pdfparsing.strategies

import com.example.farmaciasdeguardiaensegovia.data.DutyDate
import com.example.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.example.farmaciasdeguardiaensegovia.data.Pharmacy
import com.example.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.example.farmaciasdeguardiaensegovia.services.DebugConfig
import com.example.farmaciasdeguardiaensegovia.services.pdfparsing.PDFParsingStrategy
import com.example.farmaciasdeguardiaensegovia.services.pdfparsing.PDFParsingUtils
import com.example.farmaciasdeguardiaensegovia.services.pdfparsing.strategies.ElEspinarParser.PharmacyInfo
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.File

/**
 * Parser implementation for Cuéllar pharmacy schedules.
 * Android equivalent of iOS CuellarParser with enhanced special case handling.
 * 
 * Handles both regular format (dd-mmm) and special transition format 
 * (e.g., "DOMINGO 31 DE AGOSTO Y LUNES 1 DE SEPTIEMBRE").
 */
class CuellarParser : PDFParsingStrategy {
    
    /** Current year being processed, incremented when January 1st is found */
    private var currentYear = 2024
    
    override fun getStrategyName(): String = "CuellarParser"
    
    override fun parseSchedules(pdfFile: File): List<PharmacySchedule> {
        val allSchedules = mutableListOf<PharmacySchedule>()
        
        DebugConfig.debugPrint("\n=== Cuéllar Pharmacy Schedules ===")
        
        // Open PDF once and reuse across all pages
        val reader = PdfReader(pdfFile)
        val pdfDoc = PdfDocument(reader)
        
        return try {
            val pageCount = pdfDoc.numberOfPages
            DebugConfig.debugPrint("📄 Processing $pageCount pages of Cuéllar PDF...")
            
            // Process each page
            for (pageIndex in 1..pageCount) { // iText uses 1-based indexing
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
                val pageSchedules = processPageTable(lines)
                allSchedules.addAll(pageSchedules)
            }
            
            // Sort schedules by date efficiently
            val sortedSchedules = allSchedules.sortedWith(dateComparator)
            
            DebugConfig.debugPrint("✅ Successfully parsed ${sortedSchedules.size} schedules for Cuéllar")
            sortedSchedules
            
        } catch (e: Exception) {
            DebugConfig.debugError("❌ Error parsing Cuéllar PDF: ${e.message}", e)
            emptyList()
        } finally {
            pdfDoc.close()
        }
    }

    private fun processPageTable(lines: List<String>): List<PharmacySchedule> {
        val (schedules, _, _) = lines.fold(Triple(emptyList<PharmacySchedule>(), null as String?, emptyList<String>())) { (acc, pharmacyKey, dates), line ->
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

            if(parsedPharmacy != null && parsedDates.isNotEmpty()) {
                Triple(acc + processDateSet(parsedDates, parsedPharmacy), null, emptyList())
            } else Triple(acc, parsedPharmacy, parsedDates)
        }

        return schedules
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

        if(maybePharmacy == null) {
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
     * Check if line contains special transition format (e.g., "DE AGOSTO", "DE SEPTIEMBRE")
     */
    private fun containsSpecialTransition(line: String): Boolean {
        return line.contains("DE AGOSTO", ignoreCase = true) || 
               line.contains("DE SEPTIEMBRE", ignoreCase = true) ||
               line.contains("de Septiembre")
    }
    
    /**
     * Check if line contains regular date format (dd-mmm)
     */
    private fun containsRegularDates(line: String): Boolean {
        return REGULAR_DATE_REGEX.containsMatchIn(line)
    }
    
    /**
     * Check if line is a month indicator (e.g., "ENE", "FEB", "MAR")
     */
    private fun isMonthIndicatorLine(line: String): Boolean {
        return MONTH_INDICATOR_REGEX.matches(line)
    }
    
    /**
     * Parse special transition format lines like:
     * "DOMINGO 31 DE AGOSTO Y LUNES 1 DE SEPTIEMBRE  STA. MARINA"
     */
    private fun parseSpecialTransitionLine(line: String): Pair<List<String>, String>? {
        DebugConfig.debugPrint("\n🔄 Parsing special format line: '$line'")
        
        val dates = mutableListOf<String>()
        val dateMatches = SPECIAL_TRANSITION_REGEX.findAll(line)
        
        DebugConfig.debugPrint("✅ Found ${dateMatches.count()} special date matches")
        
        for (match in dateMatches) {
            val day = match.groupValues[2].toIntOrNull() ?: continue
            val monthAbbr = when (match.groupValues[3].uppercase()) {
                "AGOSTO" -> "ago"
                "SEPTIEMBRE", "Septiembre" -> "sep"
                else -> continue
            }
            val formattedDate = String.format("%02d-%s", day, monthAbbr)
            dates.add(formattedDate)
            DebugConfig.debugPrint("   - Converted '${match.value}' to '$formattedDate'")
        }
        
        // Extract pharmacy name from end of line
        val pharmacy = extractPharmacyFromEndOfLine(line)
        
        DebugConfig.debugPrint("📅 Special format dates: $dates")
        DebugConfig.debugPrint("🏥 Pharmacy: '$pharmacy'")
        
        return if (dates.isNotEmpty() && pharmacy.isNotEmpty()) {
            Pair(dates, pharmacy)
        } else {
            DebugConfig.debugPrint("❌ Failed to extract dates or pharmacy from special format line")
            null
        }
    }
    
    /**
     * Parse regular format lines like:
     * "30-dic  31-dic  01-ene  02-ene  03-ene  04-ene  05-ene  Av C.J. CELA"
     */
    private fun parseRegularDateLine(line: String): Pair<List<String>, String>? {
        DebugConfig.debugPrint("\n📋 Parsing regular format line: '$line'")
        
        val dates = mutableListOf<String>()
        val dateMatches = REGULAR_DATE_REGEX.findAll(line)
        
        DebugConfig.debugPrint("✅ Found ${dateMatches.count()} regular date matches")
        
        for (match in dateMatches) {
            dates.add(match.value)
            DebugConfig.debugPrint("   - Found date: '${match.value}'")
        }
        
        // Extract pharmacy name from end of line (after the last date)
        val pharmacy = extractPharmacyFromEndOfLine(line)
        
        DebugConfig.debugPrint("📅 Regular dates: $dates")
        DebugConfig.debugPrint("🏥 Pharmacy: '$pharmacy'")
        
        return if (dates.isNotEmpty() && pharmacy.isNotEmpty()) {
            Pair(dates, pharmacy)
        } else {
            DebugConfig.debugPrint("❌ Failed to extract dates or pharmacy from regular format line")
            null
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
     * Extract pharmacy name from the end of a line
     */
    private fun extractPharmacyFromEndOfLine(line: String): String {
        DebugConfig.debugPrint("🔍 Extracting pharmacy from line: '$line'")
        
        // Normalize whitespace in the input line
        val normalizedLine = normalizeWhitespace(line)
        DebugConfig.debugPrint("🔧 Normalized line: '$normalizedLine'")
        
        // Find the pharmacy name that appears in the line (with normalized whitespace comparison)
        val foundPharmacy = PHARMACY_INFO.keys.find { pharmacy ->
            val normalizedPharmacy = normalizeWhitespace(pharmacy.trim())
            val matches = normalizedLine.contains(normalizedPharmacy, ignoreCase = true)
            DebugConfig.debugPrint("🔍 Checking '$normalizedPharmacy' against normalized line: $matches")
            matches
        }?.trim()
        
        DebugConfig.debugPrint("🔍 Found pharmacy match: '$foundPharmacy'")
        return foundPharmacy ?: ""
    }
    
    /**
     * Process a set of dates with a pharmacy to create schedules
     */
    private fun processDateSet(dates: List<String>, pharmacyKey: String): List<PharmacySchedule> {
        DebugConfig.debugPrint("📋 Processing date set:")
        DebugConfig.debugPrint("📅 Dates: $dates")
        DebugConfig.debugPrint("🏠 Pharmacy: $pharmacyKey")
        DebugConfig.debugPrint("📆 Current year: $currentYear")

        return dates.fold(emptyList<PharmacySchedule>()) { acc, date ->
            if (date.matches(Regex("01[‐-]ene"))) {
                currentYear++
                DebugConfig.debugPrint("🎊 New year detected! Now processing year $currentYear")
            }

            DebugConfig.debugPrint("📆 Processing date: $date (year: $currentYear)")
            val dutyDate = parseDutyDate(date, currentYear)

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

                acc + PharmacySchedule(
                    date = dutyDate,
                    shifts = mapOf(
                        DutyTimeSpan.FullDay to listOf(pharmacyInstance)
                    )
                )
            } ?: acc.also {
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
        
        private val SPECIAL_TRANSITION_REGEX by lazy { 
            Regex("""(?:DOMINGO|LUNES|MARTES|MIERCOLES|JUEVES|VIERNES|SABADO)\s+(\d+)\s+DE\s+(AGOSTO|SEPTIEMBRE|Septiembre)""", RegexOption.IGNORE_CASE) 
        }
        
        private val MONTH_INDICATOR_REGEX by lazy { 
            Regex("""^[A-Z]{3}\s*$""") 
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
