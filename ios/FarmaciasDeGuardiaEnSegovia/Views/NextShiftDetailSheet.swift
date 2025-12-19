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

/// Modal sheet showing full details of next shift
struct NextShiftDetailSheet: View {
    let schedule: PharmacySchedule
    let timeSpan: DutyTimeSpan
    let region: Region
    @Environment(\.dismiss) private var dismiss

    private var pharmacies: [Pharmacy] {
        schedule.shifts[timeSpan] ?? []
    }

    private var shiftLabel: String {
        switch timeSpan {
        case .capitalDay:
            return "Diurno"
        case .capitalNight:
            return "Nocturno"
        case .fullDay:
            return "24 horas"
        default:
            return timeSpan.displayName
        }
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Shift header
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            Image(systemName: timeSpan == .capitalNight ?
                                  "moon.stars.fill" : "sun.max.fill")
                                .foregroundColor(.secondary.opacity(0.7))
                            Text(shiftLabel)
                                .font(.title2)
                                .fontWeight(.semibold)
                        }

                        Text(timeSpan.displayName)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.systemGray6))
                    .cornerRadius(8)

                    // Pharmacies
                    ForEach(pharmacies, id: \.name) { pharmacy in
                        PharmacyView(pharmacy: pharmacy)
                    }
                }
                .padding()
            }
            .navigationTitle("Siguiente turno")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") {
                        dismiss()
                    }
                }
            }
        }
    }
}
