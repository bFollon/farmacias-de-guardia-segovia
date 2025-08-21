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
package com.farmaciasdeGuardia.services.pdfparsing

import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.StringWriter

/**
 * Base class for parsing PDFs that use a column-based layout.
 * Android equivalent of iOS ColumnBasedPDFParser.
 */
abstract class ColumnBasedPDFParser {
    
    /**
     * Represents a column of text in the PDF with its position and content
     */
    data class TextColumn(
        val x: Float,
        val width: Float,
        val texts: MutableList<Pair<Float, String>> = mutableListOf()
    )
    
    /**
     * Custom PDFTextStripper that captures text positions
     */
    private inner class PositionalTextStripper : PDFTextStripper() {
        val textBlocks = mutableListOf<TextBlock>()
        
        data class TextBlock(
            val x: Float,
            val y: Float,
            val width: Float,
            val height: Float,
            val text: String
        )
        
        override fun writeString(text: String, textPositions: List<TextPosition>) {
            if (text.trim().isEmpty()) return
            
            val firstPos = textPositions.firstOrNull() ?: return
            val lastPos = textPositions.lastOrNull() ?: firstPos
            
            textBlocks.add(
                TextBlock(
                    x = firstPos.x,
                    y = firstPos.y,
                    width = lastPos.x + lastPos.width - firstPos.x,
                    height = firstPos.height,
                    text = text.trim()
                )
            )
        }
    }
    
    /**
     * Extract text from a PDF page with positional information
     */
    protected fun extractTextWithPositions(page: PDPage): List<PositionalTextStripper.TextBlock> {
        return try {
            val stripper = PositionalTextStripper()
            stripper.startPage = 1
            stripper.endPage = 1
            
            // Process the page to extract positioned text
            val writer = StringWriter()
            stripper.writeText(page.document, writer)
            
            // Sort blocks by Y position (top to bottom), then X position (left to right)
            stripper.textBlocks.sortedWith(
                compareBy<PositionalTextStripper.TextBlock> { -it.y }.thenBy { it.x }
            )
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ Error extracting text positions: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Group text blocks into columns based on X position
     */
    protected fun groupIntoColumns(
        textBlocks: List<PositionalTextStripper.TextBlock>,
        columnThreshold: Float = 50f
    ): List<TextColumn> {
        if (textBlocks.isEmpty()) return emptyList()
        
        val columns = mutableListOf<TextColumn>()
        
        for (block in textBlocks) {
            // Find existing column or create new one
            val existingColumn = columns.find { column ->
                kotlin.math.abs(block.x - column.x) <= columnThreshold
            }
            
            if (existingColumn != null) {
                existingColumn.texts.add(Pair(block.y, block.text))
            } else {
                val newColumn = TextColumn(
                    x = block.x,
                    width = block.width
                )
                newColumn.texts.add(Pair(block.y, block.text))
                columns.add(newColumn)
            }
        }
        
        // Sort columns by X position and sort texts within each column by Y position
        return columns.sortedBy { it.x }.map { column ->
            column.copy(texts = column.texts.sortedBy { -it.first }.toMutableList())
        }
    }
    
    /**
     * Remove duplicate adjacent text blocks, keeping only the first occurrence
     */
    protected fun removeDuplicateAdjacent(blocks: List<Pair<Float, String>>): List<Pair<Float, String>> {
        return PDFParsingUtils.removeDuplicateAdjacent(blocks)
    }
    
    /**
     * Extract text from columns and flatten into lists
     */
    protected fun extractColumnTextFlattened(
        page: PDPage,
        expectedColumns: Int = 3
    ): Triple<List<String>, List<String>, List<String>> {
        val textBlocks = extractTextWithPositions(page)
        val columns = groupIntoColumns(textBlocks)
        
        DebugConfig.debugPrint("📄 Extracted ${columns.size} columns from PDF page")
        
        // Ensure we have at least the expected number of columns
        val paddedColumns = columns.toMutableList()
        while (paddedColumns.size < expectedColumns) {
            paddedColumns.add(TextColumn(0f, 0f))
        }
        
        // Extract text from each column
        val dates = paddedColumns.getOrNull(0)?.texts
            ?.map { it.second }
            ?.filter { it.isNotBlank() } ?: emptyList()
            
        val dayShifts = paddedColumns.getOrNull(1)?.texts
            ?.map { it.second }
            ?.filter { it.isNotBlank() } ?: emptyList()
            
        val nightShifts = paddedColumns.getOrNull(2)?.texts
            ?.map { it.second }
            ?.filter { it.isNotBlank() } ?: emptyList()
        
        DebugConfig.debugPrint("📄 Column data - Dates: ${dates.size}, Day shifts: ${dayShifts.size}, Night shifts: ${nightShifts.size}")
        
        return Triple(dates, dayShifts, nightShifts)
    }
}
