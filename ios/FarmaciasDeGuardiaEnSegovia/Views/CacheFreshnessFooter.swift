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

/**
 * Subtle footnote indicator showing when the app last successfully checked in with the
 * sync server - whether or not that check found new data. Distinct from "when the data
 * changed": a schedule can be legitimately unchanged for weeks, which would otherwise leave
 * this stuck on an old date even though the app confirms it's current every day.
 * Positioned at the very bottom of the schedule like a footnote
 * Matches Android LastUpdatedIndicator
 */
struct CacheFreshnessFooter: View {
    let cacheTimestamp: TimeInterval

    private var formattedDate: String {
        let now = Date().timeIntervalSince1970
        let diff = now - cacheTimestamp
        let daysDiff = Int(diff / (24 * 60 * 60))

        switch daysDiff {
        case 0:
            let hoursDiff = Int(diff / (60 * 60))
            if hoursDiff == 0 {
                return "Comprobado hace menos de una hora"
            } else if hoursDiff == 1 {
                return "Comprobado hace 1 hora"
            } else {
                return "Comprobado hace \(hoursDiff) horas"
            }
        case 1:
            return "Comprobado ayer"
        case 2..<7:
            return "Comprobado hace \(daysDiff) días"
        default:
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "es_ES")
            formatter.dateFormat = "d 'de' MMMM, yyyy"
            return "Comprobado el \(formatter.string(from: Date(timeIntervalSince1970: cacheTimestamp)))"
        }
    }

    var body: some View {
        HStack(spacing: 4) {
            Text("📥")
                .font(.caption2)
            Text(formattedDate)
                .font(.caption2)
                .foregroundColor(.secondary.opacity(0.6))
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.top, 16)
        .padding(.bottom, 8)
    }
}
