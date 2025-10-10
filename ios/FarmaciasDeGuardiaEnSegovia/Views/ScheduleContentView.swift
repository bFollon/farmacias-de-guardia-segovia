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
    let region: Region
    @Binding var isPresentingInfo: Bool
    let formattedDateTime: String
    let downloadDate: Date?
    
    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                // Region name at the top (matching ZBS style)
                HStack {
                    Text(region.icon)
                        .font(.title)
                    Text(region.name)
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
                
                // Pharmacy section with header
                VStack(alignment: .leading, spacing: 12) {
                    Text("Farmacias de Guardia")
                        .font(.headline)
                    
                    // Show shift info if applicable (for day/night regions)
                    if activeShift == .capitalDay || activeShift == .capitalNight {
                        // Convert DutyTimeSpan back to ShiftType for ShiftHeaderView compatibility
                        let legacyShiftType: DutyDate.ShiftType = activeShift == .capitalDay ? .day : .night
                        ShiftHeaderView(shiftType: legacyShiftType, date: Date(), isPresentingInfo: $isPresentingInfo)
                    }
                    
                    // Show active pharmacy for current shift
                    if let pharmacies = schedule.shifts[activeShift], let pharmacy = pharmacies.first {
                        PharmacyView(pharmacy: pharmacy)
                    } else {
                        // Fallback to legacy properties if shift-specific data isn't available
                        if let pharmacy = schedule.dayShiftPharmacies.first {
                            PharmacyView(pharmacy: pharmacy)
                        }
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
                    
                    Link("Calendario de Guardias - \(region.name)",
                         destination: region.pdfURL)
                        .font(.footnote)
                    
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
        
        // Data freshness footer pinned to bottom
        DataFreshnessFooter(downloadDate: downloadDate)
        }
    }
}
