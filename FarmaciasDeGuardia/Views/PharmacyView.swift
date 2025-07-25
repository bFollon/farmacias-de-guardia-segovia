import SwiftUI
import UIKit

struct PharmacyView: View {
    let pharmacy: Pharmacy
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Name with pharmacy cross
            HStack(spacing: ViewConstants.iconSpacing) {
                Image(systemName: "cross.case.fill")
                    .foregroundColor(.secondary.opacity(0.7))
                    .frame(width: ViewConstants.iconColumnWidth)
                Text(pharmacy.name)
            }
            .font(.system(.body, design: .rounded))
            
            // Address with location icon
            Button {
                if let query = "\(pharmacy.name), \(pharmacy.address), Segovia"
                    .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                   let url = URL(string: "maps://?q=\(query)") {
                    UIApplication.shared.open(url)
                }
            } label: {
                HStack(spacing: ViewConstants.iconSpacing) {
                    Image(systemName: "location.fill")
                        .foregroundColor(.secondary.opacity(0.7))
                        .frame(width: ViewConstants.iconColumnWidth)
                    Text(pharmacy.address)
                        .font(.subheadline)
                        .foregroundColor(.primary)
                    Image(systemName: "arrow.up.forward.app.fill")
                        .font(.caption)
                        .foregroundColor(.blue.opacity(0.7))
                }
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
        .padding(.vertical, 16)
    }
}
