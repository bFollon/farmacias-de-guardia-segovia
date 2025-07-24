import SwiftUI

struct PharmacyView: View {
    let pharmacy: Pharmacy
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(pharmacy.name)
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
