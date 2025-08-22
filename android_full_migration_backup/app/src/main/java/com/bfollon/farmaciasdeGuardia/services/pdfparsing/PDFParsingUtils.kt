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
package com.bfollon.farmaciasdeGuardia.services.pdfparsing

import com.bfollon.farmaciasdeGuardia.data.model.DutyDate
import com.bfollon.farmaciasdeGuardia.data.model.Pharmacy
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.*
import kotlin.collections.iterator

/**
 * Utility functions for parsing dates and text from PDFs
 * Android equivalent of iOS DutyDate and parsing utilities
 */
object PDFParsingUtils {
    
    /**
     * Parse a date string in various Spanish formats
     * Handles formats like "Lunes 1", "1 de Enero", etc.
     */
    fun parseDate(dateString: String): DutyDate? {
        val cleanString = dateString.trim().lowercase(Locale.getDefault())
        
        // Try to extract day and month from various formats
        return tryParseSpanishDate(cleanString)
    }
    
    private fun tryParseSpanishDate(dateString: String): DutyDate? {
        // Spanish day names to numbers
        val dayNames = mapOf(
            "lunes" to null, "martes" to null, "miércoles" to null, "miercoles" to null,
            "jueves" to null, "viernes" to null, "sábado" to null, "sabado" to null, "domingo" to null
        )
        
        // Spanish month names
        val monthNames = mapOf(
            "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
            "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
            "septiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12
        )
        
        // Remove day names first
        var cleanString = dateString
        for (dayName in dayNames.keys) {
            cleanString = cleanString.replace(dayName, "").trim()
        }
        
        // Look for patterns like "1 de enero", "1 enero", "enero 1"
        val parts = cleanString.split(Regex("\\s+|\\s*de\\s*")).filter { it.isNotBlank() }
        
        var day: Int? = null
        var month: String? = null
        
        for (part in parts) {
            // Try to parse as number
            val number = part.toIntOrNull()
            if (number != null && number in 1..31) {
                day = number
                continue
            }
            
            // Try to match month name
            for ((monthName, monthNum) in monthNames) {
                if (part.contains(monthName)) {
                    month = monthName
                    break
                }
            }
        }
        
        return if (day != null && month != null) {
            DutyDate(day = day, month = month, year = getCurrentYear())
        } else {
            null
        }
    }
    
    /**
     * Get current year for date parsing
     */
    fun getCurrentYear(): Int {
        return Clock.System.todayIn(TimeZone.currentSystemDefault()).year
    }
    
    /**
     * Convert month name to number
     */
    fun monthToNumber(monthName: String): Int? {
        val monthNames = mapOf(
            "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
            "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
            "septiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12
        )
        return monthNames[monthName.lowercase()]
    }
    
    /**
     * Parse a batch of pharmacy text lines into Pharmacy objects
     */
    fun parsePharmacies(pharmacyLines: List<String>): List<Pharmacy> {
        return pharmacyLines.mapNotNull { line ->
            parsePharmacyLine(line.trim())
        }
    }
    
    /**
     * Parse a single pharmacy line into a Pharmacy object
     * Expected format: "Pharmacy Name - Address, Phone"
     */
    private fun parsePharmacyLine(line: String): Pharmacy? {
        if (line.isBlank()) return null
        
        // Try different parsing strategies
        // Format 1: "Name - Address, Phone"
        val dashSplit = line.split(" - ")
        if (dashSplit.size >= 2) {
            val name = dashSplit[0].trim()
            val remainder = dashSplit[1].trim()
            
            // Try to separate address and phone
            val commaSplit = remainder.split(",")
            val address = commaSplit[0].trim()
            val phone = if (commaSplit.size > 1) commaSplit[1].trim() else ""
            
            if (name.isNotEmpty()) {
                return Pharmacy(
                    id = generatePharmacyId(name),
                    name = name,
                    address = address,
                    phone = phone,
                    latitude = null,
                    longitude = null,
                    schedule = ""
                )
            }
        }
        
        // Format 2: Just the name (fallback)
        if (line.isNotEmpty()) {
            return Pharmacy(
                id = generatePharmacyId(line),
                name = line,
                address = "",
                phone = "",
                latitude = null,
                longitude = null,
                schedule = ""
            )
        }
        
        return null
    }
    
    /**
     * Generate a consistent ID for a pharmacy based on its name
     */
    private fun generatePharmacyId(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50)
    }
    
    /**
     * Remove duplicate adjacent text blocks
     */
    fun removeDuplicateAdjacent(blocks: List<Pair<Float, String>>): List<Pair<Float, String>> {
        if (blocks.isEmpty()) return emptyList()
        
        val result = mutableListOf<Pair<Float, String>>()
        result.add(blocks[0])
        
        for (i in 1 until blocks.size) {
            val current = blocks[i]
            if (current.second != result.last().second) {
                result.add(current)
            }
        }
        
        return result
    }
}
