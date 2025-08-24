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

import kotlinx.serialization.Serializable
import java.util.UUID

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
        /**
         * Parse a single pharmacy from a list of text lines
         * Equivalent to iOS Pharmacy.parse(from:)
         */
        fun parse(lines: List<String>): Pharmacy? {
            println("Attempting to parse single pharmacy from ${lines.size} lines")
            
            // Clean up and filter lines
            val nonEmptyLines = lines.map { it.trim() }
                .filter { it.isNotEmpty() }
            
            if (nonEmptyLines.isEmpty()) {
                println("Warning: No valid lines to parse")
                return null
            }
            
            // Find the pharmacy name (must contain "FARMACIA")
            val nameIndex = nonEmptyLines.indexOfFirst { it.contains("FARMACIA", ignoreCase = true) }
            if (nameIndex == -1) {
                println("Error: No valid pharmacy name found (must contain 'FARMACIA')")
                return null
            }
            
            val name = nonEmptyLines[nameIndex]
            println("Found pharmacy name at index $nameIndex: $name")
            
            // Get lines after the name
            val remainingLines = nonEmptyLines.drop(nameIndex + 1)
            if (remainingLines.isEmpty()) {
                println("Error: No address or additional information found after pharmacy name")
                return null
            }
            
            // First line after name is the address
            val address = remainingLines[0]
            println("Found address: $address")
            
            // Remaining lines contain phone and additional info
            val infoLines = remainingLines.drop(1).joinToString(" ")
            
            // Extract phone number if present
            var phone = ""
            var additionalInfo = infoLines
            
            val phoneRegex = Regex("Tfno:\\s*\\d{3}\\s*\\d{6}")
            val phoneMatch = phoneRegex.find(infoLines)
            
            if (phoneMatch != null) {
                phone = phoneMatch.value
                    .replace("Tfno:", "")
                    .trim()
                additionalInfo = infoLines
                    .replace(phoneMatch.value, "")
                    .trim()
                println("Extracted phone number: $phone")
            }
            
            // Only keep additional info if it's not empty
            val finalAdditionalInfo = if (additionalInfo.isEmpty()) null else additionalInfo
            
            return Pharmacy(
                name = name,
                address = address,
                phone = phone,
                additionalInfo = finalAdditionalInfo
            )
        }

        /**
         * Parse multiple pharmacies from a batch of text lines
         * Following iOS implementation - groups of 3 lines: [additionalInfo, address, name]
         */
        fun parseBatch(lines: List<String>): List<Pharmacy> {
            println("🏥 Parsing batch of ${lines.size} lines")
            
            // Clean up and filter lines first
            val cleanLines = lines.map { it.trim() }
                .filter { it.isNotEmpty() }
            
            if (cleanLines.isEmpty()) {
                println("⚠️ Warning: No valid lines to parse")
                return emptyList()
            }
            
            if (cleanLines.size % 3 != 0) {
                println("⚠️ Warning: Number of lines (${cleanLines.size}) is not divisible by 3. Some entries may be incomplete.")
            }
            
            val pharmacies = mutableListOf<Pharmacy>()
            val currentGroup = mutableListOf<String>()
            
            // Process lines in groups of 3
            for ((index, line) in cleanLines.withIndex()) {
                currentGroup.add(line)
                
                if (currentGroup.size == 3) {
                    // iOS order: [additionalInfo, address, name]
                    val additionalInfo = currentGroup[0]
                    val address = currentGroup[1]
                    val name = currentGroup[2]
                    
                    println("\n🔍 Processing pharmacy group at index ${index - 2}:")
                    println("   📛 Name: $name")
                    println("   📍 Address: $address")  
                    println("   ℹ️ Info: $additionalInfo")
                    
                    // Validate pharmacy name format
                    if (!name.contains("FARMACIA", ignoreCase = true)) {
                        println("⚠️ Warning: Skipping entry - Invalid pharmacy name format: $name")
                        currentGroup.clear()
                        continue
                    }
                    
                    // Extract phone from additional info if present
                    var phone = ""
                    var finalAdditionalInfo = additionalInfo
                    
                    val phoneRegex = Regex("Tfno:\\s*(\\d{3}\\s*\\d{6})")
                    val phoneMatch = phoneRegex.find(additionalInfo)
                    
                    if (phoneMatch != null) {
                        phone = phoneMatch.groupValues[1].trim()
                        finalAdditionalInfo = additionalInfo
                            .replace(phoneMatch.value, "")
                            .trim()
                        println("   📞 Extracted phone: $phone")
                    } else {
                        println("   📞 No phone number found in additional info")
                    }
                    
                    // Validate address
                    if (address.isEmpty()) {
                        println("⚠️ Warning: Empty address for pharmacy: $name")
                    }
                    
                    // Create pharmacy
                    pharmacies.add(Pharmacy(
                        name = name,
                        address = address,
                        phone = phone,
                        additionalInfo = if (finalAdditionalInfo.isEmpty()) null else finalAdditionalInfo
                    ))
                    println("✅ Successfully parsed pharmacy: $name")
                    
                    // Start new group
                    currentGroup.clear()
                }
            }
            
            println("🏥 Final result: parsed ${pharmacies.size} pharmacies")
            return pharmacies
        }
    }
}
