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
 * Card component for displaying disclaimer and links to official calendar
 * Provides proper styling for both light and dark modes
 */
struct DisclaimerCard: View {
    let location: DutyLocation
    let onOpenPDF: () -> Void
    let errorReportURL: URL?
    let isValidatingPDF: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Aviso")
                .font(.footnote)
                .fontWeight(.bold)
                .foregroundColor(.primary)

            Text("La información mostrada puede no ser exacta. Por favor, consulte siempre la fuente oficial:")
                .font(.footnote)
                .foregroundColor(.secondary)

            Button(action: onOpenPDF) {
                Text("Calendario de Guardias - \(location.name)")
                    .font(.footnote)
                    .foregroundColor(.accentColor)
            }
            .disabled(isValidatingPDF)
            .buttonStyle(PlainButtonStyle())

            if let errorURL = errorReportURL {
                Link(destination: errorURL) {
                    Text("¿Ha encontrado algún error? Repórtelo aquí")
                        .font(.footnote)
                        .foregroundColor(.accentColor)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.gray.opacity(0.15))
        )
    }
}

struct DisclaimerCard_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            VStack {
                DisclaimerCard(
                    location: DutyLocation.fromRegion(.segoviaCapital),
                    onOpenPDF: {},
                    errorReportURL: URL(string: "mailto:test@example.com"),
                    isValidatingPDF: false
                )
                .padding()
            }
            .preferredColorScheme(.light)

            VStack {
                DisclaimerCard(
                    location: DutyLocation.fromRegion(.cuellar),
                    onOpenPDF: {},
                    errorReportURL: URL(string: "mailto:test@example.com"),
                    isValidatingPDF: false
                )
                .padding()
            }
            .preferredColorScheme(.dark)
        }
    }
}
