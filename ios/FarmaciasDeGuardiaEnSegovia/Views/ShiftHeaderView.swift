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

/// Generic schedule header view that works with any DutyTimeSpan
struct ScheduleHeaderView: View {
    let timeSpan: DutyTimeSpan
    let date: Date
    @Binding var isPresentingInfo: Bool

    var body: some View {
        HStack(alignment: .center, spacing: ViewConstants.iconSpacing) {
            // Shift icon (sun, moon, clock, etc.)
            Image(systemName: timeSpan.icon)
                .foregroundColor(.secondary.opacity(0.7))
                .frame(width: ViewConstants.iconColumnWidth)

            // Shift label with time range
            Text("\(timeSpan.shiftLabel) (\(timeSpan.displayName))")
                .font(.headline)
                .foregroundColor(.secondary)

            // Info button (only shown if shift has explanatory content)
            if let infoContent = timeSpan.infoContent(for: date) {
                Button {
                    isPresentingInfo = true
                } label: {
                    Image(systemName: "info.circle")
                        .foregroundColor(.secondary)
                }
                .sheet(isPresented: $isPresentingInfo) {
                    ShiftInfoSheet(content: infoContent, date: date)
                }
            }
        }
    }
}

/// Generic info sheet that displays shift explanatory content
struct ShiftInfoSheet: View {
    let content: ShiftInfoContent
    let date: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(content.title)
                .font(.headline)

            content.bodyContent(for: date)

            Spacer()
        }
        .padding()
        .presentationDetents([.medium])
    }
}

// MARK: - Legacy Compatibility

/// Legacy ShiftHeaderView for backward compatibility
/// Wraps the new ScheduleHeaderView with Segovia Capital-specific logic
@available(*, deprecated, message: "Use ScheduleHeaderView with DutyTimeSpan instead")
struct ShiftHeaderView: View {
    let shiftType: DutyDate.ShiftType
    let date: Date
    @Binding var isPresentingInfo: Bool

    var body: some View {
        let timeSpan: DutyTimeSpan = shiftType == .day ? .capitalDay : .capitalNight
        ScheduleHeaderView(timeSpan: timeSpan, date: date, isPresentingInfo: $isPresentingInfo)
    }
}
