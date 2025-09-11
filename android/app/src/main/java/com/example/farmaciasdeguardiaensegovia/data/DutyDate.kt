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

package com.example.farmaciasdeguardiaensegovia.data

import com.example.farmaciasdeguardiaensegovia.services.DebugConfig
import kotlinx.serialization.Serializable
import java.util.*

/**
 * Represents a duty date with day, month, year information
 * Equivalent to iOS DutyDate.swift
 */
@Serializable
data class DutyDate(
    val dayOfWeek: String,
    val day: Int,
    val month: String,
    val year: Int?
) {
    /**
     * Convert to timestamp (milliseconds since epoch)
     */
    fun toTimestamp(): Long? {
        val calendar = Calendar.getInstance()
        val actualYear = year ?: getCurrentYear()
        
        // Convert Spanish month to number (1-12)
        val monthNumber = monthToNumber(month) ?: return null
        
        // Create calendar instance with the date
        calendar.set(actualYear, monthNumber - 1, day, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        return calendar.timeInMillis
    }
    
    /**
     * Convert to Java Date for compatibility with PharmacySchedule
     */
    fun toDate(): Date {
        val calendar = Calendar.getInstance()
        val actualYear = year ?: getCurrentYear()
        
        // Convert Spanish month to number (1-12)
        val monthNumber = monthToNumber(month) ?: 1
        
        // Create calendar instance with the date
        calendar.set(actualYear, monthNumber - 1, day, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        return calendar.time
    }
    
    companion object {
        private val MONTH_MAP = mapOf(
            "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
            "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
            "septiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12
        )
        
        // PERFORMANCE: Pre-compiled regex pattern to avoid repeated compilation
        private val datePattern = """(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),\s(\d{1,2})\sde\s(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\sde\s(\d{4}))?""".toRegex()
        
        fun monthToNumber(month: String): Int? {
            return MONTH_MAP[month.lowercase()]
        }
        
        fun getCurrentYear(): Int {
            return Calendar.getInstance().get(Calendar.YEAR)
        }
        
        /**
         * Parse a date string like "lunes, 15 de julio de 2025"
         */
        fun parse(dateString: String): DutyDate? {
            // PERFORMANCE: Only log when detailed debugging is enabled
            if (DebugConfig.isDetailedLoggingEnabled) {
                println("Attempting to parse date: '$dateString'")
            }
            
            // PERFORMANCE: Use pre-compiled regex pattern
            val match = datePattern.find(dateString)
            if (match == null) {
                if (DebugConfig.isDetailedLoggingEnabled) {
                    println("Failed to match regex pattern")
                }
                return null
            }
            
            val dayOfWeek = dateString.split(",")[0].trim()
            val day = match.groups[1]?.value?.toInt() ?: return null
            val month = match.groups[2]?.value ?: return null
            
            var year: Int? = match.groups[3]?.value?.toInt()
            if (year == null) {
                val currentYear = getCurrentYear()
                // Only January 1st and 2nd are from next year for 2024->2025 transition
                year = if (month.lowercase() == "enero" && (day == 1 || day == 2)) {
                    currentYear + 1
                } else {
                    currentYear
                }
            }
            
            return DutyDate(dayOfWeek = dayOfWeek, day = day, month = month, year = year)
        }
    }
    
    /**
     * Represents shift types for pharmacy duty
     */
    enum class ShiftType {
        DAY,    // 10:15 to 22:00 same day
        NIGHT   // 22:00 to 10:15 next day
    }
    
    /**
     * Information about duty time for a given timestamp
     */
    data class DutyTimeInfo(
        val date: DutyDate,
        val shiftType: ShiftType
    )
}
