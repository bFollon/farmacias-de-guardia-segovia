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
 * Represents a ZBS (Zona Básica de Salud) within Segovia Rural region
 */
data class ZBS(
    /** Unique identifier for the ZBS */
    val id: String,

    /** Display name of the ZBS */
    val name: String,

    /** Emoji icon representing the ZBS area */
    val icon: String,

    /** Additional notes about this ZBS */
    val notes: String? = null
) {
    companion object {
        /** List of all available ZBS areas in Segovia Rural */

        val RIAZA_SEPULVEDA = ZBS(
            id = "riaza-sepulveda",
            name = "Riaza / Sepúlveda",
            icon = "🏔️",
            notes = "Mountain highland area"
        )
        val LA_GRANJA = ZBS(
            id = "la-granja",
            name = "La Granja",
            icon = "🏰",
            notes = "Historic palace town area"
        )
        val LA_SIERRA = ZBS(
            id = "la-sierra",
            name = "La Sierra",
            icon = "⛰️",
            notes = "Mountain range area"
        )
        val FUENTIDUENA = ZBS(
            id = "fuentiduena",
            name = "Fuentidueña",
            icon = "🏞️",
            notes = "Valley countryside area"
        )
        val CARBONERO = ZBS(
            id = "carbonero",
            name = "Carbonero",
            icon = "🌲",
            notes = "Forest region area"
        )
        val NAVAS_DE_LA_ASUNCION = ZBS(
            id = "navas-asuncion",
            name = "Navas de la Asunción",
            icon = "🏘️",
            notes = "Small town area"
        )
        val VILLACASTIN = ZBS(
            id = "villacastin",
            name = "Villacastín",
            icon = "🚂",
            notes = "Railway junction town"
        )
        val CANTALEJO = ZBS(
            id = "cantalejo",
            name = "Cantalejo",
            icon = "🏘️",
            notes = "Rural town area"
        )
        val availableZBS = listOf(
            RIAZA_SEPULVEDA,
            LA_GRANJA,
            LA_SIERRA,
            FUENTIDUENA,
            CARBONERO,
            NAVAS_DE_LA_ASUNCION,
            VILLACASTIN,
            CANTALEJO
        )
    }
}
