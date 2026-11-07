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

import kotlinx.serialization.Serializable

@Serializable
data class Pharmacy(
    val id: String,
    val name: String,
    val address: String,
    val phone: String,
    val additionalInfo: String? = null,
    // From the server's pharmacy catalog. Absent for pharmacies the catalog doesn't cover
    // yet, and in schedules cached by older app versions.
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    /**
     * Format phone number for display with spaces every 3 digits
     */
    val formattedPhone: String
        get() {
            val cleanNumber = phone.replace(" ", "")
            return cleanNumber.chunked(3).joinToString(" ").trim()
        }
}
