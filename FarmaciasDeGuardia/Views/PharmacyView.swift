import SwiftUI
import UIKit
import MapKit
import CoreLocation

struct PharmacyView: View {
    let pharmacy: Pharmacy
    @State private var showingMapOptions = false
    
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
                showingMapOptions = true
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
