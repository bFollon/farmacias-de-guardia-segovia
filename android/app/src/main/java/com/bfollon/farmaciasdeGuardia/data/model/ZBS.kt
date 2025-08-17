package com.bfollon.farmaciasdeGuardia.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a Zona Básica de Salud (Basic Health Zone) for Segovia Rural
 * Equivalent to iOS ZBS.swift
 */
@Serializable
data class ZBS(
    /** Unique identifier for the ZBS */
    val id: String,
    
    /** Display name of the ZBS */
    val name: String,
    
    /** Emoji icon for the ZBS */
    val icon: String
) {
    companion object {
        /** Available ZBS options for Segovia Rural */
        val AVAILABLE_ZBS = listOf(
            ZBS(id = "riaza-sepulveda", name = "Riaza / Sepúlveda", icon = "🏔️"),
            ZBS(id = "la-granja", name = "La Granja", icon = "🏰"),
            ZBS(id = "la-sierra", name = "La Sierra", icon = "⛰️"),
            ZBS(id = "fuentidueña", name = "Fuentidueña", icon = "🏞️"),
            ZBS(id = "carbonero", name = "Carbonero", icon = "🌲"),
            ZBS(id = "navas-asuncion", name = "Navas de la Asunción", icon = "🏘️"),
            ZBS(id = "villacastin", name = "Villacastín", icon = "🚂"),
            ZBS(id = "cantalejo", name = "Cantalejo", icon = "🏘️")
        )
        
        /**
         * Find ZBS by ID
         */
        fun findById(id: String): ZBS? {
            return AVAILABLE_ZBS.find { it.id == id }
        }
        
        /**
         * Find ZBS by name
         */
        fun findByName(name: String): ZBS? {
            return AVAILABLE_ZBS.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}
