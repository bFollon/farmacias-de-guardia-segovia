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

import kotlinx.serialization.Serializable
import java.util.Date

/**
 * Represents a pharmacy schedule for a specific date with different duty shifts
 * Equivalent to iOS PharmacySchedule.swift
 */
@Serializable
data class PharmacySchedule(
    val date: DutyDate,
    val shifts: Map<DutyTimeSpan, List<Pharmacy>>
) {
    /**
     * Convenience constructor for backward compatibility during transition
     */
    constructor(
        date: DutyDate,
        dayShiftPharmacies: List<Pharmacy>,
        nightShiftPharmacies: List<Pharmacy>
    ) : this(
        date = date,
        shifts = mapOf(
            DutyTimeSpan.CapitalDay to dayShiftPharmacies,
            DutyTimeSpan.CapitalNight to nightShiftPharmacies
        )
    )
    
    /**
     * Backward compatibility properties (can be removed after UI is updated)
     */
    val dayShiftPharmacies: List<Pharmacy>
        get() {
            // Try capital-specific shifts first, then fall back to full day
            return shifts[DutyTimeSpan.CapitalDay] ?: shifts[DutyTimeSpan.FullDay] ?: emptyList()
        }
    
    val nightShiftPharmacies: List<Pharmacy>
        get() {
            // Try capital-specific shifts first, then fall back to full day (for 24-hour regions)
            return shifts[DutyTimeSpan.CapitalNight] ?: shifts[DutyTimeSpan.FullDay] ?: emptyList()
        }
}
