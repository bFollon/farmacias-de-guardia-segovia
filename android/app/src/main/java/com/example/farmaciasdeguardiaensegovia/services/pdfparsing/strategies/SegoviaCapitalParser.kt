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

import com.example.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.example.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.example.farmaciasdeguardiaensegovia.services.DebugConfig
import com.example.farmaciasdeguardiaensegovia.services.pdfparsing.ColumnBasedPDFParser
import com.example.farmaciasdeguardiaensegovia.services.pdfparsing.PDFParsingStrategy
import com.example.farmaciasdeguardiaensegovia.services.pdfparsing.PDFParsingUtils
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import java.io.File
import java.util.*

/**
 * Parser implementation for Segovia Capital pharmacy schedules.
 * Android equivalent of iOS SegoviaCapitalParser.
 */
class SegoviaCapitalParser : ColumnBasedPDFParser(), PDFParsingStrategy {
    
    override fun getStrategyName(): String = "SegoviaCapitalParser"
    
    override fun parseSchedules(pdfFile: File): List<PharmacySchedule> {
        val allSchedules = mutableListOf<PharmacySchedule>()
        
        DebugConfig.debugPrint("\n=== Segovia Capital Schedules ===")
        
        try {
            val reader = PdfReader(pdfFile)
            val pdfDoc = PdfDocument(reader)
            val pageCount = pdfDoc.numberOfPages
            
            DebugConfig.debugPrint("📄 Processing $pageCount pages of Segovia Capital PDF...")
            
            // Process each page
            for (pageIndex in 1..pageCount) { // iText uses 1-based indexing
                DebugConfig.debugPrint("\n📃 Processing page $pageIndex of $pageCount")
                
                // Extract column text from the page
                val (dates, dayShiftLines, nightShiftLines) = extractColumnTextFlattened(pdfFile, pageIndex, 3)
                
                DebugConfig.debugPrint("📊 Extracted data from page $pageIndex:")
                DebugConfig.debugPrint("   📅 Dates: ${dates.size} entries")
                DebugConfig.debugPrint("   ☀️ Day shift lines: ${dayShiftLines.size} entries")
                DebugConfig.debugPrint("   🌙 Night shift lines: ${nightShiftLines.size} entries")
                
                if (dates.isNotEmpty()) {
                    DebugConfig.debugPrint("📅 Sample dates: ${dates.take(5)}")
                }
                if (dayShiftLines.isNotEmpty()) {
                    DebugConfig.debugPrint("☀️ Sample day shift: ${dayShiftLines.take(3)}")
                }
                if (nightShiftLines.isNotEmpty()) {
                    DebugConfig.debugPrint("🌙 Sample night shift: ${nightShiftLines.take(3)}")
                }
                
                // Convert pharmacy lines to Pharmacy objects
                DebugConfig.debugPrint("🏥 Parsing pharmacy objects...")
                val dayPharmacies = PDFParsingUtils.parsePharmacies(dayShiftLines)
                val nightPharmacies = PDFParsingUtils.parsePharmacies(nightShiftLines)
                
                DebugConfig.debugPrint("✅ Parsed ${dayPharmacies.size} day pharmacies, ${nightPharmacies.size} night pharmacies")
                
                // Parse dates and remove duplicates while preserving order
                DebugConfig.debugPrint("📅 Parsing dates...")
                val seen = mutableSetOf<String>()
                val parsedDates = dates
                    .mapNotNull { dateString ->
                        val date = PDFParsingUtils.parseDate(dateString)
                        if (date != null) {
                            val key = "${date.day}-${date.month}-${date.year}"
                            if (seen.add(key)) date else null
                        } else {
                            DebugConfig.debugWarn("⚠️ Could not parse date: '$dateString'")
                            null
                        }
                    }
                
                DebugConfig.debugPrint("📊 Array lengths - parsedDates: ${parsedDates.size}, dayPharmacies: ${dayPharmacies.size}, nightPharmacies: ${nightPharmacies.size}")
                DebugConfig.debugPrint("📅 Parsed dates: ${parsedDates.map { "${it.day} ${it.month}" }}")
                DebugConfig.debugPrint("☀️ Day pharmacies: ${dayPharmacies.map { it.name }}")
                DebugConfig.debugPrint("🌙 Night pharmacies: ${nightPharmacies.map { it.name }}")
                
                // Create schedules for valid dates with available pharmacies
                val minSize = minOf(parsedDates.size, dayPharmacies.size, nightPharmacies.size)
                for (index in 0 until minSize) {
                    val date = parsedDates[index]
                    val dayPharmacy = dayPharmacies[index]
                    val nightPharmacy = nightPharmacies[index]
                    
                    allSchedules.add(
                        PharmacySchedule(
                            date = date,
                            shifts = mapOf(
                                DutyTimeSpan.CapitalDay to listOf(dayPharmacy),
                                DutyTimeSpan.CapitalNight to listOf(nightPharmacy)
                            )
                        )
                    )
                }
            }
            
            // Sort schedules by date
            val sortedSchedules = allSchedules.sortedWith { first, second ->
                compareSchedulesByDate(first, second)
            }
            
            pdfDoc.close()
            
            DebugConfig.debugPrint("✅ Successfully parsed ${sortedSchedules.size} schedules for Segovia Capital")
            return sortedSchedules
            
        } catch (e: Exception) {
            DebugConfig.debugError("❌ Error parsing Segovia Capital PDF: ${e.message}", e)
            return emptyList()
        }
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
