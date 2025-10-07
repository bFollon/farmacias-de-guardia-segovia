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

import com.github.bfollon.farmaciasdeguardiaensegovia.services.DebugConfig
import kotlinx.serialization.Serializable

/**
 * Represents a geographical region or location that has its own pharmacy duty schedule
 */
@Serializable
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
        /**
         * Set the URL provider for regions
         * Must be called during app initialization before creating regions
         */
        private var urlProvider: ((String) -> String)? = null
        
        fun setURLProvider(provider: (String) -> String) {
            urlProvider = provider
        }
        
        private fun getURL(regionName: String, fallback: String): String {
            return urlProvider?.invoke(regionName) ?: fallback
        }
        
        /** The default region (Segovia Capital) */
        val segoviaCapital: Region
            get() {
                val finalURL = getURL(
                    "Segovia Capital",
                    "https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf"
                )
                return Region(
                    id = "segovia-capital",
                    name = "Segovia Capital",
                    icon = "🏙",
                    pdfURL = finalURL,
                    metadata = RegionMetadata(
                        has24HourPharmacies = false,
                        notes = "Includes both day and night shifts"
                    )
                )
            }
        
        /** Cuéllar region */
        val cuellar: Region
            get() {
                val finalURL = getURL(
                    "Cuéllar",
                    "https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf"
                )
                return Region(
                    id = "cuellar",
                    name = "Cuéllar",
                    icon = "🌳",
                    pdfURL = finalURL,
                    metadata = RegionMetadata(
                        notes = "Servicios semanales excepto primera semana de septiembre"
                    )
                )
            }
        
        /** El Espinar region */
        val elEspinar: Region
            get() {
                val finalURL = getURL(
                    "El Espinar",
                    "https://cofsegovia.com/wp-content/uploads/2025/01/Guardias-EL-ESPINAR_2025.pdf"
                )
                return Region(
                    id = "el-espinar",
                    name = "El Espinar / San Rafael",
                    icon = "🏔️",
                    pdfURL = finalURL,
                    metadata = RegionMetadata(
                        notes = "Servicios semanales"
                    )
                )
            }
        
        /** Segovia Rural region */
        val segoviaRural: Region
            get() {
                val finalURL = getURL(
                    "Segovia Rural",
                    "https://cofsegovia.com/wp-content/uploads/2025/06/SERVICIOS-DE-URGENCIA-RURALES-2025.pdf"
                )
                return Region(
                    id = "segovia-rural",
                    name = "Segovia Rural",
                    icon = "🚜",
                    pdfURL = finalURL,
                    metadata = RegionMetadata(
                        notes = "Servicios de urgencia rurales"
                    )
                )
            }
        
        /** List of all available regions */
        val allRegions = listOf(segoviaCapital, cuellar, elEspinar, segoviaRural)
    }

    fun toDutyLocationList(): List<DutyLocation> = when(id) {
        segoviaRural.id -> { ZBS.availableZBS.map { DutyLocation.fromZBS(it) } }
        else -> listOf(DutyLocation.fromRegion(this))
    }
}

/**
 * Additional configuration and metadata for a region
 */
@Serializable
data class RegionMetadata(
    /** Whether this region has 24-hour pharmacies that are always open */
    val has24HourPharmacies: Boolean = false,
    
    /** Any special notes about this region's pharmacy schedule */
    val notes: String? = null
)
