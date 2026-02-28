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
 * Represents a single factor that contributes to (or reduces) the confidence score.
 * Each subclass carries the relevant data for display in the breakdown sheet.
 */
sealed class ConfidenceFactor {
    /** Net deduction this factor applies (positive = bad, reduces score). */
    abstract val deduction: Double

    /** Whether this factor is actively lowering the score. */
    val isIssue: Boolean get() = deduction > 0.0

    /** Localised Spanish label for the breakdown sheet. */
    abstract val localizedTitle: String

    data class ScrapingFailed(override val deduction: Double) : ConfidenceFactor() {
        override val localizedTitle = "Comprobación de URLs fallida"
    }

    data class ScrapingAge(val days: Int, override val deduction: Double) : ConfidenceFactor() {
        override val localizedTitle: String
            get() = if (days == 0) "URLs verificadas recientemente"
                    else "URLs no verificadas hace $days días"
    }

    data class PendingPDFUpdate(override val deduction: Double) : ConfidenceFactor() {
        override val localizedTitle = "Hay una actualización pendiente de los PDFs"
    }

    data class LowScheduleCount(
        val actual: Int,
        val expected: Int,
        override val deduction: Double
    ) : ConfidenceFactor() {
        override val localizedTitle = "Pocos horarios ($actual de ~$expected esperados)"
    }

    data class NoCurrentYearSchedules(override val deduction: Double) : ConfidenceFactor() {
        override val localizedTitle = "Sin horarios para el año actual"
    }

    data class MissingDaysNearToday(
        val missingDays: Int,
        override val deduction: Double
    ) : ConfidenceFactor() {
        override val localizedTitle: String
            get() = "$missingDays día${if (missingDays == 1) "" else "s"} sin horario cerca de hoy"
    }
}
