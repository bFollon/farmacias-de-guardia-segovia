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

package com.github.bfollon.farmaciasdeguardiaensegovia.data

/**
 * Represents pharmacy schedules organized by ZBS (Zona Básica de Salud)
 * Android equivalent of iOS ZBSSchedule.swift
 */
data class ZBSSchedule(
    /** The date this schedule applies to */
    val date: DutyDate,
    
    /** Schedules organized by ZBS ID */
    val schedulesByZBS: Map<String, List<Pharmacy>>
) {
    /**
     * Get pharmacies for a specific ZBS
     */
    fun pharmaciesForZBS(zbsId: String): List<Pharmacy> {
        return schedulesByZBS[zbsId] ?: emptyList()
    }
    
    /**
     * Get all available ZBS IDs for this date
     */
    val availableZBSIds: List<String>
        get() = schedulesByZBS.keys.sorted()
}
