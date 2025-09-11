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

import com.example.farmaciasdeguardiaensegovia.services.PDFURLScrapingService
import com.example.farmaciasdeguardiaensegovia.services.DebugConfig

/**
 * Represents a geographical region or location that has its own pharmacy duty schedule
 */
data class Region(
    /** Unique identifier for the region, used to match with PDF parsing strategies */
    val id: String,
    
    /** Display name of the region (e.g., "Segovia Capital", "Cuéllar") */
    val name: String,
    
    /** Emoji icon representing the region */
    val icon: String,
    
    /** URL to the PDF file containing the schedule for this region */
    val pdfURL: String,
    
    /** Additional metadata about the region */
    val metadata: RegionMetadata = RegionMetadata(),
    
    /** Whether to force refresh this region's cache on every load (useful for testing) */
    val forceRefresh: Boolean = false
) {
    companion object {
        /** The default region (Segovia Capital) */
        val segoviaCapital: Region
            get() {
                val scrapedURL = PDFURLScrapingService.getScrapedURL("Segovia Capital")
                val finalURL = scrapedURL ?: "https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf"
                if (scrapedURL != null) {
                    DebugConfig.debugPrint("Region: Using SCRAPED URL for Segovia Capital: $scrapedURL")
                } else {
                    DebugConfig.debugPrint("Region: Using FALLBACK URL for Segovia Capital: $finalURL")
                }
                return Region(
                    id = "segovia-capital",
                    name = "Segovia Capital",
                    icon = "🏙",
                    pdfURL = finalURL,
                    metadata = RegionMetadata(
                        has24HourPharmacies = false,
                        isMonthlySchedule = false,
                        notes = "Includes both day and night shifts"
                    )
                )
            }
        
        /** Cuéllar region */
        val cuellar: Region
            get() = Region(
                id = "cuellar",
                name = "Cuéllar",
                icon = "🌳",
                pdfURL = PDFURLScrapingService.getScrapedURL("Cuéllar") ?: "https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf",
                metadata = RegionMetadata(
                    isMonthlySchedule = false,  // Cuéllar uses weekly schedules
                    notes = "Servicios semanales excepto primera semana de septiembre"
                ),
                forceRefresh = false
            )
        
        /** El Espinar region */
        val elEspinar: Region
            get() = Region(
                id = "el-espinar",
                name = "El Espinar / San Rafael",
                icon = "🏔️",
                pdfURL = PDFURLScrapingService.getScrapedURL("El Espinar") ?: "https://cofsegovia.com/wp-content/uploads/2025/01/Guardias-EL-ESPINAR_2025.pdf",
                metadata = RegionMetadata(
                    isMonthlySchedule = false,  // Uses weekly schedules like Cuéllar
                    notes = "Servicios semanales"
                ),
            )
        
        /** Segovia Rural region */
        val segoviaRural: Region
            get() = Region(
                id = "segovia-rural",
                name = "Segovia Rural",
                icon = "🚜",
                pdfURL = PDFURLScrapingService.getScrapedURL("Segovia Rural") ?: "https://cofsegovia.com/wp-content/uploads/2025/06/SERVICIOS-DE-URGENCIA-RURALES-2025.pdf",
                metadata = RegionMetadata(
                    isMonthlySchedule = false,
                    notes = "Servicios de urgencia rurales"
                )
            )
        
        /** List of all available regions */
        val allRegions = listOf(segoviaCapital, cuellar, elEspinar, segoviaRural)
    }
}

/**
 * Additional configuration and metadata for a region
 */
data class RegionMetadata(
    /** Whether this region has 24-hour pharmacies that are always open */
    val has24HourPharmacies: Boolean = false,
    
    /** Whether this region's schedule changes monthly */
    val isMonthlySchedule: Boolean = false,
    
    /** Any special notes about this region's pharmacy schedule */
    val notes: String? = null
)
