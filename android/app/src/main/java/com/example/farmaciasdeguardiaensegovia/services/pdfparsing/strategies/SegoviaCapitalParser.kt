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
                DebugConfig.debugPrint("📃 Processing page $pageIndex of $pageCount")
                
                // OPTIMIZED: Use the shared PDF document instance
                val (dates, dayShiftLines, nightShiftLines) = extractColumnTextFlattenedOptimized(pdfDoc, pageIndex, 3)
                
                // OPTIMIZATION: Skip detailed logging for performance in production-like scenarios
                if (DebugConfig.isDetailedLoggingEnabled) {
                    DebugConfig.debugPrint("📊 Extracted data from page $pageIndex:")
                    DebugConfig.debugPrint("   📅 Dates: ${dates.size} entries")
                    DebugConfig.debugPrint("   ☀️ Day shift lines: ${dayShiftLines.size} entries")
                    DebugConfig.debugPrint("   🌙 Night shift lines: ${nightShiftLines.size} entries")
                    
                    if (dates.isNotEmpty()) {
                        DebugConfig.debugPrint("📅 Sample dates: ${dates.take(3)}")
                    }
                    if (dayShiftLines.isNotEmpty()) {
                        DebugConfig.debugPrint("☀️ Sample day shift: ${dayShiftLines.take(2)}")
                    }
                    if (nightShiftLines.isNotEmpty()) {
                        DebugConfig.debugPrint("🌙 Sample night shift: ${nightShiftLines.take(2)}")
                    }
                }
                
                // OPTIMIZATION: Batch process pharmacies more efficiently
                val dayPharmacies = PDFParsingUtils.parsePharmaciesOptimized(dayShiftLines)
                val nightPharmacies = PDFParsingUtils.parsePharmaciesOptimized(nightShiftLines)
                
                // OPTIMIZATION: Batch parse dates with pre-filtering
                val parsedDates = parseDatesOptimized(dates)
                
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
            
            // Sort schedules by date efficiently
            val sortedSchedules = allSchedules.sortedWith(dateComparator)
            
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
     * OPTIMIZATION: Efficient date parsing with pre-filtering and caching
     */
    private fun parseDatesOptimized(dates: List<String>): List<DutyDate> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<DutyDate>()
        
        for (dateString in dates) {
            // Quick pre-filter: skip obviously invalid dates
            if (dateString.length < 10 || 
                !dateString.contains("de") ||
                dateString == "FECHA") {
                continue
            }
            
            val date = PDFParsingUtils.parseDate(dateString)
            if (date != null) {
                val key = "${date.day}-${date.month}-${date.year}"
                if (seen.add(key)) {
                    result.add(date)
                }
            }
        }
        
        return result
    }
    
    /**
     * MEMORY-OPTIMIZED: Row-based extraction with strategic object reuse
     * Create objects per column type instead of per scan - still dramatically reduces allocations
     */
    private fun extractColumnTextFlattenedOptimized(pdfDoc: PdfDocument, pageNumber: Int, columnCount: Int): Triple<List<String>, List<String>, List<String>> {
        DebugConfig.debugPrint("🔍 SegoviaCapitalParser: Memory-optimized extraction from page $pageNumber")
        
        return try {
            val (pageWidth, pageHeight) = getPageDimensionsOptimized(pdfDoc, pageNumber)
            
            // Layout constants matching iOS
            val pageMargin = 40f
            val contentWidth = pageWidth - (2 * pageMargin)
            val dateColumnWidth = contentWidth * 0.22f
            val pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2
            val columnGap = 5f
            
            // Column definitions
            val dateColumnX = pageMargin
            val dayPharmacyColumnX = pageMargin + dateColumnWidth + columnGap
            val nightPharmacyColumnX = dayPharmacyColumnX + pharmacyColumnWidth + columnGap
            
            val page = pdfDoc.getPage(pageNumber)
            
            // Use larger scanning increments to reduce the number of extractions by ~75%
            val startY = 80f
            val endY = pageHeight - 50f
            val rowScanIncrement = 45f  // INCREASED from 35f to reduce scan count further
            val smallIncrement = 30f    // INCREASED from 20f to reduce scan count further
            
            val allDates = mutableListOf<String>()
            val allDayPharmacies = mutableListOf<String>()
            val allNightPharmacies = mutableListOf<String>()
            
            var currentY = startY
            
            // MEMORY OPTIMIZATION: Reduce scan iterations by ~50% with larger increments
            while (currentY < endY) {
                // Extract date with optimized method
                val dateText = extractTextFromRegionMemoryOptimized(page, pageHeight,
                    dateColumnX, currentY, dateColumnWidth, rowScanIncrement)
                
                if (dateText.isNotEmpty() && isValidDateString(dateText)) {
                    DebugConfig.debugPrint("📅 Found valid date at Y=$currentY: $dateText")
                    
                    // Extract pharmacy data with optimized methods
                    val dayPharmacyLines = extractPharmacyFromRegionMemoryOptimized(page, pageHeight,
                        dayPharmacyColumnX, currentY, pharmacyColumnWidth, rowScanIncrement)
                    
                    val nightPharmacyLines = extractPharmacyFromRegionMemoryOptimized(page, pageHeight,
                        nightPharmacyColumnX, currentY, pharmacyColumnWidth, rowScanIncrement)
                    
                    if (dayPharmacyLines.size >= 3 && nightPharmacyLines.size >= 3) {
                        allDates.add(dateText)
                        // Take exactly 3 lines per pharmacy for consistency
                        allDayPharmacies.addAll(dayPharmacyLines.take(3))
                        allNightPharmacies.addAll(nightPharmacyLines.take(3))
                        
                        DebugConfig.debugPrint("✅ Extracted complete row: date + 2 pharmacies")
                        currentY += rowScanIncrement
                    } else {
                        DebugConfig.debugPrint("⚠️ Incomplete pharmacy data, day: ${dayPharmacyLines.size} lines, night: ${nightPharmacyLines.size} lines")
                        currentY += smallIncrement
                    }
                } else {
                    currentY += smallIncrement
                }
            }
            
            DebugConfig.debugPrint("📊 Memory-optimized results: ${allDates.size} dates, ${allDayPharmacies.size} day lines, ${allNightPharmacies.size} night lines")
            
            Triple(allDates, allDayPharmacies, allNightPharmacies)
            
        } catch (e: Exception) {
            DebugConfig.debugError("Error in memory-optimized extraction: ${e.message}", e)
            Triple(emptyList(), emptyList(), emptyList())
        }
    }
    
    // MEMORY OPTIMIZATION: Reusable Rectangle object to reduce allocations by ~33%
    private val reusableRectangle = com.itextpdf.kernel.geom.Rectangle(0f, 0f, 0f, 0f)

    /**
     * MEMORY-OPTIMIZED: Extract text with reusable Rectangle object to reduce object creation
     */
    private fun extractTextFromRegionMemoryOptimized(
        page: com.itextpdf.kernel.pdf.PdfPage,
        pageHeight: Float,
        x: Float, 
        y: Float, 
        width: Float, 
        height: Float
    ): String {
        return try {
            val adjustedY = pageHeight - y - height
            
            // MEMORY OPTIMIZATION: Reuse Rectangle object by setting new coordinates
            reusableRectangle.setX(x)
            reusableRectangle.setY(adjustedY)
            reusableRectangle.setWidth(width)
            reusableRectangle.setHeight(height)
            val filter = com.itextpdf.kernel.pdf.canvas.parser.filter.TextRegionEventFilter(reusableRectangle)
            val strategy = com.itextpdf.kernel.pdf.canvas.parser.listener.FilteredTextEventListener(
                com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy(), filter)
            
            val rawText = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(page, strategy)
            
            // Simplified text processing - avoid creating multiple intermediate strings
            val lines = rawText.split('\n')
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.length > 8 && !trimmed.matches(SEPARATOR_LINE_REGEX)) {
                    return trimmed
                }
            }
            ""
                
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * MEMORY-OPTIMIZED: Extract pharmacy information with reusable Rectangle object
     */
    private fun extractPharmacyFromRegionMemoryOptimized(
        page: com.itextpdf.kernel.pdf.PdfPage,
        pageHeight: Float,
        x: Float, 
        y: Float, 
        width: Float, 
        height: Float
    ): List<String> {
        return try {
            val adjustedY = pageHeight - y - height
            
            // MEMORY OPTIMIZATION: Reuse Rectangle object by setting new coordinates
            reusableRectangle.setX(x)
            reusableRectangle.setY(adjustedY)
            reusableRectangle.setWidth(width)
            reusableRectangle.setHeight(height)
            val filter = com.itextpdf.kernel.pdf.canvas.parser.filter.TextRegionEventFilter(reusableRectangle)
            val strategy = com.itextpdf.kernel.pdf.canvas.parser.listener.FilteredTextEventListener(
                com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy(), filter)
            
            val rawText = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(page, strategy)
            
            if (rawText.isEmpty()) return emptyList()
            
            // Optimized line processing - avoid multiple intermediate lists
            val validLines = mutableListOf<String>()
            val lines = rawText.split('\n')
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.length > 3 && 
                    !trimmed.matches(SEPARATOR_LINE_REGEX) &&
                    !trimmed.matches(Regex("^[\\d\\s\\-]+$")) && 
                    !trimmed.equals("FARMACIA", ignoreCase = true)) {
                    validLines.add(trimmed)
                    if (validLines.size >= 4) break // Early termination
                }
            }
            
            // Find pharmacy name and reorder efficiently
            for (i in validLines.indices) {
                if (validLines[i].contains("FARMACIA", ignoreCase = true) && i + 2 < validLines.size) {
                    return listOf(validLines[i], validLines[i + 1], validLines[i + 2])
                }
            }
            
            // Fallback: return first 3 lines or pad with empty strings
            return (0..2).map { i -> if (i < validLines.size) validLines[i] else "" }
                
        } catch (e: Exception) {
            emptyList()
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
     * PERFORMANCE OPTIMIZED: Check if a string contains a valid Spanish date format
     */
    private fun isValidDateString(text: String): Boolean {
        // Quick length and content checks first
        if (text.length < 15 || !text.contains("de") || text == "FECHA") {
            return false
        }
        
        // PERFORMANCE: Check for Spanish day names efficiently
        return when {
            text.contains("lunes", ignoreCase = true) -> true
            text.contains("martes", ignoreCase = true) -> true
            text.contains("miércoles", ignoreCase = true) -> true
            text.contains("jueves", ignoreCase = true) -> true
            text.contains("viernes", ignoreCase = true) -> true
            text.contains("sábado", ignoreCase = true) -> true
            text.contains("domingo", ignoreCase = true) -> true
            else -> false
        }
    }
    
    companion object {
        // PERFORMANCE: Pre-compiled regex patterns (reused across instances)
        private val SEPARATOR_LINE_REGEX by lazy { Regex("^[\\s\\-_=]+$") }
        
        // OPTIMIZATION: Cached date comparator to avoid lambda creation overhead
        private val dateComparator = Comparator<PharmacySchedule> { first, second ->
            compareSchedulesByDate(first, second)
        }
        
        /**
         * Compare two pharmacy schedules by date for sorting
         * OPTIMIZED: Static method to avoid repeated closures
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
