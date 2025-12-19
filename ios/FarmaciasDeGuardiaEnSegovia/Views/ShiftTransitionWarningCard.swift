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

/// Warning card shown when within 30 minutes of shift change
struct ShiftTransitionWarningCard: View {
    let minutesUntilChange: Int
    let nextShiftName: String

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(Color(red: 1.0, green: 0.6, blue: 0.0)) // Orange
                .frame(width: 24, height: 24)

            VStack(alignment: .leading, spacing: 4) {
                Text("El turno cambia pronto")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Color(red: 0.36, green: 0.25, blue: 0.22)) // Dark brown

                Text("En \(minutesUntilChange) minuto\(minutesUntilChange == 1 ? "" : "s") comienza el \(nextShiftName.lowercased()). Considera la farmacia del siguiente turno.")
                    .font(.caption)
                    .foregroundColor(Color(red: 0.36, green: 0.25, blue: 0.22))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(Color(red: 1.0, green: 0.95, blue: 0.88)) // Light orange
        )
    }
}
