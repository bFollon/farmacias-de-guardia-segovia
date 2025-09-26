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

import androidx.compose.ui.text.toLowerCase
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

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
    fun contains(date: DutyDate, timestamp: Long): Boolean {
        val toCheck =
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()

        val shiftDate = LocalDate.of(date.year!!, DutyDate.monthToNumber(date.month)!!, date.day)

        val (startTime, endTime) = if (spansMultipleDays) {
            Pair(
                LocalDateTime.of(shiftDate, LocalTime.of(startHour, startMinute)),
                LocalDateTime.of(shiftDate.plusDays(1), LocalTime.of(endHour, endMinute))
            )
        } else {
            Pair(
                LocalDateTime.of(shiftDate, LocalTime.of(startHour, startMinute)),
                LocalDateTime.of(shiftDate, LocalTime.of(endHour, endMinute))
            )
        }

        return toCheck in startTime..endTime
    }

    fun isSameDay(date: DutyDate, timestamp: Long): Boolean {
        val toCheck =
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()

        val shiftDate = LocalDate.of(date.year!!, DutyDate.monthToNumber(date.month)!!, date.day)

        return toCheck.dayOfMonth == shiftDate.dayOfMonth && toCheck.month == shiftDate.month
                && toCheck.year == shiftDate.year
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

    fun isActiveNow(): Boolean {
        val now = LocalDateTime.now()
        return containsTimeOfDay(now.hour, now.minute)
    }

    val displayName: String get() {
        return when {
            spansMultipleDays && startHour > 19 -> "Turno nocturno"
            !spansMultipleDays && startHour == 0 && endHour == 23 -> "Turno 24h"
            !spansMultipleDays && endHour > 20 -> "Turno diurno extendido"
            !spansMultipleDays -> "Turno diurno"
            else -> "Turno"
        }
    }

    /**
     * A human-readable representation of the time span (e.g. "10:15 - 22:00")
     */
    val displayFormat: String
        get() {
            return "$startTimeFormatted - $endTimeFormatted"
        }

    val startTimeFormatted get() = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute)

    val endTimeFormatted get() = String.format(Locale.getDefault(),"%02d:%02d", endHour, endMinute)

    val shiftInfo: String
        get() = when (spansMultipleDays) {
            true -> "El ${displayName.lowercase()} empieza a las $startTimeFormatted y se extiende hasta las $endTimeFormatted del día siguiente"
            false -> "El ${displayName.lowercase()} empieza a las $startTimeFormatted y se extiende hasta las $endTimeFormatted del mismo día"
        }

    companion object {
        /** Segovia Capital daytime shift (10:15 - 22:00) */
        val CapitalDay = DutyTimeSpan(startHour = 10, startMinute = 15, endHour = 22, endMinute = 0)

        /** Segovia Capital nighttime shift (22:00 - 10:15 next day) */
        val CapitalNight =
            DutyTimeSpan(startHour = 22, startMinute = 0, endHour = 10, endMinute = 15)

        /** 24-hour shift used by Cuéllar and El Espinar (00:00 - 23:59) */
        val FullDay = DutyTimeSpan(startHour = 0, startMinute = 0, endHour = 23, endMinute = 59)

        /** Rural daytime shift for standard hours (10:00 - 20:00) */
        val RuralDaytime =
            DutyTimeSpan(startHour = 10, startMinute = 0, endHour = 20, endMinute = 0)

        /** Rural extended daytime shift (10:00 - 22:00) */
        val RuralExtendedDaytime =
            DutyTimeSpan(startHour = 10, startMinute = 0, endHour = 22, endMinute = 0)
    }
}
