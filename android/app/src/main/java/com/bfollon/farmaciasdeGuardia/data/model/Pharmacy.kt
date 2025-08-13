package com.bfollon.farmaciasdeGuardia.data.model

import android.location.Location
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import kotlinx.serialization.Serializable
import java.util.UUID

@Entity(tableName = "pharmacies")
@Serializable
data class Pharmacy(
    @PrimaryKey
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
            DebugConfig.debugPrint("\nAttempting to parse single pharmacy from ${lines.size} lines")
            
            // Clean up and filter lines
            val nonEmptyLines = lines.map { it.trim() }
                .filter { it.isNotEmpty() }
            
            if (nonEmptyLines.isEmpty()) {
                DebugConfig.debugPrint("Warning: No valid lines to parse")
                return null
            }
            
            // Print the lines we're working with
            DebugConfig.debugPrint("\nInput lines after cleanup:")
            nonEmptyLines.forEachIndexed { index, line ->
                DebugConfig.debugPrint("Line $index: $line")
            }
            
            // Find the pharmacy name (must contain "FARMACIA")
            val nameIndex = nonEmptyLines.indexOfFirst { it.contains("FARMACIA") }
            if (nameIndex == -1) {
                DebugConfig.debugPrint("Error: No valid pharmacy name found (must contain 'FARMACIA')")
                return null
            }
            
            val name = nonEmptyLines[nameIndex]
            DebugConfig.debugPrint("\nFound pharmacy name at index $nameIndex: $name")
            
            // Get lines after the name
            val remainingLines = nonEmptyLines.drop(nameIndex + 1)
            if (remainingLines.isEmpty()) {
                DebugConfig.debugPrint("Error: No address or additional information found after pharmacy name")
                return null
            }
            
            // First line after name is the address
            val address = remainingLines[0]
            if (address.isEmpty()) {
                DebugConfig.debugPrint("Warning: Empty address for pharmacy: $name")
            } else {
                DebugConfig.debugPrint("Found address: $address")
            }
            
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
                DebugConfig.debugPrint("Extracted phone number: $phone")
            } else {
                DebugConfig.debugPrint("No phone number found in additional info")
            }
            
            // Only keep additional info if it's not empty
            val finalAdditionalInfo = if (additionalInfo.isEmpty()) null else additionalInfo
            finalAdditionalInfo?.let { info ->
                DebugConfig.debugPrint("Additional info found: $info")
            }
            
            DebugConfig.debugPrint("Successfully parsed pharmacy: $name")
            
            return Pharmacy(
                name = name,
                address = address,
                phone = phone,
                additionalInfo = finalAdditionalInfo
            )
        }

        /**
         * Parse multiple pharmacies from a batch of text lines
         * Equivalent to iOS Pharmacy.parseBatch(from:)
         */
        fun parseBatch(lines: List<String>): List<Pharmacy> {
            DebugConfig.debugPrint("\nParsing batch of ${lines.size} lines")
            
            // Clean up and filter lines first
            val cleanLines = lines.map { it.trim() }
                .filter { it.isNotEmpty() }
            
            if (cleanLines.isEmpty()) {
                DebugConfig.debugPrint("Warning: No valid lines to parse")
                return emptyList()
            }
            
            if (cleanLines.size % 3 != 0) {
                DebugConfig.debugPrint("Warning: Number of lines (${cleanLines.size}) is not divisible by 3. Some entries may be incomplete.")
            }
            
            val pharmacies = mutableListOf<Pharmacy>()
            val currentGroup = mutableListOf<String>()
            
            // Process lines in groups of 3
            for ((index, line) in cleanLines.withIndex()) {
                currentGroup.add(line)
                
                if (currentGroup.size == 3) {
                    // Remember: lines are [Additional info, Address, Name] in this order
                    val additionalInfo = currentGroup[0]
                    val address = currentGroup[1]
                    val name = currentGroup[2]
                    
                    DebugConfig.debugPrint("\nProcessing pharmacy group at index ${index - 2}:")
                    DebugConfig.debugPrint("Name: $name")
                    DebugConfig.debugPrint("Address: $address")
                    DebugConfig.debugPrint("Info: $additionalInfo")
                    
                    // Validate pharmacy name format
                    if (!name.contains("FARMACIA")) {
                        DebugConfig.debugPrint("Warning: Skipping entry - Invalid pharmacy name format: $name")
                        currentGroup.clear()
                        continue
                    }
                    
                    // Extract phone from additional info if present
                    var phone = ""
                    var finalAdditionalInfo = additionalInfo
                    
                    val phoneRegex = Regex("Tfno:\\s*\\d{3}\\s*\\d{6}")
                    val phoneMatch = phoneRegex.find(additionalInfo)
                    
                    if (phoneMatch != null) {
                        phone = phoneMatch.value
                            .replace("Tfno:", "")
                            .trim()
                        finalAdditionalInfo = additionalInfo
                            .replace(phoneMatch.value, "")
                            .trim()
                        DebugConfig.debugPrint("Extracted phone: $phone")
                    } else {
                        DebugConfig.debugPrint("No phone number found in additional info")
                    }
                    
                    // Validate address
                    if (address.isEmpty()) {
                        DebugConfig.debugPrint("Warning: Empty address for pharmacy: $name")
                    }
                    
                    // Create pharmacy
                    pharmacies.add(
                        Pharmacy(
                            name = name,
                            address = address,
                            phone = phone,
                            additionalInfo = if (finalAdditionalInfo.isEmpty()) null else finalAdditionalInfo
                        )
                    )
                    DebugConfig.debugPrint("Successfully parsed pharmacy: $name")
                    
                    // Start new group
                    currentGroup.clear()
                }
            }
            
            return pharmacies
        }
    }
}

/**
 * Extension functions for location-related operations
 * Equivalent to iOS Location Extensions
 */
suspend fun Pharmacy.distanceFrom(userLocation: Location): Float? {
    // This will be implemented when we create the GeocodingService
    // For now, return null - we'll implement this in the service layer
    return null
}

suspend fun Pharmacy.coordinates(): Location? {
    // This will be implemented when we create the GeocodingService
    // For now, return null - we'll implement this in the service layer
    return null
}
