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

package com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing

import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import java.io.File

/**
 * Interface for PDF parsing strategies
 * Now using File instead of PDDocument for iText compatibility
 */
interface PDFParsingStrategy {
    
    /**
     * Parse schedules from a PDF file
     * @param pdfFile The PDF file to parse
     * @return List of parsed pharmacy schedules
     */
    fun parseSchedules(pdfFile: File): Map<DutyLocation, List<PharmacySchedule>>
    
    /**
     * Get the name of this parsing strategy (for debugging)
     */
    fun getStrategyName(): String
}
