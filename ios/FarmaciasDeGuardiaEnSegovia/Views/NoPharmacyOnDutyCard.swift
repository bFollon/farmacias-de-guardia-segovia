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
 * Card component for displaying "No pharmacy on duty" message inline
 * Equivalent to Android NoPharmacyOnDutyCard - shows within schedule content
 * rather than replacing the entire screen
 */
struct NoPharmacyOnDutyCard: View {
    let message: String
    let additionalInfo: String?

    init(
        message: String = "No hay farmacia de guardia programada para esta fecha.",
        additionalInfo: String? = "Intente refrescar o seleccione una fecha diferente."
    ) {
        self.message = message
        self.additionalInfo = additionalInfo
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Warning icon and title row
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(Color(hex: "F2994A"))
                    .frame(width: 20, height: 20)

                Text("Sin farmacia de guardia")
                    .font(.body)
                    .fontWeight(.bold)
                    .foregroundColor(Color(hex: "F2994A"))
            }

            // Main message
            Text(message)
                .font(.body)
                .foregroundColor(.primary)

            // Additional info if provided
            if let info = additionalInfo {
                Text(info)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.orange.opacity(0.15))
        )
    }
}

// Helper extension for hex colors
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

struct NoPharmacyOnDutyCard_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 16) {
            NoPharmacyOnDutyCard()

            NoPharmacyOnDutyCard(
                message: "No hay farmacia de guardia asignada para Segovia Capital en esta fecha.",
                additionalInfo: "Por favor, consulte las farmacias de guardia de otras zonas cercanas o el calendario oficial."
            )
        }
        .padding()
    }
}
