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
import java.util.*

/**
 * Service for loading and managing pharmacy schedules
 * Equivalent to iOS ScheduleService
 */
class ScheduleService(context: Context) {
    
    private val pdfDownloadService = PDFDownloadService(context)
    private val pdfProcessingService = PDFProcessingService()
    
    // Cache for loaded schedules
    private val cachedSchedules = mutableMapOf<String, List<PharmacySchedule>>()
    
    /**
     * Load schedules for a specific region
     * @param region The region to load schedules for
     * @param forceRefresh Whether to bypass cache and reload
     * @return List of pharmacy schedules for the region
     */
    suspend fun loadSchedules(region: Region, forceRefresh: Boolean = false): List<PharmacySchedule> {
        val cacheKey = region.id
        
        // Check cache first unless force refresh
        if (!forceRefresh && cachedSchedules.containsKey(cacheKey)) {
            println("ScheduleService: Returning cached schedules for ${region.name}")
            return cachedSchedules[cacheKey] ?: emptyList()
        }
        
        try {
            println("ScheduleService: Loading schedules for ${region.name}")
            
            // Download the PDF file
            val fileName = "${region.id}.pdf"
            val pdfFile = pdfDownloadService.downloadPDF(region.pdfURL, fileName)
            
            if (pdfFile == null) {
                println("ScheduleService: Failed to download PDF for ${region.name}")
                return emptyList()
            }
            
            // Process the PDF file
            val schedules = pdfProcessingService.loadPharmacies(pdfFile, region, forceRefresh)
            
            // Cache the results
            cachedSchedules[cacheKey] = schedules
            
            println("ScheduleService: Successfully loaded ${schedules.size} schedules for ${region.name}")
            
            // Log sample data for debugging
            if (schedules.isNotEmpty()) {
                val sampleSchedule = schedules.first()
                println("Sample schedule: ${sampleSchedule.date.day} ${sampleSchedule.date.month}")
                println("Day shift pharmacies: ${sampleSchedule.dayShiftPharmacies.map { it.name }}")
                println("Night shift pharmacies: ${sampleSchedule.nightShiftPharmacies.map { it.name }}")
            }
            
            return schedules
            
        } catch (e: Exception) {
            println("ScheduleService: Error loading schedules for ${region.name}: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * Find the current active schedule and shift type
     * @param schedules List of schedules to search in
     * @return Pair of (schedule, active timespan) or null if not found
     */
    fun findCurrentSchedule(schedules: List<PharmacySchedule>): Pair<PharmacySchedule, DutyTimeSpan>? {
        val now = System.currentTimeMillis()
        val currentTime = Calendar.getInstance()
        
        // Determine which duty date and shift we need
        val dutyInfo = getDutyTimeInfo(now)
        
        // Find matching schedule
        val matchingSchedule = schedules.find { schedule ->
            schedule.date.day == dutyInfo.date.day &&
            schedule.date.month == dutyInfo.date.month &&
            (schedule.date.year ?: DutyDate.getCurrentYear()) == (dutyInfo.date.year ?: DutyDate.getCurrentYear())
        }
        
        if (matchingSchedule == null) {
            println("ScheduleService: No matching schedule found for duty info: ${dutyInfo.date.day} ${dutyInfo.date.month}")
            return null
        }
        
        // Determine active timespan
        val activeTimeSpan = when (dutyInfo.shiftType) {
            DutyDate.ShiftType.DAY -> DutyTimeSpan.CapitalDay
            DutyDate.ShiftType.NIGHT -> DutyTimeSpan.CapitalNight
        }
        
        return Pair(matchingSchedule, activeTimeSpan)
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
        cachedSchedules.clear()
        pdfProcessingService.clearCache()
        pdfDownloadService.clearCache()
        println("ScheduleService: All caches cleared")
    }
    
    /**
     * Clear cache for a specific region
     */
    suspend fun clearCacheForRegion(region: Region) {
        cachedSchedules.remove(region.id)
        pdfProcessingService.clearCacheForRegion(region)
        println("ScheduleService: Cleared cache for ${region.name}")
    }
    
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
