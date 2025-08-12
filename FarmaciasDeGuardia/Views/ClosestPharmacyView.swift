import SwiftUI
import CoreLocation
import Foundation

struct ClosestPharmacyView: View {
    @StateObject private var locationManager = LocationManager()
    @State private var closestPharmacy: ClosestPharmacyResult?
    @State private var isSearching = false
    @State private var errorMessage: String?
    @State private var showingResult = false
    
    var body: some View {
        VStack(spacing: 16) {
            Button(action: {
                findClosestPharmacy()
            }) {
                HStack {
                    Image(systemName: "location.circle.fill")
                        .font(.title2)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Encuentra la más cercana")
                            .font(.headline)
                            .multilineTextAlignment(.leading)
                        Text("Buscar farmacia de guardia abierta")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.leading)
                    }
                    
                    Spacer()
                    
                    if isSearching {
                        ProgressView()
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding()
                .background(Color.green.opacity(0.1))
                .foregroundColor(.primary)
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.green.opacity(0.3), lineWidth: 1)
                )
            }
            .disabled(isSearching)
            .buttonStyle(PlainButtonStyle())
            
            // Error message
            if let errorMessage = errorMessage {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }
        }
        .sheet(isPresented: $showingResult) {
            if let result = closestPharmacy {
                ClosestPharmacyResultView(result: result)
            }
        }
    }
    
    private func findClosestPharmacy() {
        errorMessage = nil
        isSearching = true
        
        // Request location first
        locationManager.requestLocationOnce()
        
        // Listen for location updates
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            checkLocationAndSearch()
        }
    }
    
    private func checkLocationAndSearch() {
        // Check if we have location permission and location
        if let error = locationManager.error {
            isSearching = false
            errorMessage = error.localizedDescription
            return
        }
        
        guard let userLocation = locationManager.userLocation else {
            // If still loading, wait a bit more
            if locationManager.isLoading {
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    checkLocationAndSearch()
                }
            } else {
                isSearching = false
                errorMessage = "No se pudo obtener tu ubicación"
            }
            return
        }
        
        // We have location, now search for closest pharmacy
        Task {
            do {
                let result = try await ClosestPharmacyService.findClosestOnDutyPharmacy(
                    userLocation: userLocation
                )
                
                await MainActor.run {
                    self.closestPharmacy = result
                    self.isSearching = false
                    self.showingResult = true
                    
                    // Provide haptic feedback for success
                    let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
                    impactFeedback.impactOccurred()
                }
            } catch {
                await MainActor.run {
                    self.isSearching = false
                    self.errorMessage = error.localizedDescription
                    
                    // Provide haptic feedback for error
                    let notificationFeedback = UINotificationFeedbackGenerator()
                    notificationFeedback.notificationOccurred(.error)
                }
            }
        }
    }
}

struct ClosestPharmacyResultView: View {
    let result: ClosestPharmacyResult
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @State private var showingMapOptions = false
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // Header
                    VStack(spacing: 12) {
                        Image(systemName: "cross.circle.fill")
                            .font(.system(size: 60))
                            .foregroundColor(.green)
                        
                        Text("Farmacia más cercana")
                            .font(.title2)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top)
                    
                    // Pharmacy info
                    VStack(alignment: .leading, spacing: 16) {
                        // Name and distance
                        VStack(alignment: .leading, spacing: 8) {
                            Text(result.pharmacy.name)
                                .font(.title3)
                                .fontWeight(.semibold)
                            
                            HStack {
                                Image(systemName: "location.fill")
                                    .foregroundColor(.blue)
                                Text(result.formattedDistance)
                                    .font(.headline)
                                    .foregroundColor(.blue)
                            }
                        }
                        
                        Divider()
                        
                        // Address
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Dirección")
                                .font(.headline)
                                .foregroundColor(.primary)
                            
                            Button(action: {
                                let availableApps = MapApp.availableApps()
                                if availableApps.count == 1 {
                                    openInMaps(using: availableApps[0])
                                } else {
                                    showingMapOptions = true
                                }
                            }) {
                                Text(result.pharmacy.address)
                                    .font(.body)
                                    .foregroundColor(.blue)
                                    .underline()
                                    .multilineTextAlignment(.leading)
                            }
                        }
                        
                        // Phone (if available)
                        if !result.pharmacy.phone.isEmpty {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Teléfono")
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                
                                if result.pharmacy.phone != "No disponible" {
                                    Button(action: {
                                        callPharmacy()
                                    }) {
                                        Text(result.pharmacy.formattedPhone)
                                            .font(.body)
                                            .foregroundColor(.blue)
                                            .underline()
                                    }
                                } else {
                                    Text("No disponible")
                                        .font(.body)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                        
                        Divider()
                        
                        // Region and schedule info
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Información del servicio")
                                .font(.headline)
                                .foregroundColor(.primary)
                            
                            HStack {
                                Text("Región:")
                                    .foregroundColor(.secondary)
                                Spacer()
                                Text(result.regionDisplayName)
                                    .fontWeight(.medium)
                            }
                            
                            HStack {
                                Text("Horario:")
                                    .foregroundColor(.secondary)
                                Spacer()
                                Text(result.timeSpan.displayName)
                                    .fontWeight(.medium)
                            }
                        }
                        
                        // Additional info (if available)
                        if let additionalInfo = result.pharmacy.additionalInfo,
                           !additionalInfo.isEmpty {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Información adicional")
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                
                                Text(additionalInfo)
                                    .font(.body)
                                    .foregroundColor(.secondary)
                            }
                        }
                        
                        // Action buttons
                        VStack(spacing: 12) {
                            Button(action: {
                                let availableApps = MapApp.availableApps()
                                if availableApps.count == 1 {
                                    openInMaps(using: availableApps[0])
                                } else {
                                    showingMapOptions = true
                                }
                            }) {
                                HStack {
                                    Image(systemName: "map.fill")
                                    Text("Abrir en Mapas")
                                }
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.blue)
                                .foregroundColor(.white)
                                .cornerRadius(10)
                            }
                            
                            if !result.pharmacy.phone.isEmpty && result.pharmacy.phone != "No disponible" {
                                Button(action: {
                                    callPharmacy()
                                }) {
                                    HStack {
                                        Image(systemName: "phone.fill")
                                        Text("Llamar")
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(Color.green)
                                    .foregroundColor(.white)
                                    .cornerRadius(10)
                                }
                            }
                        }
                        .padding(.top)
                    }
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                    
                    Spacer()
                }
                .padding()
            }
            .navigationTitle("Resultado")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") {
                        dismiss()
                    }
                }
            }
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
    
    private func openInMaps(using app: MapApp) {
        // Use enhanced query with pharmacy name (same as PharmacyView.swift)
        let query = "\(result.pharmacy.name), \(result.pharmacy.address), Segovia, España"
        let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        
        var urlString: String
        switch app {
        case .apple:
            urlString = "http://maps.apple.com/?q=\(encodedQuery)"
        case .google:
            urlString = "comgooglemaps://?q=\(encodedQuery)"
        case .waze:
            urlString = "waze://?q=\(encodedQuery)"
        }
        
        if let url = URL(string: urlString) {
            openURL(url)
        }
    }
    
    private func callPharmacy() {
        let phoneNumber = result.pharmacy.phone.replacingOccurrences(of: " ", with: "")
        if let url = URL(string: "tel:\(phoneNumber)") {
            openURL(url)
        }
    }
}

#Preview {
    ClosestPharmacyView()
}
