import SwiftUI

struct DayScheduleView: View {
    let schedule: PharmacySchedule
    let location: DutyLocation
    @Binding var isPresentingInfo: Bool // Keep for backward compatibility
    @State private var isPresentingDayInfo: Bool = false
    @State private var isPresentingNightInfo: Bool = false
    let date: Date

    // Observe network status
    @ObservedObject private var networkMonitor = NetworkMonitor.shared

    // PDF link validation
    @State private var isValidatingPDFLink = false
    @State private var showPDFLinkError = false
    @State private var pdfLinkErrorMessage = ""
    @Environment(\.openURL) private var openURL

    // Detail view sheet
    @State private var showingDetailView = false

    private var formattedDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .full
        formatter.locale = Locale(identifier: "es_ES")
        return formatter.string(from: date)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                // Location name at the top (matching ZBS style)
                HStack {
                    Text(location.icon)
                        .font(.title)
                    Text(location.name)
                        .font(.title)
                        .fontWeight(.medium)
                }
                .padding(.bottom, 5)

                // Date with calendar icon
                HStack(spacing: 8) {
                    Image(systemName: "calendar.circle.fill")
                        .foregroundColor(.blue)
                        .frame(width: 20)
                    Text(formattedDate)
                        .font(.title2)
                        .fontWeight(.medium)
                }
                .padding(.bottom, 20)

                // Offline warning (if not connected)
                if !networkMonitor.isOnline {
                    OfflineWarningCard()
                        .padding(.bottom, 12)
                }
                
                // Pharmacy section with header
                VStack(alignment: .leading, spacing: 12) {
                    Text("Farmacias de Guardia")
                        .font(.headline)

                    // Show notes if present (e.g., Cantalejo special instructions)
                    if let notes = location.notes {
                        Button(action: {
                            if location.detailViewId != nil {
                                showingDetailView = true
                            }
                        }) {
                            HStack(spacing: 8) {
                                Image(systemName: "info.circle.fill")
                                    .foregroundColor(.blue)
                                Text(notes)
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                                if location.detailViewId != nil {
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .foregroundColor(.blue)
                                        .font(.caption)
                                }
                            }
                            .padding()
                            .background(Color.blue.opacity(0.1))
                            .cornerRadius(8)
                        }
                        .buttonStyle(PlainButtonStyle())
                        .disabled(location.detailViewId == nil)
                    }

                    if location.id == "segovia-capital" {
                        // Show day/night shifts for Segovia Capital
                        VStack(alignment: .leading, spacing: 12) {
                            ScheduleHeaderView(timeSpan: .capitalDay, date: date, isPresentingInfo: $isPresentingDayInfo)
                            if let pharmacy = schedule.dayShiftPharmacies.first {
                                PharmacyView(pharmacy: pharmacy, activeShift: .capitalDay)
                            }
                        }
                        .padding(.vertical)

                        Divider()

                        VStack(alignment: .leading, spacing: 12) {
                            ScheduleHeaderView(timeSpan: .capitalNight, date: date, isPresentingInfo: $isPresentingNightInfo)
                            if let pharmacy = schedule.nightShiftPharmacies.first {
                                PharmacyView(pharmacy: pharmacy, activeShift: .capitalNight)
                            }
                        }
                        .padding(.vertical)
                    } else {
                        // Show schedule header and pharmacies for other regions
                        // Determine shift type from schedule.shifts (first available shift)
                        if let firstShift = schedule.shifts.keys.first {
                            VStack(alignment: .leading, spacing: 12) {
                                ScheduleHeaderView(timeSpan: firstShift, date: date, isPresentingInfo: $isPresentingInfo)

                                if let pharmacies = schedule.shifts[firstShift], !pharmacies.isEmpty {
                                    ForEach(pharmacies, id: \.name) { pharmacy in
                                        PharmacyView(pharmacy: pharmacy, activeShift: firstShift)
                                    }
                                }
                            }
                        } else {
                            // Fallback to legacy properties if shift data unavailable
                            let pharmacies = schedule.dayShiftPharmacies
                            if !pharmacies.isEmpty {
                                // Use fullDay as default for non-capital regions
                                ScheduleHeaderView(timeSpan: .fullDay, date: date, isPresentingInfo: $isPresentingInfo)

                                ForEach(pharmacies, id: \.name) { pharmacy in
                                    PharmacyView(pharmacy: pharmacy, activeShift: .fullDay)
                                }
                            }
                        }
                    }
                }
                
