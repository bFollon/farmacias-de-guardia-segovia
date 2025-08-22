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
import com.bfollon.farmaciasdeGuardia.services.ScheduleService
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*
import javax.inject.Inject

/**
 * UI state for schedule display
 * Equivalent to iOS schedule view state management
 */
data class ScheduleUiState(
    val schedules: List<PharmacySchedule> = emptyList(),
    val currentSchedule: PharmacySchedule? = null,
    val activeTimeSpan: DutyTimeSpan? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val formattedDateTime: String = "",
    val region: Region? = null,
    val showingInfoSheet: Boolean = false
)

/**
 * ViewModel for schedule content display
 * Equivalent to iOS ScheduleContentView and DayScheduleView logic
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleService: ScheduleService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()
    
    /**
     * Load schedules for a specific region
     * Equivalent to iOS ScheduleService.loadSchedules
     */
    fun loadSchedules(region: Region, forceRefresh: Boolean = false) {
        DebugConfig.debugPrint("📋 ScheduleViewModel: Loading schedules for ${region.name}")
        
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            region = region
        )
        
        viewModelScope.launch {
            try {
                val schedules = scheduleService.loadSchedules(region, forceRefresh)
                DebugConfig.debugPrint("✅ ScheduleViewModel: Loaded ${schedules.size} schedules for ${region.name}")
                
                // Find current schedule and active timespan
                val currentResult = scheduleService.findCurrentSchedule(schedules, region)
                val currentDateTime = scheduleService.getCurrentDateTime()
                
                if (currentResult != null) {
                    val (currentSchedule, activeTimeSpan) = currentResult
                    DebugConfig.debugPrint("⏰ ScheduleViewModel: Current schedule found with timespan: $activeTimeSpan")
                    
                    _uiState.value = _uiState.value.copy(
                        schedules = schedules,
                        currentSchedule = currentSchedule,
                        activeTimeSpan = activeTimeSpan,
                        formattedDateTime = currentDateTime,
                        isLoading = false
                    )
                } else {
                    DebugConfig.debugPrint("❌ ScheduleViewModel: No current schedule found")
                    _uiState.value = _uiState.value.copy(
                        schedules = schedules,
                        currentSchedule = null,
                        activeTimeSpan = null,
                        formattedDateTime = currentDateTime,
                        isLoading = false,
                        error = "No hay horarios disponibles para la fecha actual"
                    )
                }
                
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ ScheduleViewModel: Error loading schedules: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar los horarios: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Load schedule for a specific date
     * Equivalent to iOS ScheduleService.findSchedule(for:in:)
     */
    fun loadScheduleForDate(region: Region, date: Date) {
        DebugConfig.debugPrint("📅 ScheduleViewModel: Loading schedule for date: $date")
        
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            region = region
        )
        
        viewModelScope.launch {
            try {
                val schedules = scheduleService.loadSchedules(region)
                val dateSchedule = scheduleService.findSchedule(date, schedules)
                
                val formatter = DateFormat.getDateInstance(DateFormat.FULL, Locale("es", "ES"))
                val formattedDate = formatter.format(date)
                
                if (dateSchedule != null) {
                    DebugConfig.debugPrint("✅ ScheduleViewModel: Found schedule for date: $formattedDate")
                    
                    // For specific date views, we might want different timespan logic
                    // For now, use full day for non-capital regions, or detect from schedule
                    val timeSpan = determineTimeSpanForRegion(region, dateSchedule)
                    
                    _uiState.value = _uiState.value.copy(
                        schedules = schedules,
                        currentSchedule = dateSchedule,
                        activeTimeSpan = timeSpan,
                        formattedDateTime = formattedDate,
                        isLoading = false
                    )
                } else {
                    DebugConfig.debugPrint("❌ ScheduleViewModel: No schedule found for date: $formattedDate")
                    _uiState.value = _uiState.value.copy(
                        schedules = schedules,
                        currentSchedule = null,
                        activeTimeSpan = null,
                        formattedDateTime = formattedDate,
                        isLoading = false,
                        error = "No hay horarios disponibles para la fecha seleccionada"
                    )
                }
                
            } catch (e: Exception) {
                DebugConfig.debugPrint("❌ ScheduleViewModel: Error loading schedule for date: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar el horario: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Determine appropriate timespan for a region and schedule
     */
    private fun determineTimeSpanForRegion(region: Region, schedule: PharmacySchedule): DutyTimeSpan {
        return when (region) {
            Region.SEGOVIA_CAPITAL -> {
                // Capital has day/night shifts, determine current active one
                val now = Date()
                val calendar = Calendar.getInstance()
                calendar.time = now
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                
                // Day shift: 9:00 - 22:00, Night shift: 22:00 - 9:00
                if (hour in 9..21) DutyTimeSpan.CAPITAL_DAY else DutyTimeSpan.CAPITAL_NIGHT
            }
            else -> {
                // Other regions are typically 24h
                DutyTimeSpan.FULL_DAY
            }
        }
    }
    
    /**
     * Refresh schedules (force refresh from server)
     */
    fun refreshSchedules() {
        val region = _uiState.value.region ?: return
        loadSchedules(region, forceRefresh = true)
    }
    
    /**
     * Show info sheet
     */
    fun showInfoSheet() {
        _uiState.value = _uiState.value.copy(showingInfoSheet = true)
    }
    
    /**
     * Dismiss info sheet
     */
    fun dismissInfoSheet() {
        _uiState.value = _uiState.value.copy(showingInfoSheet = false)
    }
    
    /**
     * Clear error state
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Get active pharmacy for current schedule and timespan
     */
    fun getActivePharmacy(): Pharmacy? {
        val currentSchedule = _uiState.value.currentSchedule ?: return null
        val activeTimeSpan = _uiState.value.activeTimeSpan ?: return null
        
        return currentSchedule.shifts[activeTimeSpan]?.firstOrNull()
            ?: currentSchedule.dayShiftPharmacies.firstOrNull()
    }
    
    /**
     * Get all pharmacies for current schedule (for regions with multiple shifts)
     */
    fun getAllPharmaciesForCurrentSchedule(): Map<DutyTimeSpan, List<Pharmacy>> {
        val currentSchedule = _uiState.value.currentSchedule ?: return emptyMap()
        return currentSchedule.shifts
    }
    
    /**
     * Check if region has day/night shifts (like Segovia Capital)
     */
    fun hasDayNightShifts(): Boolean {
        val region = _uiState.value.region ?: return false
        return region == Region.SEGOVIA_CAPITAL
    }
    
    /**
     * Get formatted error report URL for current state
     */
    fun getErrorReportUrl(): String? {
        val currentSchedule = _uiState.value.currentSchedule ?: return null
        val activePharmacy = getActivePharmacy() ?: return null
        val timeSpan = _uiState.value.activeTimeSpan ?: return null
        
        val shiftName = when (timeSpan) {
            DutyTimeSpan.FULL_DAY -> "24 horas"
            DutyTimeSpan.CAPITAL_DAY -> "Diurno"
            DutyTimeSpan.CAPITAL_NIGHT -> "Nocturno"
            else -> timeSpan.displayName
        }
        
        // TODO: Implement equivalent to iOS AppConfig.EmailLinks.errorReport
        // This would generate a mailto: URL with pre-filled error report
        return "mailto:report@example.com?subject=Error en horarios&body=Error en turno $shiftName: ${activePharmacy.name} - ${activePharmacy.address}"
    }
}
