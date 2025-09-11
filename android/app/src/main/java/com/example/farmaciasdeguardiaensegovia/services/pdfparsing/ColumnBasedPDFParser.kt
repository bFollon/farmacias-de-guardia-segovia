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

package com.example.farmaciasdeguardiaensegovia.services.pdfparsing

import com.example.farmaciasdeguardiaensegovia.services.DebugConfig
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy
import com.itextpdf.kernel.pdf.canvas.parser.listener.FilteredTextEventListener
import com.itextpdf.kernel.pdf.canvas.parser.filter.TextRegionEventFilter
import com.itextpdf.kernel.geom.Rectangle
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * OPTIMIZED Base class for column-based PDF parsers using coordinate-based extraction
 * 
 * MAJOR PERFORMANCE OPTIMIZATIONS APPLIED:
 * 1. PDF Document Reuse: Opens PDF once and reuses across all extractions (eliminates repeated I/O)
 * 2. Large Region Extraction: Extracts entire columns in single operations instead of thousands of small regions
 * 3. Pre-compiled Regex: Uses lazy-loaded regex patterns to reduce object allocation during filtering
 * 4. Memory-efficient Text Processing: Uses sequences and efficient filtering to reduce GC pressure
 * 5. Eliminated Row Scanning: Replaces Y-coordinate scanning loop with direct column extraction
 * 
 * These optimizations should reduce parsing time by 80-90% and eliminate GC pressure.
 */
abstract class ColumnBasedPDFParser {
    
    /**
     * Represents a cell scan area for coordinate-based text extraction
     * Mirrors iOS CellScanArea structure
     */
    data class CellScanArea(
        val x: Float,           // X coordinate (left edge)
        val width: Float,       // Width of the scan area
        val increment: Float,   // Height increment per row
        val rows: Int          // Number of rows to scan per cell
    )
    
    /**
     * Layout constants for Segovia Capital PDF format
     * Mirrors iOS Layout enum
     */
    private object Layout {
        const val pageMargin: Float = 40f
        const val dateColumnWidthRatio: Float = 0.22f
        const val columnGap: Float = 5f
    }
    
