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

package com.github.bfollon.farmaciasdeguardiaensegovia.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Pharmacy
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.github.bfollon.farmaciasdeguardiaensegovia.repositories.PDFURLRepository
import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFCacheManager
import com.github.bfollon.farmaciasdeguardiaensegovia.services.ScheduleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for managing pharmacy schedules
 */
class ScheduleViewModel(
    context: Context,
    locationId: String
) : ViewModel() {
    
    private val scheduleService = ScheduleService(context)
    private val pdfCacheManager = PDFCacheManager.getInstance(context)
    private val urlRepository = PDFURLRepository.getInstance(context)
    
    // Find the region by ID
    private val location = DutyLocation.Companion.fromId(locationId)

    data class ScheduleUiState(
        val isLoading: Boolean = false,
        val schedules: List<PharmacySchedule> = emptyList(),
        val currentSchedule: PharmacySchedule? = null,
        val activeTimeSpan: DutyTimeSpan? = null,
        val selectedDate: Calendar? = null,
        val allShiftsForSelectedDate: List<Pair<DutyTimeSpan, List<Pharmacy>>> = emptyList(),
        val location: DutyLocation? = null,
        val error: String? = null,
        val formattedDateTime: String = "",
        val downloadDate: Long? = null,
        val isValidatingPDFURL: Boolean = false,
        val pdfURLError: String? = null,
        // Next shift properties
        val nextSchedule: PharmacySchedule? = null,
        val nextTimeSpan: DutyTimeSpan? = null,
        val minutesUntilShiftChange: Long? = null,
        val showShiftTransitionWarning: Boolean = false
    )
    
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()
    
    init {
        // Update the state with the current region and load its schedules
        _uiState.value = _uiState.value.copy(location = location)
        loadSchedules(location)
    }
    
    /**
     * Load schedules for a specific region
     */
    fun loadSchedules(location: DutyLocation, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    location = location
                )
                
                val schedules = scheduleService.loadSchedules(location, forceRefresh)
                val currentDateTime = scheduleService.getCurrentDateTime()

                // Find current schedule and active timespan
                val currentInfo = scheduleService.findCurrentSchedule(schedules)

                // Find next schedule
                val nextInfo = scheduleService.findNextSchedule(
                    schedules,
                    currentInfo?.first,
                    currentInfo?.second
                )

                // Calculate minutes until current shift ends
                val minutesUntilChange = currentInfo?.second?.let { timeSpan ->
                    calculateMinutesUntilShiftEnd(timeSpan)
                }

                // Show warning if within 30 minutes
                val showWarning = minutesUntilChange != null && minutesUntilChange <= 30

                // Get download date for cache age indicator
                val downloadDate = pdfCacheManager.getDownloadDate(location.associatedRegion)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    schedules = schedules,
                    currentSchedule = currentInfo?.first,
                    activeTimeSpan = currentInfo?.second,
                    nextSchedule = nextInfo?.first,
                    nextTimeSpan = nextInfo?.second,
                    minutesUntilShiftChange = minutesUntilChange,
                    showShiftTransitionWarning = showWarning,
                    formattedDateTime = currentDateTime,
                    downloadDate = downloadDate
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error loading schedules: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Find schedule for a specific date
     */
    fun findScheduleForDate(calendar: Calendar): PharmacySchedule? {
        val targetDay = calendar.get(Calendar.DAY_OF_MONTH)
        val targetMonth = getSpanishMonthName(calendar.get(Calendar.MONTH))
        val targetYear = calendar.get(Calendar.YEAR)
        
        return _uiState.value.schedules.find { schedule ->
            schedule.date.day == targetDay &&
            schedule.date.month.equals(targetMonth, ignoreCase = true) &&
            (schedule.date.year ?: calendar.get(Calendar.YEAR)) == targetYear
        }
    }
    
    /**
     * Set selected date and find corresponding schedule
     */
    fun setSelectedDate(calendar: Calendar) {
        println("🔍 ViewModel setSelectedDate:")
        println("  Received date: ${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH)+1}/${calendar.get(Calendar.YEAR)} ${calendar.get(Calendar.HOUR_OF_DAY)}:${calendar.get(Calendar.MINUTE)}")
        
        val schedule = findScheduleForDate(calendar)
        val allShifts = findAllShiftsForDate(calendar)
        _uiState.value = _uiState.value.copy(
            selectedDate = calendar,
            currentSchedule = schedule,
            allShiftsForSelectedDate = allShifts
        )
    }
    
    /**
     * Find all shifts that have pharmacies assigned for a specific date
     */
    fun findAllShiftsForDate(calendar: Calendar): List<Pair<DutyTimeSpan, List<Pharmacy>>> {
        val schedule = findScheduleForDate(calendar)
        return schedule?.shifts?.filter { (_, pharmacies) -> 
            pharmacies.isNotEmpty() 
        }?.toList() ?: emptyList()
    }
    
    /**
     * Reset to today's date and find current active schedule
     */
    fun resetToToday() {
        _uiState.value = _uiState.value.copy(
            selectedDate = null,
            allShiftsForSelectedDate = emptyList()
        )
        _uiState.value.location?.let { location ->
            loadSchedules(location)
        }
    }
    
    /**
     * Refresh current data
     */
    fun refresh() {
        _uiState.value.location?.let { loadSchedules(it, forceRefresh = true) }
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Clear all caches
     */
    fun clearCache() {
        viewModelScope.launch {
            try {
                scheduleService.clearCache()
                // Reload after clearing cache
                _uiState.value.location?.let { loadSchedules(it, forceRefresh = true) }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error clearing cache: ${e.message}"
                )
            }
        }
    }

    /**
     * Validate PDF URL and return the best URL to use
     * If URL is invalid (404), attempts to scrape fresh URL
     * 
     * @return Validated URL or null if validation fails
     */
    suspend fun validateAndGetPDFURL(): String? {
        val location = _uiState.value.location ?: return null
        val regionName = location.associatedRegion.name
        
        _uiState.value = _uiState.value.copy(
            isValidatingPDFURL = true,
            pdfURLError = null
        )
        
        return try {
            when (val result = urlRepository.resolveURLWithHealing(regionName)) {
                is PDFURLRepository.URLResolutionResult.Success -> {
                    _uiState.value = _uiState.value.copy(isValidatingPDFURL = false)
                    result.url
                }
                is PDFURLRepository.URLResolutionResult.Updated -> {
                    _uiState.value = _uiState.value.copy(isValidatingPDFURL = false)
                    result.newUrl
                }
                is PDFURLRepository.URLResolutionResult.Failed -> {
                    _uiState.value = _uiState.value.copy(
                        isValidatingPDFURL = false,
                        pdfURLError = result.message
                    )
                    null
                }
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isValidatingPDFURL = false,
                pdfURLError = "Error de red. Inténtalo más tarde."
            )
            null
        }
    }
    
    /**
     * Clear PDF URL error
     */
    fun clearPDFURLError() {
        _uiState.value = _uiState.value.copy(pdfURLError = null)
    }

    /**
     * Calculate minutes until the current shift ends
     * Handles midnight-crossing shifts correctly
     */
    private fun calculateMinutesUntilShiftEnd(timeSpan: DutyTimeSpan): Long {
        val now = java.time.LocalDateTime.now()
        val currentMinutes = now.hour * 60 + now.minute
        val endMinutes = timeSpan.endHour * 60 + timeSpan.endMinute

        return if (timeSpan.spansMultipleDays) {
            // Night shift crossing midnight (e.g., 22:00 → 10:15)
            if (currentMinutes >= (timeSpan.startHour * 60 + timeSpan.startMinute)) {
                // Currently in "today" portion (after 22:00)
                val minutesUntilMidnight = (24 * 60) - currentMinutes
                (minutesUntilMidnight + endMinutes).toLong()
            } else {
                // Currently in "tomorrow" portion (before 10:15)
                (endMinutes - currentMinutes).toLong()
            }
        } else {
            // Same-day shift (e.g., 10:15 → 22:00)
            (endMinutes - currentMinutes).toLong()
        }
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
}
