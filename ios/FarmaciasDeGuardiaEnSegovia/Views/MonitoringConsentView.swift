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

/// Modal view for obtaining user consent for monitoring and error tracking
struct MonitoringConsentView: View {
    @Binding var isPresented: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header
            VStack(spacing: 8) {
                Image(systemName: "chart.bar.fill")
                    .font(.system(size: 36))
                    .foregroundColor(.blue)

                Text("Ayúdenos a Mejorar la App")
                    .font(.title3)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)

            // Main Description
            Text("¿Permitir recopilación de datos técnicos anónimos para detectar errores?")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .fixedSize(horizontal: false, vertical: true)

            Divider()

            // What IS collected
            VStack(alignment: .leading, spacing: 8) {
                Text("Se recopila:")
                    .font(.subheadline)
                    .fontWeight(.semibold)

                VStack(alignment: .leading, spacing: 4) {
                    InfoRow(icon: "iphone", text: "Info del dispositivo", color: .blue)
                    InfoRow(icon: "exclamationmark.triangle", text: "Errores y fallos", color: .blue)
                    InfoRow(icon: "speedometer", text: "Métricas de rendimiento", color: .blue)
                }
            }

            // What is NOT collected
            VStack(alignment: .leading, spacing: 8) {
                Text("NO se recopila:")
                    .font(.subheadline)
                    .fontWeight(.semibold)

                VStack(alignment: .leading, spacing: 4) {
                    InfoRow(icon: "person.fill.xmark", text: "Información personal", color: .red)
                    InfoRow(icon: "location.slash", text: "Ubicación precisa", color: .red)
                    InfoRow(icon: "pill.fill", text: "Farmacias consultadas", color: .red)
                }
            }

            Divider()

            // Buttons
            VStack(spacing: 8) {
                // Enable button (green)
                Button(action: {
                    MonitoringPreferencesService.shared.setMonitoringEnabled(true)
                    isPresented = false
                }) {
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                        Text("Permitir")
                            .fontWeight(.medium)
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.green)
                    .cornerRadius(10)
                }
                .buttonStyle(PlainButtonStyle())

                // Not now button (gray)
                Button(action: {
                    MonitoringPreferencesService.shared.setMonitoringEnabled(false)
                    isPresented = false
                }) {
                    HStack {
                        Image(systemName: "xmark.circle")
                        Text("No, Gracias")
                            .fontWeight(.medium)
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.gray)
                    .cornerRadius(10)
                }
                .buttonStyle(PlainButtonStyle())
            }

            // Footer note
            Text("Cambiar en Ajustes cuando quiera")
                .font(.caption2)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
        .padding(20)
    }
}

/// Helper view for displaying an info row with icon and text
private struct InfoRow: View {
    let icon: String
    let text: String
    var color: Color = .blue

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Image(systemName: icon)
                .foregroundColor(color)
                .font(.caption)
                .frame(width: 16)
            Text(text)
                .font(.caption)
                .foregroundColor(.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

#Preview {
    MonitoringConsentView(isPresented: .constant(true))
}
