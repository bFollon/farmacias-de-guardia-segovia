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

struct ScheduleContentView: View {
    let schedule: PharmacySchedule
    let activeShift: DutyTimeSpan
    let location: DutyLocation
    @Binding var isPresentingInfo: Bool
    let formattedDateTime: String
    let cacheTimestamp: TimeInterval?

    // Observe network status
    @ObservedObject private var networkMonitor = NetworkMonitor.shared

    // PDF link validation
    @State private var isValidatingPDFLink = false
    @State private var showPDFLinkError = false
    @State private var pdfLinkErrorMessage = ""
    @Environment(\.openURL) private var openURL

    // Detail view sheet
    @State private var showingDetailView = false

    // Next shift feature
    @State private var nextShiftInfo: NextShiftInfo?

    var body: some View {
        VStack(spacing: 0) {
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

                    // Date and time with calendar icon
                    HStack(spacing: 8) {
                        Image(systemName: "calendar.circle.fill")
                            .foregroundColor(.blue)
                            .frame(width: 20)
                        Text(formattedDateTime)
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

                    // Always show schedule header for all regions
                    ScheduleHeaderView(
                        timeSpan: activeShift,
                        date: Date(),
                        isPresentingInfo: $isPresentingInfo
                    )
                    
                    // Show active pharmacy/pharmacies for current shift
                    if let pharmacies = schedule.shifts[activeShift], !pharmacies.isEmpty {
                        ForEach(pharmacies, id: \.name) { pharmacy in
                            PharmacyView(pharmacy: pharmacy, activeShift: activeShift)
                        }
                    } else {
                        // Fallback to legacy properties if shift-specific data isn't available
                        let pharmacies = schedule.dayShiftPharmacies
                        if !pharmacies.isEmpty {
                            ForEach(pharmacies, id: \.name) { pharmacy in
                                PharmacyView(pharmacy: pharmacy, activeShift: activeShift)
                            }
                        }
                    }

                    // Shift transition warning (if within 30 minutes)
                    if let info = nextShiftInfo, info.shouldShowWarning,
                       let minutes = info.minutesUntilChange {
                        let nextShiftName = info.timeSpan == .capitalNight ? "turno nocturno" :
                                            info.timeSpan == .capitalDay ? "turno diurno" : "turno 24 horas"
                        ShiftTransitionWarningCard(
                            minutesUntilChange: minutes,
                            nextShiftName: nextShiftName
                        )
                        .padding(.top, 12)
                    }

                    // Next shift card
                    if let info = nextShiftInfo {
                        NextShiftCard(
                            schedule: info.schedule,
                            timeSpan: info.timeSpan,
                            region: location.associatedRegion
                        )
                        .padding(.top, 12)
                    }
                }

                Divider()
                    .padding(.vertical)
                
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
                    
                    let currentPharmacy = schedule.shifts[activeShift]?.first ?? schedule.dayShiftPharmacies.first
                    let shiftName = activeShift == .fullDay ? "24 horas" : (activeShift == .capitalDay ? "Diurno" : "Nocturno")
                    
                    let emailBody = AppConfig.EmailLinks.currentScheduleContentErrorBody(
                        shiftName: shiftName,
                        pharmacyName: currentPharmacy?.name ?? "",
                        pharmacyAddress: currentPharmacy?.address ?? ""
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

            // Cache freshness indicator at bottom (fixed footer outside ScrollView)
            if let cacheTimestamp = cacheTimestamp {
                Divider()
                CacheFreshnessFooter(cacheTimestamp: cacheTimestamp)
                    .background(Color(UIColor.systemBackground))
            }
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
        .onAppear {
            loadNextShiftInfo()
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

    private func loadNextShiftInfo() {
        // Find next schedule
        Task {
            let schedules = await ScheduleService.loadSchedules(for: location)

            if let nextInfo = ScheduleService.findNextSchedule(
                in: schedules,
                for: location.associatedRegion,
                currentSchedule: schedule,
                currentTimeSpan: activeShift
            ) {
                let minutes = ScheduleService.calculateMinutesUntilShiftEnd(for: activeShift)

                await MainActor.run {
                    nextShiftInfo = NextShiftInfo(
                        schedule: nextInfo.0,
                        timeSpan: nextInfo.1,
                        minutesUntilChange: minutes
                    )

                    DebugConfig.debugPrint("⏰ Next shift info loaded: \(nextInfo.1.displayName), minutes until change: \(minutes ?? -1)")
                }
            }
        }
    }
}
