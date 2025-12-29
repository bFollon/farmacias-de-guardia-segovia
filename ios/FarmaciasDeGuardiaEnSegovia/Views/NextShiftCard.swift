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

/// Card displaying information about the next pharmacy shift
struct NextShiftCard: View {
    let schedule: PharmacySchedule
    let timeSpan: DutyTimeSpan
    let region: Region
    @State private var showingDetails = false

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
        case .ruralDaytime, .ruralExtendedDaytime:
            return "Diurno"
        default:
            return "Turno"
        }
    }

    private var sheetHeight: CGFloat {
        let baseHeight: CGFloat = 150  // Nav bar + inline shift header + padding
        let pharmacyHeight: CGFloat = 120  // Height per pharmacy including spacing
        let pharmacyCount = CGFloat(pharmacies.count)
        return baseHeight + (pharmacyCount * pharmacyHeight)
    }

    var body: some View {
        Button {
            showingDetails = true
        } label: {
            HStack(alignment: .center, spacing: 12) {
                // Left side - all content in VStack
                VStack(alignment: .leading, spacing: 12) {
                    // Header
                    HStack(spacing: 8) {
                        Image(systemName: "clock.fill")
                            .foregroundColor(.secondary)
                            .frame(width: 20)
                        Text("Siguiente turno")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }

                    // Shift info
                    HStack(spacing: 8) {
                        Image(systemName: timeSpan == .capitalNight ? "moon.stars.fill" : "sun.max.fill")
                            .foregroundColor(.secondary.opacity(0.7))
                            .frame(width: ViewConstants.iconColumnWidth)
                        Text("\(shiftLabel) • \(timeSpan.displayName)")
                            .font(.headline)
                            .foregroundColor(.primary)
                    }

                    // Compact pharmacy preview
                    if let firstPharmacy = pharmacies.first {
                        HStack(spacing: 8) {
                            Text(firstPharmacy.name)
                                .font(.body)
                                .foregroundColor(.primary)
                                .lineLimit(1)

                            if pharmacies.count > 1 {
                                Text("+\(pharmacies.count - 1) más")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }

                Spacer()

                // Right side - chevron
                Image(systemName: "chevron.right")
                    .foregroundColor(.secondary)
                    .font(.system(size: 14))
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.1))
            )
            .contentShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $showingDetails) {
            NextShiftDetailSheet(
                schedule: schedule,
                timeSpan: timeSpan,
                region: region
            )
            .presentationDetents([.height(sheetHeight)])
            .presentationDragIndicator(.visible)
        }
    }
}
