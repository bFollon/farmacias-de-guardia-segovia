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

package com.farmaciasdeGuardia.services

import com.bfollon.farmaciasdeGuardia.data.model.*
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android equivalent of iOS ScheduleService
 * Manages loading and caching of pharmacy schedules from PDFs
 */
@Singleton
class ScheduleService @Inject constructor(
    private val pdfCacheService: PDFCacheService,
    private val pdfProcessingService: PDFProcessingService
) {
    
    // Cache by region name (equivalent to iOS cachedSchedules)
    private val cachedSchedules = mutableMapOf<String, List<PharmacySchedule>>()
    private val cacheMutex = Mutex()
    
    /**
     * Load schedules for a region with caching
     * Equivalent to iOS loadSchedules(for:forceRefresh:)
     */
    suspend fun loadSchedules(
        region: Region,
        forceRefresh: Boolean = false
    ): List<PharmacySchedule> = cacheMutex.withLock {
        
        // Return cached schedules if available and not forcing refresh
        if (!forceRefresh) {
            cachedSchedules[region.name]?.let { cached ->
                DebugConfig.debugPrint("ScheduleService: Using cached schedules for region ${region.name}")
                return@withLock cached
            }
        }
        
        // Load and cache if not available or force refresh requested
        DebugConfig.debugPrint("ScheduleService: Loading schedules from PDF for region ${region.name}...")
        
        // Get the PDF file (cached or download if needed)
        val pdfFileResult = pdfCacheService.getEffectivePDFFile(for = region)
        
        if (pdfFileResult.isFailure) {
            DebugConfig.debugPrint("❌ ScheduleService: Failed to get PDF for ${region.name}: ${pdfFileResult.exceptionOrNull()?.message}")
            return@withLock emptyList()
        }
        
        val pdfFile = pdfFileResult.getOrThrow()
        
        // Process the PDF to extract schedules
        val schedules = pdfProcessingService.loadPharmacies(pdfFile, region, forceRefresh)
        
        // Cache the results
        cachedSchedules[region.name] = schedules
        
        DebugConfig.debugPrint("ScheduleService: Successfully cached ${schedules.size} schedules for ${region.name}")
        
        // Print a sample schedule for verification (like iOS)
        if (schedules.isNotEmpty()) {
            val sampleSchedule = schedules.first()
            DebugConfig.debugPrint("\nSample schedule for ${region.name}:")
            DebugConfig.debugPrint("Date: ${sampleSchedule.date}")
            
            DebugConfig.debugPrint("\nDay Shift Pharmacies:")
            sampleSchedule.dayShiftPharmacies.forEach { pharmacy ->
                DebugConfig.debugPrint("- ${pharmacy.name}")
                DebugConfig.debugPrint("  Address: ${pharmacy.address}")
                DebugConfig.debugPrint("  Phone: ${pharmacy.formattedPhone}")
                pharmacy.additionalInfo?.let { info ->
                    DebugConfig.debugPrint("  Additional Info: $info")
                }
            }
            
            DebugConfig.debugPrint("\nNight Shift Pharmacies:")
            sampleSchedule.nightShiftPharmacies.forEach { pharmacy ->
                DebugConfig.debugPrint("- ${pharmacy.name}")
                DebugConfig.debugPrint("  Address: ${pharmacy.address}")
                DebugConfig.debugPrint("  Phone: ${pharmacy.formattedPhone}")
                pharmacy.additionalInfo?.let { info ->
                    DebugConfig.debugPrint("  Additional Info: $info")
                }
            }
            DebugConfig.debugPrint("")
        }
        
        return@withLock schedules
    }
    
    /**
     * Clear schedule cache
     * Equivalent to iOS clearCache()
     */
    suspend fun clearCache() = cacheMutex.withLock {
        cachedSchedules.clear()
        DebugConfig.debugPrint("🗑️ ScheduleService: Cache cleared")
    }
    
    /**
     * Clear cache for a specific region
     */
    suspend fun clearCache(region: Region) = cacheMutex.withLock {
        cachedSchedules.remove(region.name)
        DebugConfig.debugPrint("🗑️ ScheduleService: Cache cleared for ${region.name}")
    }
    
    /**
     * Find current schedule using legacy iOS logic
     * Equivalent to iOS findCurrentSchedule(in:)
     */
    fun findCurrentSchedule(schedules: List<PharmacySchedule>): Pair<PharmacySchedule, DutyDate.ShiftType>? {
        val now = Date()
        val currentTimestamp = now.time / 1000.0 // Convert to seconds like iOS
        
        // Get the duty time info for current timestamp
        val dutyInfo = DutyDate.dutyTimeInfoForTimestamp(currentTimestamp)
        
        // Find the schedule for the required date (using dutyInfo.date)
        val schedule = schedules.find { schedule ->
            // Both dates should have the same day, month, and year
            schedule.date.day == dutyInfo.date.day &&
            schedule.date.year == dutyInfo.date.year &&
            DutyDate.monthToNumber(schedule.date.month) == Calendar.getInstance().get(Calendar.MONTH) + 1
        }
        
        return schedule?.let { it to dutyInfo.shiftType }
    }
    
    /**
     * Region-aware version that detects shift pattern from schedule data
     * Equivalent to iOS findCurrentSchedule(in:for:)
     */
    fun findCurrentSchedule(
        schedules: List<PharmacySchedule>,
        region: Region
    ): Pair<PharmacySchedule, DutyTimeSpan>? {
        val now = Date()
        val calendar = Calendar.getInstance()
        
        // First, find a sample schedule to determine the shift pattern for this region
        val sampleSchedule = schedules.firstOrNull() ?: return null
        
        // Detect shift pattern based on what shifts are available in the schedule
        val has24HourShifts = sampleSchedule.shifts[DutyTimeSpan.FULL_DAY] != null
        val hasDayNightShifts = sampleSchedule.shifts[DutyTimeSpan.CAPITAL_DAY] != null || 
                               sampleSchedule.shifts[DutyTimeSpan.CAPITAL_NIGHT] != null
        
        return if (has24HourShifts) {
            // For 24-hour regions, always use current day and full-day shift
            val schedule = schedules.find { schedule ->
                val scheduleTimestamp = schedule.date.toTimestamp()
                if (scheduleTimestamp != null) {
                    val scheduleDate = Date((scheduleTimestamp * 1000).toLong())
                    calendar.time = now
                    val nowDay = calendar.get(Calendar.DAY_OF_YEAR)
                    val nowYear = calendar.get(Calendar.YEAR)
                    
                    calendar.time = scheduleDate
                    val scheduleDay = calendar.get(Calendar.DAY_OF_YEAR)
                    val scheduleYear = calendar.get(Calendar.YEAR)
                    
                    nowDay == scheduleDay && nowYear == scheduleYear
                } else false
            }
            schedule?.let { it to DutyTimeSpan.FULL_DAY }
        } else if (hasDayNightShifts) {
            // For day/night regions, use the existing logic
            val legacyResult = findCurrentSchedule(schedules)
            legacyResult?.let { (schedule, shiftType) ->
                val timeSpan = if (shiftType == DutyDate.ShiftType.DAY) {
                    DutyTimeSpan.CAPITAL_DAY
                } else {
                    DutyTimeSpan.CAPITAL_NIGHT
                }
                schedule to timeSpan
            }
        } else {
            // Fallback: treat as 24-hour if we can't determine
            val schedule = schedules.find { schedule ->
                val scheduleTimestamp = schedule.date.toTimestamp()
                if (scheduleTimestamp != null) {
                    val scheduleDate = Date((scheduleTimestamp * 1000).toLong())
                    calendar.time = now
                    val nowDay = calendar.get(Calendar.DAY_OF_YEAR)
                    val nowYear = calendar.get(Calendar.YEAR)
                    
                    calendar.time = scheduleDate
                    val scheduleDay = calendar.get(Calendar.DAY_OF_YEAR)
                    val scheduleYear = calendar.get(Calendar.YEAR)
                    
                    nowDay == scheduleDay && nowYear == scheduleYear
                } else false
            }
            schedule?.let { it to DutyTimeSpan.FULL_DAY }
        }
    }
    
    /**
     * Get current date and time formatted for display
     * Equivalent to iOS getCurrentDateTime()
     */
    fun getCurrentDateTime(): String {
        val today = Date()
        val calendar = Calendar.getInstance().apply { time = today }
        
        // Format similar to iOS: "lunes 25 enero · Ahora"
        val dayNames = arrayOf(
            "", "domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"
        )
        val monthNames = arrayOf(
            "", "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
        )
        
        val dayOfWeek = dayNames[calendar.get(Calendar.DAY_OF_WEEK)]
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val month = monthNames[calendar.get(Calendar.MONTH) + 1]
        
        return "$dayOfWeek $day $month · Ahora"
    }
    
    /**
     * Find schedule for a specific date
     * Equivalent to iOS findSchedule(for:in:)
     */
    fun findSchedule(date: Date, schedules: List<PharmacySchedule>): PharmacySchedule? {
        val targetCalendar = Calendar.getInstance().apply { time = date }
        
        return schedules.find { schedule ->
            val scheduleTimestamp = schedule.date.toTimestamp()
            if (scheduleTimestamp != null) {
                val scheduleDate = Date((scheduleTimestamp * 1000).toLong())
                val scheduleCalendar = Calendar.getInstance().apply { time = scheduleDate }
                
                targetCalendar.get(Calendar.DAY_OF_YEAR) == scheduleCalendar.get(Calendar.DAY_OF_YEAR) &&
                targetCalendar.get(Calendar.YEAR) == scheduleCalendar.get(Calendar.YEAR)
            } else false
        }
    }
    
    /**
     * Get cached schedules count for debugging
     */
    fun getCachedSchedulesCount(): Map<String, Int> = cacheMutex.runCatching {
        cachedSchedules.mapValues { it.value.size }
    }.getOrElse { emptyMap() }
    
    /**
     * Check if schedules are cached for a region
     */
    fun hasSchedulesCached(region: Region): Boolean {
        return cachedSchedules.containsKey(region.name)
    }
}
