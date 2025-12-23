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
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Header
                    VStack(spacing: 12) {
                        Image(systemName: "chart.bar.fill")
                            .font(.system(size: 50))
                            .foregroundColor(.blue)

                        Text("Ayúdenos a Mejorar la App")
                            .font(.title2)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top)

                    // Main Description
                    VStack(alignment: .leading, spacing: 12) {
                        Text("¿Puede la aplicación recopilar datos técnicos anónimos para ayudarnos a detectar errores y mejorar el rendimiento de la app?")
                            .font(.body)
                            .foregroundColor(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    Divider()

                    // What IS collected
                    VStack(alignment: .leading, spacing: 16) {
                        Text("¿Qué se recopila?")
                            .font(.headline)
                            .foregroundColor(.primary)

                        VStack(alignment: .leading, spacing: 8) {
                            InfoRow(icon: "iphone", text: "Información del dispositivo (modelo, versión de iOS)")
                            InfoRow(icon: "exclamationmark.triangle", text: "Errores y fallos de la aplicación")
                            InfoRow(icon: "speedometer", text: "Métricas de rendimiento y tiempos de carga")
                        }
                    }

                    Divider()

                    // What is NOT collected
                    VStack(alignment: .leading, spacing: 16) {
                        Text("¿Qué NO se recopila?")
                            .font(.headline)
                            .foregroundColor(.primary)

                        VStack(alignment: .leading, spacing: 8) {
                            InfoRow(icon: "person.fill.xmark", text: "Información personal identificable", color: .red)
                            InfoRow(icon: "location.slash", text: "Ubicación geográfica precisa", color: .red)
                            InfoRow(icon: "cross.case.slash", text: "Datos de farmacias consultadas", color: .red)
                        }
                    }

                    Divider()

                    // Buttons
                    VStack(spacing: 12) {
                        // Enable button (green)
                        Button(action: {
                            MonitoringPreferencesService.shared.setMonitoringEnabled(true)
                            dismiss()
                        }) {
                            HStack {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.white)
                                Text("Habilitar Monitoreo")
                                    .fontWeight(.medium)
                                    .foregroundColor(.white)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 14)
                            .background(Color.green)
                            .cornerRadius(12)
                        }
                        .buttonStyle(PlainButtonStyle())

                        // Not now button (gray)
                        Button(action: {
                            MonitoringPreferencesService.shared.setMonitoringEnabled(false)
                            dismiss()
                        }) {
                            HStack {
                                Image(systemName: "xmark.circle")
                                    .foregroundColor(.white)
                                Text("Ahora No")
                                    .fontWeight(.medium)
                                    .foregroundColor(.white)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 14)
                            .background(Color.gray)
                            .cornerRadius(12)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }

                    // Footer note
                    Text("Esta función es completamente opcional. Puede cambiar su preferencia en cualquier momento desde la sección de Ajustes.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 8)
                }
                .padding()
            }
            .navigationTitle("Privacidad")
            .navigationBarTitleDisplayMode(.inline)
        }
        .interactiveDismissDisabled() // Prevent dismissal by swiping down
    }
}

/// Helper view for displaying an info row with icon and text
private struct InfoRow: View {
    let icon: String
    let text: String
    var color: Color = .blue

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(color)
                .frame(width: 20)
            Text(text)
                .font(.body)
                .foregroundColor(.primary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

#Preview {
    MonitoringConsentView()
}
