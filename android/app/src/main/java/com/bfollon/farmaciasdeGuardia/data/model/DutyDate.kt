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

package com.bfollon.farmaciasdeGuardia.data.model

import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
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
    
    companion object {
        private val MONTH_MAP = mapOf(
            "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
            "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
            "septiembre" to 9, "octubre" to 10, "noviembre" to 11, "diciembre" to 12
        )
        
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
            DebugConfig.debugPrint("\nAttempting to parse date: '$dateString'")
            
            val datePattern = """(?:lunes|martes|miércoles|jueves|viernes|sábado|domingo),\s(\d{1,2})\sde\s(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)(?:\s(\d{4}))?""".toRegex()
            
            DebugConfig.debugPrint("Using pattern: ${datePattern.pattern}")
            
            val match = datePattern.find(dateString)
            if (match == null) {
                DebugConfig.debugPrint("Failed to match regex pattern")
                return null
            }
            
            DebugConfig.debugPrint("Number of capture groups: ${match.groups.size}")
            match.groups.forEachIndexed { index, group ->
                DebugConfig.debugPrint("Group $index value: '${group?.value ?: "null"}'")
            }
            
            val dayOfWeek = dateString.split(",")[0].trim()
            val day = match.groups[1]?.value?.toInt() ?: return null
            val month = match.groups[2]?.value ?: return null
            
            var year: Int? = match.groups[3]?.value?.toInt()
            if (year == null) {
                val currentYear = getCurrentYear()
                // Temporary fix: Only January 1st and 2nd are from next year
                year = if (month.lowercase() == "enero" && (day == 1 || day == 2)) {
                    currentYear + 1
                } else {
                    currentYear
                }
                DebugConfig.debugPrint("No year found, defaulting to $year")
            }
            
            DebugConfig.debugPrint("Parsed result: $dayOfWeek, $day de $month $year")
            
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
    
    companion object TimeInfo {
        /**
         * Determines which duty schedule should be active for a given timestamp
         * For example, at 00:05 on July 26th, we need the night shift from July 25th
         */
        fun dutyTimeInfoForTimestamp(timestamp: Long): DutyTimeInfo {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            // Convert current time to minutes since midnight for easier comparison
            val currentTimeInMinutes = hour * 60 + minute
            val morningTransitionInMinutes = 10 * 60 + 15  // 10:15
            val eveningTransitionInMinutes = 22 * 60       // 22:00
            
            // If we're between 00:00 and 10:15, we need previous day's night shift
            if (currentTimeInMinutes < morningTransitionInMinutes) {
                // Get previous day's date
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                
                return DutyTimeInfo(
                    date = DutyDate(
                        dayOfWeek = "", // This will be filled in by the data
                        day = calendar.get(Calendar.DAY_OF_MONTH),
                        month = "", // This will be matched by timestamp
                        year = calendar.get(Calendar.YEAR)
                    ),
                    shiftType = ShiftType.NIGHT
                )
            }
            
            // If we're between 10:15 and 22:00, we need current day's day shift
            if (currentTimeInMinutes < eveningTransitionInMinutes) {
                return DutyTimeInfo(
                    date = DutyDate(
                        dayOfWeek = "", // This will be filled in by the data
                        day = calendar.get(Calendar.DAY_OF_MONTH),
                        month = "", // This will be matched by timestamp
                        year = calendar.get(Calendar.YEAR)
                    ),
                    shiftType = ShiftType.DAY
                )
            }
            
            // If we're between 22:00 and 23:59, we need current day's night shift
            return DutyTimeInfo(
                date = DutyDate(
                    dayOfWeek = "", // This will be filled in by the data
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    month = "", // This will be matched by timestamp
                    year = calendar.get(Calendar.YEAR)
                ),
                shiftType = ShiftType.NIGHT
            )
        }
    }
}
