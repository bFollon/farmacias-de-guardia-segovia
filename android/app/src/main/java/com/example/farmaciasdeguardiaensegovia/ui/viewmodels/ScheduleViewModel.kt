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

package com.example.farmaciasdeguardiaensegovia.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmaciasdeguardiaensegovia.data.DutyTimeSpan
import com.example.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.example.farmaciasdeguardiaensegovia.data.Region
import com.example.farmaciasdeguardiaensegovia.services.ScheduleService
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
    regionId: String = "segovia-capital"
) : ViewModel() {
    
    private val scheduleService = ScheduleService(context)
    
    // Find the region by ID
    private val currentRegion = Region.allRegions.find { it.id == regionId } ?: Region.segoviaCapital
    
    data class ScheduleUiState(
        val isLoading: Boolean = false,
        val schedules: List<PharmacySchedule> = emptyList(),
        val currentSchedule: PharmacySchedule? = null,
        val activeTimeSpan: DutyTimeSpan? = null,
        val selectedDate: Calendar? = null,
        val region: Region = Region.segoviaCapital,
        val error: String? = null,
        val formattedDateTime: String = ""
    )
    
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()
    
    init {
        // Update the state with the current region and load its schedules
        _uiState.value = _uiState.value.copy(region = currentRegion)
        loadSchedules(currentRegion)
    }
    
    /**
     * Load schedules for a specific region
     */
    fun loadSchedules(region: Region, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    region = region
                )
                
                val schedules = scheduleService.loadSchedules(region, forceRefresh)
                val currentDateTime = scheduleService.getCurrentDateTime()
                
                // Find current schedule and active timespan
                val currentInfo = scheduleService.findCurrentSchedule(schedules)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    schedules = schedules,
                    currentSchedule = currentInfo?.first,
                    activeTimeSpan = currentInfo?.second,
                    formattedDateTime = currentDateTime
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
        val schedule = findScheduleForDate(calendar)
        _uiState.value = _uiState.value.copy(
            selectedDate = calendar,
            currentSchedule = schedule
        )
    }
    
    /**
     * Refresh current data
     */
    fun refresh() {
        loadSchedules(_uiState.value.region, forceRefresh = true)
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
                loadSchedules(_uiState.value.region, forceRefresh = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Error clearing cache: ${e.message}"
                )
            }
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