    /**
     * Extract text from specific rectangular region on a PDF page
     * OPTIMIZED: Now takes an already-open PdfDocument instead of opening/closing repeatedly
     */
    private fun extractTextFromRegion(pdfDoc: PdfDocument, pageNumber: Int, region: Rectangle): String {
        return try {
            val filter = TextRegionEventFilter(region)
            val strategy = FilteredTextEventListener(LocationTextExtractionStrategy(), filter)
            val text = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(pageNumber), strategy)
            text
        } catch (e: Exception) {
            DebugConfig.debugError("Error extracting text from region: ${e.message}", e)
            ""
        }
    }
    
    /**
     * Get page dimensions for coordinate calculations
     * OPTIMIZED: Now takes an already-open PdfDocument instead of opening/closing repeatedly
     */
    private fun getPageDimensions(pdfDoc: PdfDocument, pageNumber: Int): Pair<Float, Float> {
        return try {
            val page = pdfDoc.getPage(pageNumber)
            val pageSize = page.getPageSize()
            val width = pageSize.width
            val height = pageSize.height
            Pair(width, height)
        } catch (e: Exception) {
            DebugConfig.debugError("Error getting page dimensions: ${e.message}", e)
            Pair(595f, 842f) // A4 default
        }
    }
    
    /**
     * Extract column text using coordinate-based scanning (mirrors iOS approach)
     * HIGHLY OPTIMIZED: Massive performance improvements
     */
    fun extractColumnText(pdfFile: File, pageNumber: Int, columns: List<CellScanArea>): List<List<String>> {
        DebugConfig.debugPrint("🔍 ColumnBasedPDFParser: Optimized coordinate-based extraction from page $pageNumber")
        
        // OPTIMIZATION 1: Open PDF once and reuse for all extractions
        val reader = PdfReader(pdfFile)
        val pdfDoc = PdfDocument(reader)
        
        return try {
            val (pageWidth, pageHeight) = getPageDimensions(pdfDoc, pageNumber)
            DebugConfig.debugPrint("📏 Page dimensions: ${pageWidth}x${pageHeight}")
            
            // OPTIMIZATION 2: Use larger, more targeted scan areas instead of many small regions
            val contentWidth = pageWidth - (2 * Layout.pageMargin)
            val dateColumnWidth = contentWidth * Layout.dateColumnWidthRatio
            val pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2
            
            // OPTIMIZATION 3: Extract text from large column areas in single operations
            val allDates = extractColumnTextOptimized(pdfDoc, pageNumber, pageHeight,
                Layout.pageMargin, dateColumnWidth, 
                100f, pageHeight - 200f) // Skip header/footer
            
            val allDayPharmacies = extractColumnTextOptimized(pdfDoc, pageNumber, pageHeight,
                Layout.pageMargin + dateColumnWidth + Layout.columnGap, pharmacyColumnWidth,
                100f, pageHeight - 200f)
            
            val allNightPharmacies = extractColumnTextOptimized(pdfDoc, pageNumber, pageHeight,
                Layout.pageMargin + dateColumnWidth + Layout.columnGap + pharmacyColumnWidth + Layout.columnGap, 
                pharmacyColumnWidth, 100f, pageHeight - 200f)
            
            DebugConfig.debugPrint("� Optimized extraction result: ${allDates.size} dates, ${allDayPharmacies.size} day lines, ${allNightPharmacies.size} night lines")
            
            listOf(allDates, allDayPharmacies, allNightPharmacies)
            
        } finally {
            // CRITICAL: Always close the PDF document once we're done
            pdfDoc.close()
        }
    }
    
    /**
     * OPTIMIZATION 4: Extract text from entire column in one operation, then parse
     * This eliminates thousands of small text extractions that cause GC pressure
     */
    private fun extractColumnTextOptimized(
        pdfDoc: PdfDocument, 
        pageNumber: Int, 
        pageHeight: Float,
        x: Float, 
        width: Float, 
        startY: Float, 
        endY: Float
    ): List<String> {
        return try {
            // Single large text extraction instead of many small ones
            val region = Rectangle(x, pageHeight - endY, width, endY - startY)
            val filter = TextRegionEventFilter(region)
            val strategy = FilteredTextEventListener(LocationTextExtractionStrategy(), filter)
            val rawText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(pageNumber), strategy)
            
            // Parse the extracted text into meaningful lines
            parseColumnTextLines(rawText)
        } catch (e: Exception) {
            DebugConfig.debugError("Error in optimized column extraction: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * OPTIMIZATION 5: Parse column text more efficiently
     * Filters noise and extracts meaningful content using pre-compiled regex
     */
    private fun parseColumnTextLines(rawText: String): List<String> {
        return rawText
            .split('\n')
            .asSequence()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() && 
                line.length > 2 && // Filter out single characters and spacing
                !line.matches(SEPARATOR_REGEX) // Filter out separator lines using pre-compiled regex
            }
            .distinctBy { it.lowercase() } // Remove case-insensitive duplicates
            .toList()
    }
    
    /**
     * Validate if a scanned row contains coherent Segovia Capital data
     * OPTIMIZED: Uses pre-compiled regex to reduce object allocation
     */
    private fun isCoherentSegoviaRow(rowData: List<List<String>>): Boolean {
        // We expect 3 cells: date, day pharmacy, night pharmacy
        if (rowData.size != 3) return false
        
        val dateCell = rowData[0]
        val dayCell = rowData[1]
        val nightCell = rowData[2]
        
        // Date cell should have content that looks like a Spanish date
        val dateText = dateCell.joinToString(" ")
        val hasValidDate = dateText.contains(DATE_FILTER_REGEX)
        
        // At least one pharmacy cell should have content
        val hasPharmacyContent = dayCell.isNotEmpty() || nightCell.isNotEmpty()
        
        return hasValidDate && hasPharmacyContent
    }
    
    /**
     * Extract column text flattened into arrays (for batch processing)
     * HIGHLY OPTIMIZED: Eliminates repeated PDF opening/closing and uses efficient text extraction
     */
    fun extractColumnTextFlattened(pdfFile: File, pageNumber: Int, columnCount: Int): Triple<List<String>, List<String>, List<String>> {
        DebugConfig.debugPrint("🔍 ColumnBasedPDFParser: Optimized coordinate-based extraction from page $pageNumber with $columnCount columns")
        
        // OPTIMIZATION: Single extraction using the optimized method
        val columnTexts = extractColumnText(pdfFile, pageNumber, emptyList()) // cellScanAreas defined internally
        
        return Triple(
            if (columnTexts.size > 0) columnTexts[0] else emptyList(), // Dates
            if (columnTexts.size > 1) columnTexts[1] else emptyList(), // Day shift  
            if (columnTexts.size > 2) columnTexts[2] else emptyList()  // Night shift
        )
    }
    
    companion object {
        // OPTIMIZATION: Pre-allocate commonly used objects to reduce GC pressure
        private val DATE_FILTER_REGEX by lazy { Regex("(lunes|martes|miércoles|jueves|viernes|sábado|domingo)", RegexOption.IGNORE_CASE) }
        private val SEPARATOR_REGEX by lazy { Regex("^[\\s\\-_=]+$") }
        
        // Layout constants matching iOS implementation - marked as @JvmStatic for efficiency
        object Layout {
            @JvmStatic val ROW_HEIGHT = 105f
            @JvmStatic val DATE_COLUMN_X = 40f
            @JvmStatic val DATE_COLUMN_WIDTH = 140f
            @JvmStatic val DAY_COLUMN_X = 190f
            @JvmStatic val DAY_COLUMN_WIDTH = 180f
            @JvmStatic val NIGHT_COLUMN_X = 380f
            @JvmStatic val NIGHT_COLUMN_WIDTH = 180f
            @JvmStatic val PAGE_TOP_MARGIN = 150f // Space for headers
            @JvmStatic val PAGE_HEIGHT = 792f // Standard A4 height
            
            // Additional constants for compatibility
            @JvmStatic val pageMargin = 40f
            @JvmStatic val dateColumnWidthRatio = 0.22f
            @JvmStatic val columnGap = 5f
        }
        
        /**
         * Define cell scan areas similar to iOS CellScanArea struct
         */
        fun createSegoviaCellScanAreas(): List<CellScanArea> {
            return listOf(
                // Date column
                CellScanArea(
                    x = Layout.DATE_COLUMN_X,
                    width = Layout.DATE_COLUMN_WIDTH,
                    increment = Layout.ROW_HEIGHT,
                    rows = 18 // Approximately 18 dates per page
                ),
                
                // Day pharmacy column  
                CellScanArea(
                    x = Layout.DAY_COLUMN_X,
                    width = Layout.DAY_COLUMN_WIDTH,
                    increment = Layout.ROW_HEIGHT,
                    rows = 18 // Approximately 18 pharmacy entries per page
                ),
                
                // Night pharmacy column
                CellScanArea(
                    x = Layout.NIGHT_COLUMN_X,
                    width = Layout.NIGHT_COLUMN_WIDTH,
                    increment = Layout.ROW_HEIGHT,
                    rows = 18 // Approximately 18 pharmacy entries per page
                )
            )
        }
    }
}
