/*
 * Farmacias de Guardia - Segovia
 * Copyright (C) 2024 Bruno Follón
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.bfollon.farmaciasdeGuardia.services.pdfparsing

import com.bfollon.farmaciasdeGuardia.data.model.PharmacySchedule
import org.apache.pdfbox.pdmodel.PDDocument

/**
 * Interface defining the contract for PDF schedule parsing strategies.
 * Each region/location should implement its own strategy to handle its specific PDF format.
 * 
 * Android equivalent of iOS PDFParsingStrategy protocol.
 */
interface PDFParsingStrategy {
    /**
     * Parse a PDF document and extract pharmacy schedules
     * @param pdf The PDF document to parse
     * @return List of pharmacy schedules extracted from the PDF
     */
    fun parseSchedules(pdf: PDDocument): List<PharmacySchedule>
    
    /**
     * Get the name of this parsing strategy for debugging
     */
    fun getStrategyName(): String
}
