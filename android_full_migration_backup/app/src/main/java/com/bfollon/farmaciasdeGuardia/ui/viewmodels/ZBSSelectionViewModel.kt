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

package com.bfollon.farmaciasdeGuardia.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bfollon.farmaciasdeGuardia.data.model.*
import com.bfollon.farmaciasdeGuardia.services.ZBSScheduleService
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

/**
 * UI state for ZBS selection
 * Equivalent to iOS ZBSSelectionView state management
 */
data class ZBSSelectionUiState(
    val availableZBS: List<ZBS> = emptyList(),
    val zbsSchedules: List<ZBSSchedule> = emptyList(),
    val selectedZBS: ZBS? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showingSchedule: Boolean = false
)

/**
 * UI state for ZBS schedule display
 * Equivalent to iOS ZBSScheduleView state management
 */
data class ZBSScheduleUiState(
    val zbsSchedules: List<ZBSSchedule> = emptyList(),
    val selectedDate: Date = Date(),
    val todaySchedule: ZBSSchedule? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val formattedDate: String = "",
    val availableZBS: Map<String, ZBS> = emptyMap()
)

/**
 * ViewModel for ZBS (Basic Health Zone) selection
 * Equivalent to iOS ZBSSelectionView logic
 */
@HiltViewModel
class ZBSSelectionViewModel @Inject constructor(
    private val zbsScheduleService: ZBSScheduleService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ZBSSelectionUiState())
    val uiState: StateFlow<ZBSSelectionUiState> = _uiState.asStateFlow()
    
    init {
        // Initialize with available ZBS areas
        _uiState.value = _uiState.value.copy(
            availableZBS = ZBS.availableZBS
        )
        
        // Load ZBS schedules for today
        loadZBSSchedules()
    }
    
    /**
     * Load ZBS schedules from the service
     * Equivalent to iOS ZBSScheduleService.getZBSSchedules
     */
    private fun loadZBSSchedules() {
        DebugConfig.debugPrint("📅 ZBSSelectionViewModel: Loading ZBS schedules")
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val schedules = zbsScheduleService.getZBSSchedules(Region.SEGOVIA_RURAL) ?: emptyList()
                DebugConfig.debugPrint("✅ ZBSSelectionViewModel: Loaded ${schedules.size} ZBS schedules")
                
                _uiState.value = _uiState.value.copy(
                    zbsSchedules = schedules,
                    isLoading = false
                )
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ ZBSSelectionViewModel: Error loading ZBS schedules: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar horarios ZBS: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Select a ZBS area
     */
    fun selectZBS(zbs: ZBS) {
        DebugConfig.debugPrint("🎯 ZBSSelectionViewModel: ZBS selected: ${zbs.name}")
        _uiState.value = _uiState.value.copy(selectedZBS = zbs)
    }
    
    /**
     * Show schedule for selected ZBS
     */
    fun showSchedule() {
        _uiState.value = _uiState.value.copy(showingSchedule = true)
    }
    
    /**
     * Hide schedule view
     */
    fun hideSchedule() {
        _uiState.value = _uiState.value.copy(showingSchedule = false)
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Refresh ZBS schedules
     */
    fun refreshSchedules() {
        loadZBSSchedules()
    }
    
    /**
     * Check if any ZBS has pharmacies available today
     */
    fun hasPharmaciesAvailableToday(): Boolean {
        val today = Calendar.getInstance()
        val todaySchedule = _uiState.value.zbsSchedules.find { schedule ->
            val scheduleDay = schedule.date.day
            val scheduleMonth = monthNameToNumber(schedule.date.month) ?: 0
            val scheduleYear = schedule.date.year ?: today.get(Calendar.YEAR)
            
            scheduleDay == today.get(Calendar.DAY_OF_MONTH) &&
            scheduleMonth == (today.get(Calendar.MONTH) + 1) &&
            scheduleYear == today.get(Calendar.YEAR)
        }
        
        return todaySchedule?.let { schedule ->
            ZBS.availableZBS.any { zbs ->
                schedule.pharmacies(zbs.id).isNotEmpty()
            }
        } ?: false
    }
    
    /**
     * Get ZBS areas that have pharmacies available today
     */
    fun getAvailableZBSForToday(): List<ZBS> {
        val today = Calendar.getInstance()
        val todaySchedule = _uiState.value.zbsSchedules.find { schedule ->
            val scheduleDay = schedule.date.day
            val scheduleMonth = monthNameToNumber(schedule.date.month) ?: 0
            val scheduleYear = schedule.date.year ?: today.get(Calendar.YEAR)
            
            scheduleDay == today.get(Calendar.DAY_OF_MONTH) &&
            scheduleMonth == (today.get(Calendar.MONTH) + 1) &&
            scheduleYear == today.get(Calendar.YEAR)
        }
        
        return if (todaySchedule != null) {
            ZBS.availableZBS.filter { zbs ->
                todaySchedule.pharmacies(zbs.id).isNotEmpty()
            }
        } else {
            emptyList()
        }
    }
    
    /**
     * Convert month name to number (like iOS version)
     */
    private fun monthNameToNumber(monthName: String): Int? {
        val monthNames = mapOf(
            "ene" to 1, "feb" to 2, "mar" to 3, "abr" to 4,
            "may" to 5, "jun" to 6, "jul" to 7, "ago" to 8,
            "sep" to 9, "oct" to 10, "nov" to 11, "dic" to 12
        )
        return monthNames[monthName.lowercase()]
    }
}

/**
 * ViewModel for ZBS schedule display
 * Equivalent to iOS ZBSScheduleView logic
 */
@HiltViewModel
class ZBSScheduleViewModel @Inject constructor(
    private val zbsScheduleService: ZBSScheduleService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ZBSScheduleUiState())
    val uiState: StateFlow<ZBSScheduleUiState> = _uiState.asStateFlow()
    
    init {
        // Initialize with today's date
        val today = Date()
        _uiState.value = _uiState.value.copy(
            selectedDate = today,
            availableZBS = ZBS.availableZBS.associateBy { it.id }
        )
        
        // Load schedules
        loadSchedules()
    }
    
    /**
     * Load ZBS schedules and find today's schedule
     */
    private fun loadSchedules() {
        DebugConfig.debugPrint("📅 ZBSScheduleViewModel: Loading ZBS schedules")
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val schedules = zbsScheduleService.getZBSSchedules(Region.SEGOVIA_RURAL) ?: emptyList()
                DebugConfig.debugPrint("✅ ZBSScheduleViewModel: Loaded ${schedules.size} ZBS schedules")
                
                // Find today's schedule
                val todaySchedule = findScheduleForDate(_uiState.value.selectedDate, schedules)
                
                // Format date
                val formattedDate = formatDate(_uiState.value.selectedDate)
                
                _uiState.value = _uiState.value.copy(
                    zbsSchedules = schedules,
                    todaySchedule = todaySchedule,
                    formattedDate = formattedDate,
                    isLoading = false
                )
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ ZBSScheduleViewModel: Error loading schedules: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar horarios: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Change selected date
     */
    fun selectDate(date: Date) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        
        // Find schedule for new date
        val schedule = findScheduleForDate(date, _uiState.value.zbsSchedules)
        val formattedDate = formatDate(date)
        
        _uiState.value = _uiState.value.copy(
            todaySchedule = schedule,
            formattedDate = formattedDate
        )
    }
    
    /**
     * Find schedule for a specific date
     */
    private fun findScheduleForDate(date: Date, schedules: List<ZBSSchedule>): ZBSSchedule? {
        val calendar = Calendar.getInstance()
        calendar.time = date
        
        return schedules.find { schedule ->
            val scheduleDay = schedule.date.day
            val scheduleMonth = monthNameToNumber(schedule.date.month) ?: 0
            val scheduleYear = schedule.date.year ?: calendar.get(Calendar.YEAR)
            
            scheduleDay == calendar.get(Calendar.DAY_OF_MONTH) &&
            scheduleMonth == (calendar.get(Calendar.MONTH) + 1) &&
            scheduleYear == calendar.get(Calendar.YEAR)
        }
    }
    
    /**
     * Get pharmacies for a specific ZBS on the selected date
     */
    fun getPharmaciesForZBS(zbsId: String): List<Pharmacy> {
        return _uiState.value.todaySchedule?.pharmacies(zbsId) ?: emptyList()
    }
    
    /**
     * Get all ZBS areas that have pharmacies on the selected date
     */
    fun getActiveZBSAreas(): List<ZBS> {
        val todaySchedule = _uiState.value.todaySchedule ?: return emptyList()
        
        return ZBS.availableZBS.filter { zbs ->
            todaySchedule.pharmacies(zbs.id).isNotEmpty()
        }
    }
    
    /**
     * Refresh schedules
     */
    fun refreshSchedules() {
        loadSchedules()
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Format date for display
     */
    private fun formatDate(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date
        
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
        val year = calendar.get(Calendar.YEAR)
        
        return "$dayOfWeek $day de $month de $year"
    }
    
    /**
     * Convert month name to number (helper function)
     */
    private fun monthNameToNumber(monthName: String): Int? {
        val monthNames = mapOf(
            "ene" to 1, "feb" to 2, "mar" to 3, "abr" to 4,
            "may" to 5, "jun" to 6, "jul" to 7, "ago" to 8,
            "sep" to 9, "oct" to 10, "nov" to 11, "dic" to 12
        )
        return monthNames[monthName.lowercase()]
    }
}
