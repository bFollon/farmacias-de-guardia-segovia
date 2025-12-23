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

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var showingCacheStatus = false
    @State private var showingCacheRefresh = false
    @State private var showingAbout = false

    // Monitoring preferences
    @State private var monitoringEnabled = MonitoringPreferencesService.shared.hasUserOptedIn()
    @State private var originalMonitoringValue = MonitoringPreferencesService.shared.hasUserOptedIn()
    @State private var showRestartNotice = false
    
    var body: some View {
        NavigationView {
            List {
                Section("Caché de PDFs") {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("PDFs en caché")
                            .font(.headline)
                        
                        Text("Los horarios PDF se almacenan localmente para una carga más rápida y acceso sin conexión.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                    
                    Button(action: { showingCacheRefresh = true }) {
                        HStack {
                            Image(systemName: "arrow.clockwise")
                            Text("Buscar Actualizaciones")
                            
                            Spacer()
                        }
                    }
                    
                    Button(action: { showingCacheStatus = true }) {
                        HStack {
                            Image(systemName: "info.circle")
                            Text("Ver Estado de la caché")
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }

                Section("Privacidad") {
                    VStack(alignment: .leading, spacing: 8) {
                        Toggle(isOn: $monitoringEnabled) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Monitoreo de Errores")
                                    .font(.headline)
                                Text("Envía datos técnicos para ayudar a mejorar la app")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .onChange(of: monitoringEnabled) { newValue in
                            MonitoringPreferencesService.shared.setMonitoringEnabled(newValue)
                            showRestartNotice = newValue != originalMonitoringValue
                        }
                    }
                    .padding(.vertical, 4)

                    if showRestartNotice {
                        HStack {
                            Image(systemName: "info.circle")
                                .foregroundColor(.orange)
                            Text("Requiere reiniciar la app para aplicar cambios")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }

                Section("Información") {
                    Button(action: { showingAbout = true }) {
                        HStack {
                            Image(systemName: "info.circle")
                            Text("Acerca de")
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    .foregroundColor(.primary)
                }
            }
            .navigationTitle("Configuración")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Listo") {
                        dismiss()
                    }
                }
            }
        }
        .sheet(isPresented: $showingCacheStatus) {
            NavigationView {
                CacheStatusView()
            }
        }
        .sheet(isPresented: $showingCacheRefresh) {
            CacheRefreshView()
        }
        .sheet(isPresented: $showingAbout) {
            AboutView()
        }
    }
    
    struct SettingsView_Previews: PreviewProvider {
        static var previews: some View {
            SettingsView()
        }
    }
}