                Divider()
                    .padding(.vertical)
                
                // Disclaimer and support section
                VStack(alignment: .leading, spacing: 8) {
                    Text("Aviso")
                        .font(.footnote)
                        .fontWeight(.semibold)
                    
                    Text("La información mostrada puede no ser exacta. Por favor, consulte siempre la fuente oficial:")
                        .font(.footnote)
                        .foregroundColor(.secondary)

                    Button(action: {
                        openPDFLink()
                    }) {
                        Text("Calendario de Guardias - \(location.name)")
                    }
                    .font(.footnote)
                    .disabled(isValidatingPDFLink)
                    
                    let emailBody = AppConfig.EmailLinks.dayScheduleErrorBody(
                        date: formattedDate,
                        dayPharmacyName: schedule.dayShiftPharmacies.first?.name ?? "",
                        dayPharmacyAddress: schedule.dayShiftPharmacies.first?.address ?? "",
                        nightPharmacyName: schedule.nightShiftPharmacies.first?.name ?? "",
                        nightPharmacyAddress: schedule.nightShiftPharmacies.first?.address ?? ""
                    )
                    
                    if let errorURL = AppConfig.EmailLinks.errorReport(body: emailBody) {
                        Link("¿Ha encontrado algún error? Repórtelo aquí",
                             destination: errorURL)
                            .font(.footnote)
                            .padding(.top, 8)
                    }
                }
            }
            .padding()
        }
        .overlay {
            if isValidatingPDFLink {
                ZStack {
                    Color.black.opacity(0.4)
                        .ignoresSafeArea()

                    VStack(spacing: 16) {
                        ProgressView()
                            .scaleEffect(1.5)
                            .tint(.white)

                        Text("Comprobando URL...")
                            .font(.headline)
                            .foregroundColor(.white)
                    }
                    .padding(32)
                    .background(Color(.systemGray6))
                    .cornerRadius(16)
                }
            }
        }
        .alert("Error al abrir el enlace", isPresented: $showPDFLinkError) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(pdfLinkErrorMessage)
        }
        .sheet(isPresented: $showingDetailView) {
            if let detailViewId = location.detailViewId {
                DetailViewFactory.makeDetailView(for: detailViewId)
            }
        }
    }

    private func openPDFLink() {
        Task {
            let pdfURL = location.associatedRegion.pdfURL

            // Check if we need to show loading (cache miss)
            let needsValidation = PDFURLValidator.shared.needsValidation(for: pdfURL)

            await MainActor.run {
                if needsValidation {
                    isValidatingPDFLink = true
                }
            }

            let validationResult = await PDFURLValidator.shared.validateURL(pdfURL)

            await MainActor.run {
                isValidatingPDFLink = false

                if validationResult.isValid {
                    openURL(pdfURL)
                } else {
                    // URL validation failed - trigger scraping to get fresh URLs
                    DebugConfig.debugPrint("📄 PDF URL validation failed, triggering URL scraping")
                    Task {
                        _ = await PDFURLScrapingService.shared.scrapePDFURLs()
                        // After scraping, show error to user
                        await MainActor.run {
                            if let errorMsg = validationResult.errorMessage {
                                pdfLinkErrorMessage = errorMsg + "\n\nSe ha intentado actualizar los enlaces automáticamente."
                                showPDFLinkError = true
                            }
                        }
                    }
                }
            }
        }
    }
}
