import Foundation
import CoreLocation

public struct Pharmacy: Identifiable {
    public let id = UUID()
    public let name: String
    public let address: String
    public let phone: String
    public let additionalInfo: String?
    
    public var formattedPhone: String {
        let cleanNumber = phone.replacingOccurrences(of: " ", with: "")
        var result = ""
        var count = 0
        
        for char in cleanNumber {
            if count > 0 && count % 3 == 0 {
                result += " "
            }
            result += String(char)
            count += 1
        }
        
        return result.trimmingCharacters(in: .whitespaces)
    }
    
    public init(name: String, address: String, phone: String, additionalInfo: String?) {
        self.name = name
        self.address = address
        self.phone = phone
        self.additionalInfo = additionalInfo
    }
}

extension Pharmacy {
    public static func parse(from lines: [String]) -> Pharmacy? {
        DebugConfig.debugPrint("\nAttempting to parse single pharmacy from \(lines.count) lines")
        
        // Clean up and filter lines
        let nonEmptyLines = lines.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        
        guard !nonEmptyLines.isEmpty else {
            DebugConfig.debugPrint("Warning: No valid lines to parse")
            return nil
        }
        
        // Print the lines we're working with
        DebugConfig.debugPrint("\nInput lines after cleanup:")
        nonEmptyLines.enumerated().forEach { index, line in
            DebugConfig.debugPrint("Line \(index): \(line)")
        }
        
        // Find the pharmacy name (must start with "FARMACIA")
        guard let nameIndex = nonEmptyLines.firstIndex(where: { $0.contains("FARMACIA") }) else {
            DebugConfig.debugPrint("Error: No valid pharmacy name found (must contain 'FARMACIA')")
            return nil
        }
        
        let name = nonEmptyLines[nameIndex]
        DebugConfig.debugPrint("\nFound pharmacy name at index \(nameIndex): \(name)")
        
        // Get lines after the name
        let remainingLines = Array(nonEmptyLines.dropFirst(nameIndex + 1))
        guard !remainingLines.isEmpty else {
            DebugConfig.debugPrint("Error: No address or additional information found after pharmacy name")
            return nil
        }
        
        // First line after name is the address
        let address = remainingLines[0]
        if address.isEmpty {
            DebugConfig.debugPrint("Warning: Empty address for pharmacy: \(name)")
        } else {
            DebugConfig.debugPrint("Found address: \(address)")
        }
        
        // Remaining lines contain phone and additional info
        let infoLines = remainingLines.dropFirst().joined(separator: " ")
        
        // Extract phone number if present
        var phone = ""
        var additionalInfo = infoLines
        
        if let phoneMatch = infoLines.range(of: "Tfno:\\s*\\d{3}\\s*\\d{6}", options: .regularExpression) {
            phone = String(infoLines[phoneMatch])
                .replacingOccurrences(of: "Tfno:", with: "")
                .trimmingCharacters(in: .whitespaces)
            additionalInfo = infoLines
                .replacingOccurrences(of: String(infoLines[phoneMatch]), with: "")
                .trimmingCharacters(in: .whitespaces)
            DebugConfig.debugPrint("Extracted phone number: \(phone)")
        } else {
            DebugConfig.debugPrint("No phone number found in additional info")
        }
        
        // Only keep additional info if it's not empty
        let finalAdditionalInfo = additionalInfo.isEmpty ? nil : additionalInfo
        if let info = finalAdditionalInfo {
            DebugConfig.debugPrint("Additional info found: \(info)")
        }
        
        DebugConfig.debugPrint("Successfully parsed pharmacy: \(name)")
        
        return Pharmacy(
            name: name,
            address: address,
            phone: phone,
            additionalInfo: finalAdditionalInfo
        )
    }
    
    public static func parseBatch(from lines: [String]) -> [Pharmacy] {
        DebugConfig.debugPrint("\nParsing batch of \(lines.count) lines")
        
        // Clean up and filter lines first
        let cleanLines = lines.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        
        guard !cleanLines.isEmpty else {
            DebugConfig.debugPrint("Warning: No valid lines to parse")
            return []
        }
        
        if cleanLines.count % 3 != 0 {
            DebugConfig.debugPrint("Warning: Number of lines (\(cleanLines.count)) is not divisible by 3. Some entries may be incomplete.")
        }
        
        var pharmacies: [Pharmacy] = []
        var currentGroup: [String] = []
        
        // Process lines in groups of 3
        for (index, line) in cleanLines.enumerated() {
            currentGroup.append(line)
            
            if currentGroup.count == 3 {
                // Remember: lines are [Additional info, Address, Name] in this order
                let additionalInfo = currentGroup[0]
                let address = currentGroup[1]
                let name = currentGroup[2]
                
                DebugConfig.debugPrint("\nProcessing pharmacy group at index \(index - 2):")
                DebugConfig.debugPrint("Name: \(name)")
                DebugConfig.debugPrint("Address: \(address)")
                DebugConfig.debugPrint("Info: \(additionalInfo)")
                
                // Validate pharmacy name format
                if !name.contains("FARMACIA") {
                    DebugConfig.debugPrint("Warning: Skipping entry - Invalid pharmacy name format: \(name)")
                    currentGroup = []
                    continue
                }
                
                // Extract phone from additional info if present
                var phone = ""
                var finalAdditionalInfo = additionalInfo
                
                if let phoneMatch = additionalInfo.range(of: "Tfno:\\s*\\d{3}\\s*\\d{6}", options: .regularExpression) {
                    phone = String(additionalInfo[phoneMatch])
                        .replacingOccurrences(of: "Tfno:", with: "")
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    finalAdditionalInfo = additionalInfo
                        .replacingOccurrences(of: String(additionalInfo[phoneMatch]), with: "")
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                    DebugConfig.debugPrint("Extracted phone: \(phone)")
                } else {
                    DebugConfig.debugPrint("No phone number found in additional info")
                }
                
                // Validate address
                if address.isEmpty {
                    DebugConfig.debugPrint("Warning: Empty address for pharmacy: \(name)")
                }
                
                // Create pharmacy
                pharmacies.append(Pharmacy(
                    name: name,
                    address: address,
                    phone: phone,
                    additionalInfo: finalAdditionalInfo.isEmpty ? nil : finalAdditionalInfo
                ))
                DebugConfig.debugPrint("Successfully parsed pharmacy: \(name)")
                
                // Start new group
                currentGroup = []
            }
        }
        
        return pharmacies
    }
}

// MARK: - Location Extensions
extension Pharmacy {
    /// Calculate distance from user location to this pharmacy
    func distance(from userLocation: CLLocation) async -> CLLocationDistance? {
        guard let pharmacyLocation = await GeocodingService.getCoordinates(for: self.address) else {
            DebugConfig.debugPrint("❌ Could not geocode pharmacy address: \(self.address)")
            return nil
        }
        
        let distance = userLocation.distance(from: pharmacyLocation)
        DebugConfig.debugPrint("📏 Distance to \(self.name): \(Int(distance))m")
        return distance
    }
    
    /// Get coordinates for this pharmacy
    func coordinates() async -> CLLocation? {
        return await GeocodingService.getCoordinates(for: self.address)
    }
}
