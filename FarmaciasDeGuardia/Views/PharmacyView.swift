import SwiftUI

struct PharmacyView: View {
    let pharmacy: Pharmacy
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Name with pharmacy cross
            HStack(spacing: ViewConstants.iconSpacing) {
                Image(systemName: "cross.case.fill")
                    .foregroundColor(.secondary.opacity(0.7))
                    .frame(width: ViewConstants.iconColumnWidth)
                Text(pharmacy.name)
            }
            .font(.system(.body, design: .rounded))
            
            // Address with location icon
            HStack(spacing: ViewConstants.iconSpacing) {
                Image(systemName: "location.fill")
                    .foregroundColor(.secondary.opacity(0.7))
                    .frame(width: ViewConstants.iconColumnWidth)
                Text(pharmacy.address)
                    .font(.subheadline)
            }
            
            // Phone with phone icon
            if !pharmacy.phone.isEmpty {
                HStack(spacing: ViewConstants.iconSpacing) {
                    Image(systemName: "phone.fill")
                        .foregroundColor(.secondary.opacity(0.7))
                        .frame(width: ViewConstants.iconColumnWidth)
                    Text(pharmacy.phone)
                        .font(.footnote)
                }
            }
            
            // Additional info with info icon
            if let info = pharmacy.additionalInfo {
                HStack(spacing: ViewConstants.iconSpacing) {
                    Image(systemName: "info.circle.fill")
                        .foregroundColor(.secondary.opacity(0.7))
                        .frame(width: ViewConstants.iconColumnWidth)
                    Text(info)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
    }
}
