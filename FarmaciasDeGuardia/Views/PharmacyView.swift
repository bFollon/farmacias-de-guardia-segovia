import SwiftUI
import UIKit
import MapKit
import CoreLocation

struct PharmacyView: View {
    let pharmacy: Pharmacy
    @State private var showingMapOptions = false
    
    /// Determine if pharmacy should show closed warning based on additionalInfo
    private var shouldShowClosedWarning: Bool {
        guard let info = pharmacy.additionalInfo else { return false }
        
        // Only apply to Segovia Rural pharmacies (those with "ZBS:" in additionalInfo)
        guard info.contains("ZBS:") else { return false }
        
        let now = Date()
        let calendar = Calendar.current
        let hour = calendar.component(.hour, from: now)
        
        if info.contains("24h") {
            return false // 24h pharmacies never show warning
        } else if info.contains("10h-22h") {
            return hour < 10 || hour >= 22 // Extended hours
        } else if info.contains("10h-20h") {
            return hour < 10 || hour >= 20 // Standard hours
        }
        
        return false // Default: no warning
    }
    
    private func openInMaps(using app: MapApp) {
        let query = "\(pharmacy.name), \(pharmacy.address), Segovia, Spain"
            .addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        
        var urlString: String
        switch app {
        case .apple:
            urlString = "maps://?q=\(query)"
        case .google:
            urlString = "comgooglemaps://?q=\(query)"
        case .waze:
            urlString = "waze://?q=\(query)"
        }
        
        if let url = URL(string: urlString) {
            UIApplication.shared.open(url)
        }
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Warning banner for closed pharmacies
            if shouldShowClosedWarning {
                HStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                    Text("Esta farmacia está cerrada ahora")
                        .font(.caption)
                        .fontWeight(.medium)
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.yellow.opacity(0.15))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.orange.opacity(0.3), lineWidth: 1)
                )
            }
            
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
                let availableApps = MapApp.availableApps()
                if availableApps.count == 1 {
                    openInMaps(using: availableApps[0])
                } else {
                    showingMapOptions = true
                }
            } label: {
                HStack(spacing: ViewConstants.iconSpacing) {
                    Image(systemName: "location.fill")
                        .foregroundColor(.secondary.opacity(0.7))
                        .frame(width: ViewConstants.iconColumnWidth)
                    Text(pharmacy.address)
                        .font(.subheadline)
                        .foregroundColor(.primary)
                        .underline()
                }
            }
            
            // Phone with phone icon
            if !pharmacy.phone.isEmpty {
                Button {
                    if let phoneURL = URL(string: "tel://\(pharmacy.phone.replacingOccurrences(of: " ", with: ""))") {
                        UIApplication.shared.open(phoneURL)
                    }
                } label: {
                    HStack(spacing: ViewConstants.iconSpacing) {
                        Image(systemName: "phone.fill")
                            .foregroundColor(.secondary.opacity(0.7))
                            .frame(width: ViewConstants.iconColumnWidth)
                        Text(pharmacy.formattedPhone)
                            .font(.footnote)
                            .foregroundColor(.primary)
                            .underline()
                    }
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
        .confirmationDialog("Abrir en Maps", isPresented: $showingMapOptions) {
            ForEach(MapApp.availableApps(), id: \.self) { app in
                Button(app.rawValue) {
                    openInMaps(using: app)
                }
            }
            Button("Cancelar", role: .cancel) { }
        } message: {
            Text("Elije una aplicación de mapas")
        }
    }
}
