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
import java.util.*

/**
 * Utility functions for PDF parsing
 * Equivalent to iOS PDFParsingUtils
 */
object PDFParsingUtils {
    
    /**
     * Convert Spanish month name to number (1-12)
     */
    fun monthToNumber(month: String): Int? {
        return DutyDate.monthToNumber(month)
    }
    
    /**
     * Convert Spanish month abbreviation to number (1-12)
     */
    fun monthAbbrToNumber(monthAbbr: String): Int? {
        val fullMonth = monthAbbrToFullName(monthAbbr)
        return fullMonth?.let { DutyDate.monthToNumber(it) }
    }
    
    /**
     * Convert Spanish month abbreviation to full name
     */
    fun monthAbbrToFullName(monthAbbr: String): String? {
        return when (monthAbbr.lowercase()) {
            "ene" -> "enero"
            "feb" -> "febrero"
            "mar" -> "marzo"
            "abr" -> "abril"
            "may" -> "mayo"
            "jun" -> "junio"
            "jul" -> "julio"
            "ago" -> "agosto"
            "sep" -> "septiembre"
            "oct" -> "octubre"
            "nov" -> "noviembre"
            "dic" -> "diciembre"
            else -> null
        }
    }
    
    /**
     * Get current year
     */
    fun getCurrentYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR)
    }
    
    /**
     * Get Spanish day of week name for a given date
     */
    fun getDayOfWeek(day: Int, month: Int, year: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, day)
        
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "domingo"
            Calendar.MONDAY -> "lunes"
            Calendar.TUESDAY -> "martes"
            Calendar.WEDNESDAY -> "miércoles"
            Calendar.THURSDAY -> "jueves"
            Calendar.FRIDAY -> "viernes"
            Calendar.SATURDAY -> "sábado"
            else -> "unknown"
        }
    }
    
    /**
     * Get Spanish month name from number (1-12)
     */
    fun getMonthName(month: Int): String {
        return when (month) {
            1 -> "enero"
            2 -> "febrero"
            3 -> "marzo"
            4 -> "abril"
            5 -> "mayo"
            6 -> "junio"
            7 -> "julio"
            8 -> "agosto"
            9 -> "septiembre"
            10 -> "octubre"
            11 -> "noviembre"
            12 -> "diciembre"
            else -> "unknown"
        }
    }
}
