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

package com.github.bfollon.farmaciasdeguardiaensegovia.services

import android.content.Context
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyDate
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.repositories.PharmacyScheduleRepository
import java.util.*

/**
 * Service for loading and managing pharmacy schedules
 * Equivalent to iOS ScheduleService
 * Now uses PharmacyScheduleRepository for centralized caching
 */
class ScheduleService(context: Context) {

    private val repository = PharmacyScheduleRepository.Companion.getInstance(context)

    /**
     * Load schedules for a specific region
     * @param region The region to load schedules for
     * @param forceRefresh Whether to bypass cache and reload
     * @return List of pharmacy schedules for the region
     */
    suspend fun loadSchedules(location: DutyLocation, forceRefresh: Boolean = false): List<PharmacySchedule> {
        DebugConfig.debugPrint("ScheduleService: Loading schedules for ${location.associatedRegion.name} (forceRefresh: $forceRefresh)")

        // Clear caches if force refresh is requested (enables full re-download and re-parse)
        if (forceRefresh) {
            clearCacheForRegion(location.associatedRegion)
        }

        // Use the shared repository for loading
        val schedules = repository.loadSchedules(location)

        DebugConfig.debugPrint("ScheduleService: Loaded ${schedules.size} schedules for ${location.associatedRegion.name}")

        return schedules
    }

    /**
     * Find the schedule and shift type for a given timestamp
     * @param schedules List of schedules to search in
     * @param timestamp The timestamp to find the schedule for (defaults to current time)
     * @return Pair of (schedule, active timespan) or null if not found
     */
    fun findScheduleForTimestamp(
        schedules: List<PharmacySchedule>,
        timestamp: Long = System.currentTimeMillis()
    ): Pair<PharmacySchedule, DutyTimeSpan>? {
        val matchingSchedule = schedules.find {
            it.shifts.entries.find { (timeSpan, _) ->
                timeSpan.contains(it.date, timestamp)
            } != null
        }

        return matchingSchedule?.let {
            Pair(it, it.shifts.entries.find { (key, _) ->
                key.contains(it.date, timestamp)
            }?.key!!)
        } ?: run {
            // Fallback: same-day matching
            val existingSchedule = schedules.find {
                it.shifts.entries.find { (timeSpan, _) ->
                    timeSpan.isSameDay(it.date, timestamp)
                } != null
            }
            existingSchedule?.let {
                Pair(it, it.shifts.entries.find { (key, _) ->
                    key.isSameDay(it.date, timestamp)
                }?.key!!)
            } ?: null.also {
                DebugConfig.debugPrint("ScheduleService: No matching schedule found for timestamp: $timestamp")
            }
        }
    }

    /**
     * Find the current active schedule and shift type
     * Convenience method that calls findScheduleForTimestamp with current time
     * @param schedules List of schedules to search in
     * @return Pair of (schedule, active timespan) or null if not found
     */
    fun findCurrentSchedule(schedules: List<PharmacySchedule>): Pair<PharmacySchedule, DutyTimeSpan>? {
        return findScheduleForTimestamp(schedules, System.currentTimeMillis())
    }

    /**
     * Find the next schedule after the current one
     * @param schedules List of all schedules
     * @param currentSchedule The current active schedule
     * @param currentTimeSpan The current active time span
     * @return Pair of (next schedule, next timespan) or null if not found
     */
    fun findNextSchedule(
        schedules: List<PharmacySchedule>,
        currentSchedule: PharmacySchedule?,
        currentTimeSpan: DutyTimeSpan?
    ): Pair<PharmacySchedule, DutyTimeSpan>? {
        if (currentSchedule == null || currentTimeSpan == null) return null

        // Calculate timestamp for end of current shift
        val currentDate = currentSchedule.date
        val shiftDate = java.time.LocalDate.of(
            currentDate.year!!,
            DutyDate.monthToNumber(currentDate.month)!!,
            currentDate.day
        )

        val shiftEndTime = if (currentTimeSpan.spansMultipleDays) {
            // Night shift ending next day (e.g., 22:00 Dec 11 → 10:15 Dec 12)
            java.time.LocalDateTime.of(
                shiftDate.plusDays(1),
                java.time.LocalTime.of(currentTimeSpan.endHour, currentTimeSpan.endMinute)
            )
        } else {
            // Day shift ending same day (e.g., 10:15 → 22:00)
            java.time.LocalDateTime.of(
                shiftDate,
                java.time.LocalTime.of(currentTimeSpan.endHour, currentTimeSpan.endMinute)
            )
        }

        // Add 1 minute to shift end to get timestamp that falls in next shift
        val nextShiftTimestamp = shiftEndTime.plusMinutes(1)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        DebugConfig.debugPrint("ScheduleService: Finding next schedule for timestamp: $nextShiftTimestamp")

        // Reuse existing logic to find schedule for that timestamp
        return findScheduleForTimestamp(schedules, nextShiftTimestamp)
    }

    /**
     * Convert Calendar.MONTH (0-11) to Spanish month name
     */
    private fun getSpanishMonthName(monthIndex: Int): String {
        val months = arrayOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )
        return months.getOrElse(monthIndex) { "enero" }
    }

    /**
     * Clear all cached schedules
     */
    suspend fun clearCache() {
        repository.clearAllCache()
        DebugConfig.debugPrint("ScheduleService: All caches cleared via repository")
    }

    /**
     * Clear cache for a specific region
     */
    suspend fun clearCacheForRegion(region: Region) {
        repository.clearCacheForRegion(region)
        DebugConfig.debugPrint("ScheduleService: Cleared cache for ${region.name} via repository")
    }

    /**
     * Check if schedules are already loaded for a region
     */
    fun isLoaded(region: Region): Boolean = repository.isLoaded(region)

    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String = repository.getCacheStats()

    /**
     * Get current date and time formatted for display
     */
    fun getCurrentDateTime(): String {
        val calendar = Calendar.getInstance()
        val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Lunes"
            Calendar.TUESDAY -> "Martes"
            Calendar.WEDNESDAY -> "Miércoles"
            Calendar.THURSDAY -> "Jueves"
            Calendar.FRIDAY -> "Viernes"
            Calendar.SATURDAY -> "Sábado"
            Calendar.SUNDAY -> "Domingo"
            else -> "Lunes"
        }

        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = getSpanishMonthName(calendar.get(Calendar.MONTH))
        val year = calendar.get(Calendar.YEAR)

        return "$dayOfWeek, $day de $month de $year"
    }
}
