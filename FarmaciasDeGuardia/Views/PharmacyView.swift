import SwiftUI

struct PharmacyView: View {
    let pharmacy: Pharmacy
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Image(systemName: "cross.case.fill")
                    .foregroundColor(.secondary.opacity(0.7))
                Text(pharmacy.name)
            }
            .font(.system(.body, design: .rounded))
            Text(pharmacy.address)
                .font(.subheadline)
            if !pharmacy.phone.isEmpty {
                Text("📞 \(pharmacy.phone)")
                    .font(.footnote)
            }
            if let info = pharmacy.additionalInfo {
                Text(info)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
