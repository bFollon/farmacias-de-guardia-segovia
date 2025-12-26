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

    @State private var isPresentingInfo: Bool = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Use unified ScheduleHeaderView
                    // Convert DutyDate to Date for info content
                    let date: Date = {
                        if let timestamp = schedule.date.toTimestamp() {
                            return Date(timeIntervalSince1970: timestamp)
                        }
                        return Date() // Fallback to current date
                    }()

                    ScheduleHeaderView(
                        timeSpan: timeSpan,
                        date: date,
                        isPresentingInfo: $isPresentingInfo
                    )

                    // Pharmacies
                    ForEach(pharmacies, id: \.name) { pharmacy in
                        PharmacyView(pharmacy: pharmacy, activeShift: timeSpan)
                    }
                }
                .padding()
            }
            .background(Color(.systemBackground))
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
