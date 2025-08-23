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

import com.example.farmaciasdeguardiaensegovia.data.DutyDate
import com.example.farmaciasdeguardiaensegovia.data.Pharmacy
import com.example.farmaciasdeguardiaensegovia.services.DebugConfig
import java.util.*

/**
 * Utility functions for PDF parsing
 * Equivalent to iOS PDFParsingUtils
 */
object PDFParsingUtils {
    
    /**
     * Parse a date string like "lunes, 15 de julio de 2025"
     */
    fun parseDate(dateString: String): DutyDate? {
        return DutyDate.parse(dateString)
    }
    
    /**
     * Parse pharmacy lines into Pharmacy objects
     */
    fun parsePharmacies(lines: List<String>): List<Pharmacy> {
        DebugConfig.debugPrint("🏥 PDFParsingUtils.parsePharmacies: Processing ${lines.size} lines")
        if (lines.isNotEmpty()) {
            DebugConfig.debugPrint("🏥 Sample lines: ${lines.take(3)}")
        }
        val result = Pharmacy.parseBatch(lines)
        DebugConfig.debugPrint("🏥 PDFParsingUtils.parsePharmacies: Parsed ${result.size} pharmacies")
        return result
    }
    
    /**
     * Convert Spanish month name to number (1-12)
     */
    fun monthToNumber(month: String): Int? {
        return DutyDate.monthToNumber(month)
    }
    
    /**
     * Get current year
     */
    fun getCurrentYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR)
    }
}
