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
 * Base class for column-based PDF parsers using coordinate-based extraction
 * Now mirrors iOS approach with CellScanArea concept
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
     * Mirrors iOS coordinate-based text extraction
     */
    private fun extractTextFromRegion(pdfFile: File, pageNumber: Int, region: Rectangle): String {
        return try {
            val reader = PdfReader(pdfFile)
            val pdfDoc = PdfDocument(reader)
            
            val filter = TextRegionEventFilter(region)
            val strategy = FilteredTextEventListener(LocationTextExtractionStrategy(), filter)
            val text = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(pageNumber), strategy)
            
            pdfDoc.close()
            text
        } catch (e: Exception) {
            DebugConfig.debugError("Error extracting text from region: ${e.message}", e)
            ""
        }
    }
    
    /**
     * Scan a row of cells using coordinate-based extraction
     * Mirrors iOS scanRow functionality
     */
    private fun scanRowWithCoordinates(
        pdfFile: File, 
        pageNumber: Int, 
        cellScanAreas: List<CellScanArea>, 
        rowY: Float,
        pageHeight: Float
    ): List<List<String>> {
        val rowData = mutableListOf<List<String>>()
        
        for (scanArea in cellScanAreas) {
            val cellLines = mutableListOf<String>()
            
            // Scan each row within the cell
            for (row in 0 until scanArea.rows) {
                val currentY = rowY + (row * scanArea.increment)
                
                // Skip if we're outside page bounds
                if (currentY < 0 || currentY + scanArea.increment > pageHeight) {
                    continue
                }
                
                val region = Rectangle(
                    scanArea.x, 
                    pageHeight - currentY - scanArea.increment,  // iText uses bottom-left origin
                    scanArea.width, 
                    scanArea.increment
                )
                
                val text = extractTextFromRegion(pdfFile, pageNumber, region)
                val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                cellLines.addAll(lines)
            }
            
            rowData.add(cellLines)
        }
        
        return rowData
    }
    
    /**
     * Get page dimensions for coordinate calculations
     */
    private fun getPageDimensions(pdfFile: File, pageNumber: Int): Pair<Float, Float> {
        return try {
            val reader = PdfReader(pdfFile)
            val pdfDoc = PdfDocument(reader)
            val page = pdfDoc.getPage(pageNumber)
            val pageSize = page.getPageSize()
            val width = pageSize.width
            val height = pageSize.height
            pdfDoc.close()
            Pair(width, height)
        } catch (e: Exception) {
            DebugConfig.debugError("Error getting page dimensions: ${e.message}", e)
            Pair(595f, 842f) // A4 default
        }
    }
    
    /**
     * Extract column text using coordinate-based scanning (mirrors iOS approach)
     * Replaces the old text-based parsing with precise coordinate extraction
     */
    fun extractColumnText(pdfFile: File, pageNumber: Int, columns: List<CellScanArea>): List<List<String>> {
        DebugConfig.debugPrint("🔍 ColumnBasedPDFParser: Coordinate-based extraction from page $pageNumber")
        
        val (pageWidth, pageHeight) = getPageDimensions(pdfFile, pageNumber)
        DebugConfig.debugPrint("📏 Page dimensions: ${pageWidth}x${pageHeight}")
        
        // Define cell scan areas for Segovia Capital format (mirrors iOS)
        val contentWidth = pageWidth - (2 * Layout.pageMargin)
        val dateColumnWidth = contentWidth * Layout.dateColumnWidthRatio
        val pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2
        
        val cellScanAreas = listOf(
            // Date column: single line per cell (but taller to capture whole date)
            CellScanArea(
                x = Layout.pageMargin,
                width = dateColumnWidth,
                increment = 15f, // Estimate base font height - will be refined
                rows = 1
            ),
            // Day pharmacy column: three lines per cell
            CellScanArea(
                x = Layout.pageMargin + dateColumnWidth + Layout.columnGap,
                width = pharmacyColumnWidth,
                increment = 15f,
                rows = 3
            ),
            // Night pharmacy column: three lines per cell  
            CellScanArea(
                x = Layout.pageMargin + dateColumnWidth + Layout.columnGap + pharmacyColumnWidth + Layout.columnGap,
                width = pharmacyColumnWidth,
                increment = 15f,
                rows = 3
            )
        )
        
        DebugConfig.debugPrint("� Cell scan areas defined:")
        cellScanAreas.forEachIndexed { index, area ->
            val columnName = when(index) { 
                0 -> "Date" 
                1 -> "Day Pharmacy" 
                2 -> "Night Pharmacy"
                else -> "Unknown"
            }
            DebugConfig.debugPrint("  $columnName: x=${area.x}, width=${area.width}, rows=${area.rows}")
        }
        
        // Find coherent rows and extract data
        val allDates = mutableListOf<String>()
        val allDayPharmacies = mutableListOf<String>()
        val allNightPharmacies = mutableListOf<String>()
        
        // Scan from top of page looking for coherent rows
        var currentY = 100f // Start below header area
        val rowHeight = 45f   // Approximate height of each logical row (3 text lines)
        
        while (currentY < pageHeight - 100f) { // Stop before footer
            val rowData = scanRowWithCoordinates(pdfFile, pageNumber, cellScanAreas, currentY, pageHeight)
            
            // Check if this is a coherent row (has date + pharmacy data)
            if (isCoherentSegoviaRow(rowData)) {
                DebugConfig.debugPrint("✅ Found coherent row at Y=$currentY")
                
                // Extract date (first column)
                val dateCell = rowData.getOrNull(0) ?: emptyList()
                if (dateCell.isNotEmpty()) {
                    val dateText = dateCell.joinToString(" ").trim()
                    if (dateText.isNotEmpty()) {
                        allDates.add(dateText)
                        DebugConfig.debugPrint("� Extracted date: $dateText")
                    }
                }
                
                // Extract day pharmacy (second column)  
                val dayCell = rowData.getOrNull(1) ?: emptyList()
                allDayPharmacies.addAll(dayCell)
                DebugConfig.debugPrint("☀️ Extracted day pharmacy lines: ${dayCell.size}")
                
                // Extract night pharmacy (third column)
                val nightCell = rowData.getOrNull(2) ?: emptyList()
                allNightPharmacies.addAll(nightCell)
                DebugConfig.debugPrint("🌙 Extracted night pharmacy lines: ${nightCell.size}")
                
                currentY += rowHeight // Move to next logical row
            } else {
                currentY += 5f // Small increment to continue searching
            }
        }
        
        DebugConfig.debugPrint("📊 Coordinate extraction result: ${allDates.size} dates, ${allDayPharmacies.size} day lines, ${allNightPharmacies.size} night lines")
        
        return listOf(allDates, allDayPharmacies, allNightPharmacies)
    }
    
    /**
     * Validate if a scanned row contains coherent Segovia Capital data
     * Mirrors iOS isCoherentSegoviaRow logic
     */
    private fun isCoherentSegoviaRow(rowData: List<List<String>>): Boolean {
        // We expect 3 cells: date, day pharmacy, night pharmacy
        if (rowData.size != 3) return false
        
        val dateCell = rowData[0]
        val dayCell = rowData[1]
        val nightCell = rowData[2]
        
        // Date cell should have content that looks like a Spanish date
        val dateText = dateCell.joinToString(" ")
        val hasValidDate = dateText.contains(Regex("(lunes|martes|miércoles|jueves|viernes|sábado|domingo)", RegexOption.IGNORE_CASE))
        
        // At least one pharmacy cell should have content
        val hasPharmacyContent = dayCell.isNotEmpty() || nightCell.isNotEmpty()
        
        return hasValidDate && hasPharmacyContent
    }
    
    /**
     * Extract column text flattened into arrays (for batch processing)
     * Now using coordinate-based extraction instead of text parsing
     */
    fun extractColumnTextFlattened(pdfFile: File, pageNumber: Int, columnCount: Int): Triple<List<String>, List<String>, List<String>> {
        DebugConfig.debugPrint("🔍 ColumnBasedPDFParser: Coordinate-based extraction from page $pageNumber with $columnCount columns")
        
        // Use the new coordinate-based extraction
        val columnTexts = extractColumnText(pdfFile, pageNumber, emptyList()) // cellScanAreas defined internally
        
        return Triple(
            if (columnTexts.size > 0) columnTexts[0] else emptyList(), // Dates
            if (columnTexts.size > 1) columnTexts[1] else emptyList(), // Day shift  
            if (columnTexts.size > 2) columnTexts[2] else emptyList()  // Night shift
        )
    }
    
    companion object {
        // Layout constants matching iOS implementation
        object Layout {
            const val ROW_HEIGHT = 105f
            const val DATE_COLUMN_X = 40f
            const val DATE_COLUMN_WIDTH = 140f
            const val DAY_COLUMN_X = 190f
            const val DAY_COLUMN_WIDTH = 180f
            const val NIGHT_COLUMN_X = 380f
            const val NIGHT_COLUMN_WIDTH = 180f
            const val PAGE_TOP_MARGIN = 150f // Space for headers
            const val PAGE_HEIGHT = 792f // Standard A4 height
            
            // Additional constants for compatibility
            const val pageMargin = 40f
            const val dateColumnWidthRatio = 0.22f
            const val columnGap = 5f
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
