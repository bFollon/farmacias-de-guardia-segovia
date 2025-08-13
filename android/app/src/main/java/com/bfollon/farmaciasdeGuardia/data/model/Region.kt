package com.bfollon.farmaciasdeGuardia.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a geographical region or location that has its own pharmacy duty schedule
 * Equivalent to iOS Region.swift
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
    val metadata: RegionMetadata = RegionMetadata()
) {
    companion object {
        /** The default region (Segovia Capital) */
        val SEGOVIA_CAPITAL = Region(
            id = "segovia-capital",
            name = "Segovia Capital",
            icon = "🏙",
            pdfURL = "https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf",
            metadata = RegionMetadata(
                has24HourPharmacies = false,
                isMonthlySchedule = false,
                notes = "Includes both day and night shifts"
            )
        )
        
        /** Cuéllar region */
        val CUELLAR = Region(
            id = "cuellar",
            name = "Cuéllar",
            icon = "🌳",
            pdfURL = "https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf",
            metadata = RegionMetadata(
                isMonthlySchedule = false,  // Cuéllar uses weekly schedules
                notes = "Servicios semanales excepto primera semana de septiembre"
            )
        )
        
        /** El Espinar region */
        val EL_ESPINAR = Region(
            id = "el-espinar",
            name = "El Espinar / San Rafael",
            icon = "⛰",
            pdfURL = "https://cofsegovia.com/wp-content/uploads/2025/01/Guardias-EL-ESPINAR_2025.pdf",
            metadata = RegionMetadata(
                isMonthlySchedule = false,  // Uses weekly schedules like Cuéllar
                notes = "Servicios semanales"
            )
        )
        
        /** Segovia Rural region */
        val SEGOVIA_RURAL = Region(
            id = "segovia-rural",
            name = "Segovia Rural",
            icon = "🚜",
            pdfURL = "https://cofsegovia.com/wp-content/uploads/2025/06/SERVICIOS-DE-URGENCIA-RURALES-2025.pdf",
            metadata = RegionMetadata(
                isMonthlySchedule = false,
                notes = "Servicios de urgencia rurales"
            )
        )
        
        /** List of all available regions */
        val ALL_REGIONS = listOf(
            SEGOVIA_CAPITAL,
            CUELLAR,
            EL_ESPINAR,
            SEGOVIA_RURAL
        )
    }
}

/**
 * Additional configuration and metadata for a region
 * Equivalent to iOS RegionMetadata
 */
@Serializable
data class RegionMetadata(
    /** Whether this region has 24-hour pharmacies that are always open */
    val has24HourPharmacies: Boolean = false,
    
    /** Whether this region's schedule changes monthly */
    val isMonthlySchedule: Boolean = false,
    
    /** Any special notes about this region's pharmacy schedule */
    val notes: String? = null
)
