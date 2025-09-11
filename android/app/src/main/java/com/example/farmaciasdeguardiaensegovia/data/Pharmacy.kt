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

import com.example.farmaciasdeguardiaensegovia.services.DebugConfig
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.collections.plus

@Serializable
data class Pharmacy(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val phone: String,
    val additionalInfo: String? = null
) {
    /**
     * Format phone number for display with spaces every 3 digits
     */
    val formattedPhone: String
        get() {
            val cleanNumber = phone.replace(" ", "")
            return cleanNumber.chunked(3).joinToString(" ").trim()
        }

    companion object {
        private val phoneRegex = Regex("Tfno:\\s*(\\d{3}\\s*\\d{6})")

        fun parse(name: String, address: String, additionalInfoRaw: String): Pharmacy {
            val phoneMatch = phoneRegex.find(additionalInfoRaw)
            return Pharmacy(
                name = name,
                address = address,
                phone = phoneMatch?.groupValues?.getOrNull(1) ?: "",
                additionalInfo = additionalInfoRaw
                    .replace(phoneMatch?.value ?: "", "")
                    .ifEmpty { null }
            )
        }

        fun parseBatch(unparsed: List<Triple<String, String, String>>): List<Pharmacy> {
            DebugConfig.debugPrint("🏥 Parsing batch of ${unparsed.size} data chunks")

            return unparsed.fold(emptyList<Pharmacy>()) { acc, (name, address, additionalInfo) ->
                val phoneMatch = phoneRegex.find(additionalInfo)
                acc + Pharmacy(
                    name = name,
                    address = address,
                    phone = phoneMatch?.groupValues?.getOrNull(1) ?: "",
                    additionalInfo = additionalInfo
                        .replace(phoneMatch?.value ?: "", "")
                        .ifEmpty { null }
                )
            }
        }
    }
}
