import Foundation

public struct Pharmacy: Identifiable {
    public let id = UUID()
    public let name: String
    public let address: String
    public let phone: String
    public let additionalInfo: String?
    
    public init(name: String, address: String, phone: String, additionalInfo: String?) {
        self.name = name
        self.address = address
        self.phone = phone
        self.additionalInfo = additionalInfo
    }
}

extension Pharmacy {
    public static func parse(from lines: [String]) -> Pharmacy? {
        print("\nAttempting to parse single pharmacy from \(lines.count) lines")
        
        // Clean up and filter lines
        let nonEmptyLines = lines.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        
        guard !nonEmptyLines.isEmpty else {
            print("Warning: No valid lines to parse")
            return nil
        }
        
        // Print the lines we're working with
        print("\nInput lines after cleanup:")
        nonEmptyLines.enumerated().forEach { index, line in
            print("Line \(index): \(line)")
        }
        
        // Find the pharmacy name (must start with "FARMACIA")
        guard let nameIndex = nonEmptyLines.firstIndex(where: { $0.contains("FARMACIA") }) else {
            print("Error: No valid pharmacy name found (must contain 'FARMACIA')")
            return nil
        }
        
        let name = nonEmptyLines[nameIndex]
        print("\nFound pharmacy name at index \(nameIndex): \(name)")
        
        // Get lines after the name
        let remainingLines = Array(nonEmptyLines.dropFirst(nameIndex + 1))
        guard !remainingLines.isEmpty else {
            print("Error: No address or additional information found after pharmacy name")
            return nil
        }
        
        // First line after name is the address
        let address = remainingLines[0]
        if address.isEmpty {
            print("Warning: Empty address for pharmacy: \(name)")
        } else {
            print("Found address: \(address)")
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
            print("Extracted phone number: \(phone)")
        } else {
            print("No phone number found in additional info")
        }
        
        // Only keep additional info if it's not empty
        let finalAdditionalInfo = additionalInfo.isEmpty ? nil : additionalInfo
        if let info = finalAdditionalInfo {
            print("Additional info found: \(info)")
        }
        
        print("Successfully parsed pharmacy: \(name)")
        
        return Pharmacy(
            name: name,
            address: address,
            phone: phone,
            additionalInfo: finalAdditionalInfo
        )
    }
    
    public static func parseBatch(from lines: [String]) -> [Pharmacy] {
        print("\nParsing batch of \(lines.count) lines")
        
        // Clean up and filter lines first
        let cleanLines = lines.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        
        guard !cleanLines.isEmpty else {
            print("Warning: No valid lines to parse")
            return []
        }
        
        if cleanLines.count % 3 != 0 {
            print("Warning: Number of lines (\(cleanLines.count)) is not divisible by 3. Some entries may be incomplete.")
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
                
                print("\nProcessing pharmacy group at index \(index - 2):")
                print("Name: \(name)")
                print("Address: \(address)")
                print("Info: \(additionalInfo)")
                
                // Validate pharmacy name format
                if !name.contains("FARMACIA") {
                    print("Warning: Skipping entry - Invalid pharmacy name format: \(name)")
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
                    print("Extracted phone: \(phone)")
                } else {
                    print("No phone number found in additional info")
                }
                
                // Validate address
                if address.isEmpty {
                    print("Warning: Empty address for pharmacy: \(name)")
                }
                
                // Create pharmacy
                pharmacies.append(Pharmacy(
                    name: name,
                    address: address,
                    phone: phone,
                    additionalInfo: finalAdditionalInfo.isEmpty ? nil : finalAdditionalInfo
                ))
                print("Successfully parsed pharmacy: \(name)")
                
                // Start new group
                currentGroup = []
            }
        }
        
        return pharmacies
    }
}
