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
package com.bfollon.farmaciasdeGuardia.services.pdfparsing.strategies

import com.bfollon.farmaciasdeGuardia.data.model.DutyTimeSpan
import com.bfollon.farmaciasdeGuardia.data.model.PharmacySchedule
import com.bfollon.farmaciasdeGuardia.services.pdfparsing.ColumnBasedPDFParser
import com.bfollon.farmaciasdeGuardia.services.pdfparsing.PDFParsingStrategy
import com.bfollon.farmaciasdeGuardia.services.pdfparsing.PDFParsingUtils
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import org.apache.pdfbox.pdmodel.PDDocument

/**
 * Parser implementation for Cuéllar pharmacy schedules.
 * Android equivalent of iOS CuellarParser.
 */
class CuellarParser : ColumnBasedPDFParser(), PDFParsingStrategy {
    
    override fun getStrategyName(): String = "CuellarParser"
    
    override fun parseSchedules(pdf: PDDocument): List<PharmacySchedule> {
        val allSchedules = mutableListOf<PharmacySchedule>()
        
        try {
            // Process each page
            for (pageIndex in 0 until pdf.numberOfPages) {
                val page = pdf.getPage(pageIndex)
                
                DebugConfig.debugPrint("📄 Processing Cuéllar page ${pageIndex + 1} of ${pdf.numberOfPages}")
                
                // Cuéllar typically uses a 2-column layout (Date, Pharmacy)
                val (dates, pharmacyLines, _) = extractColumnTextFlattened(page, 2)
                
                // Convert pharmacy lines to Pharmacy objects
                val pharmacies = PDFParsingUtils.parsePharmacies(pharmacyLines)
                
                // Parse dates
                val parsedDates = dates.mapNotNull { dateString ->
                    PDFParsingUtils.parseDate(dateString)
                }
                
                DebugConfig.debugPrint("📄 Cuéllar data - parsedDates: ${parsedDates.size}, pharmacies: ${pharmacies.size}")
                
                // Create schedules for valid dates (full-day shifts)
                val minSize = minOf(parsedDates.size, pharmacies.size)
                for (index in 0 until minSize) {
                    val date = parsedDates[index]
                    val pharmacy = pharmacies[index]
                    
                    allSchedules.add(
                        PharmacySchedule(
                            date = date.toDate(),
                            shifts = mapOf(
                                DutyTimeSpan.FullDay to listOf(pharmacy)
                            )
                        )
                    )
                }
            }
            
            DebugConfig.debugPrint("✅ Successfully parsed ${allSchedules.size} schedules for Cuéllar")
            return allSchedules.sortedWith { first, second ->
                compareSchedulesByDate(first, second)
            }
            
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ Error parsing Cuéllar PDF: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }
    
    private fun compareSchedulesByDate(first: PharmacySchedule, second: PharmacySchedule): Int {
        val currentYear = PDFParsingUtils.getCurrentYear()
        
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
