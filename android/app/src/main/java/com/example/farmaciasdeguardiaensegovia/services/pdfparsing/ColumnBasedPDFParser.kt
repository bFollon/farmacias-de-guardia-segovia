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

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlin.math.max
import kotlin.math.min

/**
 * Base class for column-based PDF parsers
 * Equivalent to iOS ColumnBasedPDFParser
 */
abstract class ColumnBasedPDFParser {
    
    data class TextColumn(val x: Float, val width: Float)
    
    /**
     * Extract text from multiple columns on a page
     * Equivalent to iOS extractColumnText
     */
    fun extractColumnText(page: PDPage, columns: List<TextColumn>): List<List<String>> {
        val mediaBox = page.mediaBox
        val pageHeight = mediaBox.height
        
        // Create text stripper by area
        val stripper = PDFTextStripperByArea()
        
        // Define regions for each column
        columns.forEachIndexed { index, column ->
            val region = android.graphics.RectF(
                column.x,
                0f,
                column.x + column.width,
                pageHeight
            )
            stripper.addRegion("column_$index", region)
        }
        
        // Extract text from regions
        stripper.extractRegions(page)
        
        // Convert to list of text lines per column
        return columns.indices.map { index ->
            val text = stripper.getTextForRegion("column_$index") ?: ""
            text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
    
    /**
     * Extract column text flattened into arrays (for batch processing)
     * Equivalent to iOS extractColumnTextFlattened
     */
    fun extractColumnTextFlattened(page: PDPage, columnCount: Int): Triple<List<String>, List<String>, List<String>> {
        val mediaBox = page.mediaBox
        val pageWidth = mediaBox.width
        
        // Define standard layout constants
        val pageMargin = 40f
        val dateColumnWidthRatio = 0.22f
        val columnGap = 5f
        
        // Calculate column positions
        val availableWidth = pageWidth - (2 * pageMargin)
        val dateColumnWidth = availableWidth * dateColumnWidthRatio
        val pharmacyColumnWidth = (availableWidth - dateColumnWidth - (2 * columnGap)) / 2
        
        val columns = listOf(
            TextColumn(pageMargin, dateColumnWidth), // Date column
            TextColumn(pageMargin + dateColumnWidth + columnGap, pharmacyColumnWidth), // Day shift
            TextColumn(pageMargin + dateColumnWidth + columnGap + pharmacyColumnWidth + columnGap, pharmacyColumnWidth) // Night shift
        )
        
        val columnTexts = extractColumnText(page, columns)
        
        return Triple(
            columnTexts.getOrElse(0) { emptyList() }, // Dates
            columnTexts.getOrElse(1) { emptyList() }, // Day shift
            columnTexts.getOrElse(2) { emptyList() }  // Night shift
        )
    }
}
