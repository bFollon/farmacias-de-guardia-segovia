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
        VStack(alignment: .leading, spacing: 8) {
            // Warning banner for closed pharmacies
            if shouldShowClosedWarning {
                HStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                    Text("Fuera del horario de guardia")
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
            
            // Pharmacy name with red cross icon
            HStack {
                Image(systemName: "cross.circle.fill")
                    .foregroundColor(.red)
                Text(pharmacy.name)
                    .font(.headline)
            }
            
            // Address with blue location icon (clickable)
            Button {
                let availableApps = MapApp.availableApps()
                if availableApps.count == 1 {
                    openInMaps(using: availableApps[0])
                } else {
                    showingMapOptions = true
                }
            } label: {
                HStack {
                    Image(systemName: "location")
                        .foregroundColor(.blue)
                        .frame(width: 20)
                    Text(pharmacy.address)
                        .font(.body)
                        .foregroundColor(.primary)
                        .underline()
                        .multilineTextAlignment(.leading)
                }
            }
            
            // Phone with green phone icon (clickable)
            if !pharmacy.phone.isEmpty {
                let isPhoneAvailable = pharmacy.phone != "No disponible"
                
                if isPhoneAvailable {
                    Button {
                        if let phoneURL = URL(string: "tel://\(pharmacy.phone.replacingOccurrences(of: " ", with: ""))") {
                            UIApplication.shared.open(phoneURL)
                        }
                    } label: {
                        HStack {
                            Image(systemName: "phone")
                                .foregroundColor(.green)
                                .frame(width: 20)
                            Text(pharmacy.formattedPhone)
                                .font(.body)
                                .foregroundColor(.primary)
                                .underline()
                        }
                    }
                } else {
                    // Non-clickable phone number (not available)
                    HStack {
                        Image(systemName: "phone")
                            .foregroundColor(.green)
                            .frame(width: 20)
                        Text("No disponible")
                            .font(.body)
                            .foregroundColor(.secondary)
                    }
                }
            }
            
            // Additional info with blue info icon
            if let info = pharmacy.additionalInfo {
                HStack {
                    Image(systemName: "info.circle")
                        .foregroundColor(.blue)
                        .frame(width: 20)
                    Text(info)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.gray.opacity(0.1))
        .cornerRadius(8)
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
