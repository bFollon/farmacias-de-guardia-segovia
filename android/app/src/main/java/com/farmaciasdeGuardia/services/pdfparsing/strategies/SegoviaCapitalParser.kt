/*
 * Farmacias de Guardia - Segovia
 * Copyright (C) 2024 Bruno Follón
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.farmaciasdeGuardia.services.pdfparsing.strategies

import com.bfollon.farmaciasdeGuardia.data.model.DutyTimeSpan
import com.bfollon.farmaciasdeGuardia.data.model.PharmacySchedule
import com.farmaciasdeGuardia.services.pdfparsing.ColumnBasedPDFParser
import com.farmaciasdeGuardia.services.pdfparsing.PDFParsingStrategy
import com.farmaciasdeGuardia.services.pdfparsing.PDFParsingUtils
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import org.apache.pdfbox.pdmodel.PDDocument
import java.util.*

/**
 * Parser implementation for Segovia Capital pharmacy schedules.
 * Android equivalent of iOS SegoviaCapitalParser.
 */
class SegoviaCapitalParser : ColumnBasedPDFParser(), PDFParsingStrategy {
    
    override fun getStrategyName(): String = "SegoviaCapitalParser"
    
    override fun parseSchedules(pdf: PDDocument): List<PharmacySchedule> {
        val allSchedules = mutableListOf<PharmacySchedule>()
        
        try {
            // Process each page
            for (pageIndex in 0 until pdf.numberOfPages) {
                val page = pdf.getPage(pageIndex)
                
                DebugConfig.debugPrint("📄 Processing page ${pageIndex + 1} of ${pdf.numberOfPages}")
                
                // Extract column text from the page
                val (dates, dayShiftLines, nightShiftLines) = extractColumnTextFlattened(page, 3)
                
                // Convert pharmacy lines to Pharmacy objects
                val dayPharmacies = PDFParsingUtils.parsePharmacies(dayShiftLines)
                val nightPharmacies = PDFParsingUtils.parsePharmacies(nightShiftLines)
                
                // Parse dates and remove duplicates while preserving order
                val seen = mutableSetOf<String>()
                val parsedDates = dates
                    .mapNotNull { dateString ->
                        val date = PDFParsingUtils.parseDate(dateString)
                        if (date != null) {
                            val key = "${date.day}-${date.month}-${date.year}"
                            if (seen.add(key)) date else null
                        } else null
                    }
                
                DebugConfig.debugPrint("📄 Array lengths - parsedDates: ${parsedDates.size}, dayPharmacies: ${dayPharmacies.size}, nightPharmacies: ${nightPharmacies.size}")
                DebugConfig.debugPrint("📄 Parsed dates: ${parsedDates.map { "${it.day} ${it.month}" }}")
                DebugConfig.debugPrint("📄 Day pharmacies: ${dayPharmacies.map { it.name }}")
                DebugConfig.debugPrint("📄 Night pharmacies: ${nightPharmacies.map { it.name }}")
                
                // Create schedules for valid dates with available pharmacies
                val minSize = minOf(parsedDates.size, dayPharmacies.size, nightPharmacies.size)
                for (index in 0 until minSize) {
                    val date = parsedDates[index]
                    val dayPharmacy = dayPharmacies[index]
                    val nightPharmacy = nightPharmacies[index]
                    
                    allSchedules.add(
                        PharmacySchedule(
                            date = date.toDate(),
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
            
            DebugConfig.debugPrint("✅ Successfully parsed ${sortedSchedules.size} schedules for Segovia Capital")
            return sortedSchedules
            
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ Error parsing Segovia Capital PDF: ${e.message}")
            e.printStackTrace()
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
        
        val firstDay = first.date.day ?: 0
        val secondDay = second.date.day ?: 0
        
        return firstDay.compareTo(secondDay)
    }
}
