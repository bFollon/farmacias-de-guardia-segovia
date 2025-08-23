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
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Base class for column-based PDF parsers
 * Now using iText instead of PDFBox for better Android compatibility
 */
abstract class ColumnBasedPDFParser {
    
    data class TextColumn(val x: Float, val width: Float)
    
    /**
     * Extract text from a PDF page using iText
     */
    fun extractTextFromPage(pdfFile: File, pageNumber: Int): String {
        return try {
            val reader = PdfReader(pdfFile)
            val pdfDoc = PdfDocument(reader)
            
            val text = PdfTextExtractor.getTextFromPage(
                pdfDoc.getPage(pageNumber), 
                LocationTextExtractionStrategy()
            )
            
            pdfDoc.close()
            text
        } catch (e: Exception) {
            DebugConfig.debugError("Error extracting text from page $pageNumber: ${e.message}", e)
            ""
        }
    }
    
    /**
     * Extract text from multiple columns on a page
     * Using iText for better Android compatibility
     */
    fun extractColumnText(pdfFile: File, pageNumber: Int, columns: List<TextColumn>): List<List<String>> {
        DebugConfig.debugPrint("🔍 ColumnBasedPDFParser: Extracting text from page $pageNumber with ${columns.size} columns")
        
        try {
            val fullText = extractTextFromPage(pdfFile, pageNumber)
            DebugConfig.debugPrint("📄 Extracted raw text length: ${fullText.length}")
            DebugConfig.debugPrint("📄 Sample text: ${fullText.take(200)}...")
            
            // Split text into lines
            val lines = fullText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            DebugConfig.debugPrint("📄 Found ${lines.size} non-empty lines")
            
            // For now, implement a simple column parsing based on text patterns
            // This is a placeholder - we'll improve this based on the actual PDF structure
            val (dates, dayPharmacies, nightPharmacies) = parseSegoviaCapitalText(lines)
            
            return listOf(dates, dayPharmacies, nightPharmacies)
            
        } catch (e: Exception) {
            DebugConfig.debugError("❌ Error extracting column text: ${e.message}", e)
            return emptyList()
        }
    }
    
    /**
     * Parse Segovia Capital text into date, day pharmacy, and night pharmacy columns
     * This is a simplified parser based on text patterns
     */
    private fun parseSegoviaCapitalText(lines: List<String>): Triple<List<String>, List<String>, List<String>> {
        val dates = mutableListOf<String>()
        val dayPharmacies = mutableListOf<String>()
        val nightPharmacies = mutableListOf<String>()
        
        // Pattern matching for Segovia Capital format
        for (line in lines) {
            when {
                // Date patterns (Spanish day names)
                line.contains(Regex("(lunes|martes|miércoles|jueves|viernes|sábado|domingo)", RegexOption.IGNORE_CASE)) -> {
                    dates.add(line)
                    DebugConfig.debugPrint("📅 Found date: $line")
                }
                // Pharmacy patterns
                line.contains("FARMACIA", ignoreCase = true) || 
                line.contains("C/", ignoreCase = true) ||
                line.contains("Plaza", ignoreCase = true) ||
                line.matches(Regex("\\d{3}\\s*\\d{2}\\s*\\d{2}\\s*\\d{2}")) -> {
                    // Simple heuristic: alternate between day and night
                    if (dayPharmacies.size <= nightPharmacies.size) {
                        dayPharmacies.add(line)
                        DebugConfig.debugPrint("☀️ Found day pharmacy info: $line")
                    } else {
                        nightPharmacies.add(line)
                        DebugConfig.debugPrint("🌙 Found night pharmacy info: $line")
                    }
                }
            }
        }
        
        DebugConfig.debugPrint("� Parsed: ${dates.size} dates, ${dayPharmacies.size} day lines, ${nightPharmacies.size} night lines")
        return Triple(dates, dayPharmacies, nightPharmacies)
    }
    
    /**
     * Extract column text flattened into arrays (for batch processing)
     * Now using iText instead of PDFBox
     */
    fun extractColumnTextFlattened(pdfFile: File, pageNumber: Int, columnCount: Int): Triple<List<String>, List<String>, List<String>> {
        // Define standard layout constants for Segovia Capital
        val pageMargin = 40f
        val dateColumnWidthRatio = 0.22f
        val columnGap = 5f
        
        // For now, we use the simplified parsing approach
        val columns = listOf(
            TextColumn(pageMargin, 100f), // Date column
            TextColumn(200f, 150f), // Day shift
            TextColumn(400f, 150f)  // Night shift
        )
        
        val columnTexts = extractColumnText(pdfFile, pageNumber, columns)
        
        return Triple(
            columnTexts.getOrElse(0) { emptyList() }, // Dates
            columnTexts.getOrElse(1) { emptyList() }, // Day shift
            columnTexts.getOrElse(2) { emptyList() }  // Night shift
        )
    }
}
