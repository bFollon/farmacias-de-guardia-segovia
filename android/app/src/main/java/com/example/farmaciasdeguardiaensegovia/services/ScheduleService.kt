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

package com.example.farmaciasdeguardiaensegovia.services

import android.content.Context
import com.example.farmaciasdeguardiaensegovia.data.DutyDate
import com.example.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.example.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.example.farmaciasdeguardiaensegovia.data.Region
import com.example.farmaciasdeguardiaensegovia.repositories.PharmacyScheduleRepository
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import kotlin.time.Duration

/**
 * Service for loading and managing pharmacy schedules
 * Equivalent to iOS ScheduleService
 * Now uses PharmacyScheduleRepository for centralized caching
 */
class ScheduleService(context: Context) {

    private val repository = PharmacyScheduleRepository.getInstance(context)

    /**
     * Load schedules for a specific region
     * @param region The region to load schedules for
     * @param forceRefresh Whether to bypass cache and reload
     * @return List of pharmacy schedules for the region
     */
    suspend fun loadSchedules(region: Region, forceRefresh: Boolean = false): List<PharmacySchedule> {
        DebugConfig.debugPrint("ScheduleService: Loading schedules for ${region.name} (forceRefresh: $forceRefresh)")

        // Use the shared repository for loading
        val schedules = repository.loadSchedules(region, forceRefresh)

        DebugConfig.debugPrint("ScheduleService: Loaded ${schedules.size} schedules for ${region.name}")

        // Log sample data for debugging
        if (schedules.isNotEmpty()) {
            val sampleSchedule = schedules.first()
            DebugConfig.debugPrint("Sample schedule: ${sampleSchedule.date.day} ${sampleSchedule.date.month}")
            DebugConfig.debugPrint("Day shift pharmacies: ${sampleSchedule.dayShiftPharmacies.map { it.name }}")
            DebugConfig.debugPrint("Night shift pharmacies: ${sampleSchedule.nightShiftPharmacies.map { it.name }}")
        }

        return schedules
    }

    /**
     * Find the current active schedule and shift type
     * @param schedules List of schedules to search in
     * @return Pair of (schedule, active timespan) or null if not found
     */
    fun findCurrentSchedule(schedules: List<PharmacySchedule>): Pair<PharmacySchedule, DutyTimeSpan>? {
        val now = System.currentTimeMillis()

        val matchingSchedule = schedules.find { it.shifts.entries.find { (key, _) -> key.contains(it.date, now) } != null }

        return matchingSchedule?.let {
            Pair(it, it.shifts.entries.find { (key, _) -> key.contains(it.date, now) }?.key!!)
        }?: null.also {
            DebugConfig.debugPrint("ScheduleService: No matching schedule found for duty info: $now")
        }
    }

    /**
     * Get duty time info for a timestamp (similar to iOS implementation)
     */
    private fun getDutyTimeInfo(timestamp: Long): DutyDate.DutyTimeInfo {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        // Convert current time to minutes since midnight
        val currentTimeInMinutes = hour * 60 + minute
        val morningTransitionInMinutes = 10 * 60 + 15  // 10:15
        val eveningTransitionInMinutes = 22 * 60       // 22:00

        // Determine which date and shift we need
        val (targetDate, shiftType) = when {
            currentTimeInMinutes < morningTransitionInMinutes -> {
                // Between 00:00 and 10:15, we need previous day's night shift
                calendar.add(Calendar.DAY_OF_MONTH, -1)
                Pair(calendar.time, DutyDate.ShiftType.NIGHT)
            }
            currentTimeInMinutes < eveningTransitionInMinutes -> {
                // Between 10:15 and 22:00, we need current day's day shift
                Pair(calendar.time, DutyDate.ShiftType.DAY)
            }
            else -> {
                // Between 22:00 and 23:59, we need current day's night shift
                Pair(calendar.time, DutyDate.ShiftType.NIGHT)
            }
        }

        return DutyDate.DutyTimeInfo(
            date = DutyDate(
                dayOfWeek = "",
                day = calendar.get(Calendar.DAY_OF_MONTH),
                month = getSpanishMonthName(calendar.get(Calendar.MONTH)),
                year = calendar.get(Calendar.YEAR)
            ),
            shiftType = shiftType
        )
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
