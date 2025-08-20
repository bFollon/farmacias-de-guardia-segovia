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

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * Represents a time span for pharmacy duty shifts
 * Equivalent to iOS DutyTimeSpan.swift
 */
@Serializable
data class DutyTimeSpan(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    /**
     * Returns true if the span crosses over to the next day
     */
    val spansMultipleDays: Boolean
        get() = endHour < startHour || (endHour == startHour && endMinute < startMinute)
    
    /**
     * Checks if the given timestamp falls within this duty time span
     */
    fun contains(timestamp: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        
        return if (spansMultipleDays) {
            // For cross-midnight spans, we need to check time of day, not absolute dates
            containsTimeOfDay(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
        } else {
            // For same-day spans, check if time falls within range
            val timeInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val startMinutes = startHour * 60 + startMinute
            val endMinutes = endHour * 60 + endMinute
            timeInMinutes in startMinutes..endMinutes
        }
    }
    
    /**
     * Checks if a given time of day (hour and minute) falls within this duty time span
     * This method handles cross-midnight spans correctly
     */
    fun containsTimeOfDay(hour: Int, minute: Int): Boolean {
        val timeInMinutes = hour * 60 + minute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute
        
        return if (spansMultipleDays) {
            // For spans that cross midnight (e.g., 22:00 - 10:15)
            timeInMinutes >= startMinutes || timeInMinutes <= endMinutes
        } else {
            // For spans within the same day (e.g., 10:15 - 22:00)
            timeInMinutes in startMinutes..endMinutes
        }
    }
    
    /**
     * A human-readable representation of the time span (e.g. "10:15 - 22:00")
     */
    val displayName: String
        get() {
            val startTime = String.format("%02d:%02d", startHour, startMinute)
            val endTime = String.format("%02d:%02d", endHour, endMinute)
            return "$startTime - $endTime"
        }
    
    companion object {
        /** Segovia Capital daytime shift (10:15 - 22:00) */
        val CAPITAL_DAY = DutyTimeSpan(startHour = 10, startMinute = 15, endHour = 22, endMinute = 0)
        
        /** Segovia Capital nighttime shift (22:00 - 10:15 next day) */
        val CAPITAL_NIGHT = DutyTimeSpan(startHour = 22, startMinute = 0, endHour = 10, endMinute = 15)
        
        /** 24-hour shift used by Cuéllar and El Espinar (00:00 - 23:59) */
        val FULL_DAY = DutyTimeSpan(startHour = 0, startMinute = 0, endHour = 23, endMinute = 59)
        
        /** Rural daytime shift for standard hours (10:00 - 20:00) */
        val RURAL_DAYTIME = DutyTimeSpan(startHour = 10, startMinute = 0, endHour = 20, endMinute = 0)
        
        /** Rural extended daytime shift (10:00 - 22:00) */
        val RURAL_EXTENDED_DAYTIME = DutyTimeSpan(startHour = 10, startMinute = 0, endHour = 22, endMinute = 0)
    }
}
