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
        
        // MAJOR OPTIMIZATION: Open PDF once and reuse across all pages
        val reader = PdfReader(pdfFile)
        val pdfDoc = PdfDocument(reader)
        
        return try {
            val pageCount = pdfDoc.numberOfPages
            DebugConfig.debugPrint("📄 Processing $pageCount pages of Segovia Capital PDF...")
            
            // Process each page with the same PDF document instance
            for (pageIndex in 1..pageCount) { // iText uses 1-based indexing
                DebugConfig.debugPrint("\n📃 Processing page $pageIndex of $pageCount")
                
                // OPTIMIZED: Use the shared PDF document instance
                val (dates, dayShiftLines, nightShiftLines) = extractColumnTextFlattenedOptimized(pdfDoc, pageIndex, 3)
                
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
            
            DebugConfig.debugPrint("✅ Successfully parsed ${sortedSchedules.size} schedules for Segovia Capital")
            sortedSchedules
            
        } catch (e: Exception) {
            DebugConfig.debugError("❌ Error parsing Segovia Capital PDF: ${e.message}", e)
            emptyList()
        } finally {
            // CRITICAL: Always close the PDF document once we're completely done
            pdfDoc.close()
        }
    }
    
    /**
     * OPTIMIZED: Extract column text flattened into arrays using shared PDF document
     * This eliminates the repeated opening/closing of PDF documents
     */
    private fun extractColumnTextFlattenedOptimized(pdfDoc: PdfDocument, pageNumber: Int, columnCount: Int): Triple<List<String>, List<String>, List<String>> {
        DebugConfig.debugPrint("🔍 SegoviaCapitalParser: Optimized extraction from page $pageNumber with $columnCount columns")
        
        return try {
            val (pageWidth, pageHeight) = getPageDimensionsOptimized(pdfDoc, pageNumber)
            
            // OPTIMIZATION: Use large column extractions instead of many small ones
            val contentWidth = pageWidth - (2 * 40f) // pageMargin = 40f
            val dateColumnWidth = contentWidth * 0.22f // dateColumnWidthRatio = 0.22f
            val pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2
            
            // Extract text from large column areas in single operations
            val allDates = extractColumnTextOptimizedForParser(pdfDoc, pageNumber, pageHeight,
                40f, dateColumnWidth, 100f, pageHeight - 100f)
            
            val allDayPharmacies = extractColumnTextOptimizedForParser(pdfDoc, pageNumber, pageHeight,
                40f + dateColumnWidth + 5f, pharmacyColumnWidth, 100f, pageHeight - 100f)
            
            val allNightPharmacies = extractColumnTextOptimizedForParser(pdfDoc, pageNumber, pageHeight,
                40f + dateColumnWidth + 5f + pharmacyColumnWidth + 5f, pharmacyColumnWidth, 100f, pageHeight - 100f)
            
            Triple(allDates, allDayPharmacies, allNightPharmacies)
            
        } catch (e: Exception) {
            DebugConfig.debugError("Error in optimized extraction: ${e.message}", e)
            Triple(emptyList(), emptyList(), emptyList())
        }
    }
    
    /**
     * Get page dimensions from an already-open PDF document
     */
    private fun getPageDimensionsOptimized(pdfDoc: PdfDocument, pageNumber: Int): Pair<Float, Float> {
        return try {
            val page = pdfDoc.getPage(pageNumber)
            val pageSize = page.getPageSize()
            Pair(pageSize.width, pageSize.height)
        } catch (e: Exception) {
            DebugConfig.debugError("Error getting page dimensions: ${e.message}", e)
            Pair(595f, 842f) // A4 default
        }
    }
    
    /**
     * Extract text from entire column in one operation using shared PDF document
     * OPTIMIZED: Uses pre-compiled regex to reduce object allocations
     */
    private fun extractColumnTextOptimizedForParser(
        pdfDoc: PdfDocument, 
        pageNumber: Int, 
        pageHeight: Float,
        x: Float, 
        width: Float, 
        startY: Float, 
        endY: Float
    ): List<String> {
        return try {
            val region = com.itextpdf.kernel.geom.Rectangle(x, pageHeight - endY, width, endY - startY)
            val filter = com.itextpdf.kernel.pdf.canvas.parser.filter.TextRegionEventFilter(region)
            val strategy = com.itextpdf.kernel.pdf.canvas.parser.listener.FilteredTextEventListener(
                com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy(), filter)
            val rawText = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(pdfDoc.getPage(pageNumber), strategy)
            
            // Parse the extracted text into meaningful lines using optimized filtering
            rawText
                .split('\n')
                .asSequence()
                .map { it.trim() }
                .filter { line ->
                    line.isNotEmpty() && 
                    line.length > 2 && 
                    !line.matches(SEPARATOR_LINE_REGEX) // Use pre-compiled regex
                }
                .distinctBy { it.lowercase() }
                .toList()
        } catch (e: Exception) {
            DebugConfig.debugError("Error in parser column extraction: ${e.message}", e)
            emptyList()
        }
    }
    
    companion object {
        // OPTIMIZATION: Pre-compiled regex for performance
        private val SEPARATOR_LINE_REGEX by lazy { Regex("^[\\s\\-_=]+$") }
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
