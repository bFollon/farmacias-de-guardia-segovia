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

private extension ConfidenceResult {
    var barColor: Color {
        if level >= ConfidenceConfig.greenThreshold  { return .green }
        if level >= ConfidenceConfig.yellowThreshold { return .yellow }
        return .red
    }
}

/// A compact progress-bar indicator that communicates how trustworthy the displayed data is.
/// Tap the info button to open a bottom sheet with a full explanation and factor breakdown.
struct ConfidenceIndicatorView: View {
    let result: ConfidenceResult

    @State private var showBreakdown = false

    var body: some View {
        HStack(spacing: 6) {
            Text("Confianza:")
                .font(.caption2)
                .foregroundColor(.secondary)

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color.secondary.opacity(0.20))
                        .frame(height: 6)
                    RoundedRectangle(cornerRadius: 3)
                        .fill(result.barColor)
                        .frame(width: geo.size.width * result.level, height: 6)
                }
            }
            .frame(height: 6)

            Text("\(result.percentage)%")
                .font(.caption2)
                .foregroundColor(.secondary)
                .frame(minWidth: 30, alignment: .trailing)

            Button {
                showBreakdown = true
            } label: {
                Image(systemName: "info.circle")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .frame(height: 24)
        .sheet(isPresented: $showBreakdown) {
            ConfidenceBreakdownSheet(result: result)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }
}

// MARK: - Breakdown Sheet

private struct ConfidenceBreakdownSheet: View {
    let result: ConfidenceResult

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {

                    // Score summary
                    VStack(spacing: 8) {
                        HStack {
                            Text("Nivel de confianza")
                                .font(.headline)
                            Spacer()
                            Text("\(result.percentage)%")
                                .font(.title2)
                                .fontWeight(.bold)
                                .foregroundColor(result.barColor)
                        }
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Color.secondary.opacity(0.20))
                                    .frame(height: 10)
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(result.barColor)
                                    .frame(width: geo.size.width * result.level, height: 10)
                            }
                        }
                        .frame(height: 10)
                    }
                    .padding()
                    .background(Color(.secondarySystemBackground))
                    .cornerRadius(12)

                    // What this means
                    VStack(alignment: .leading, spacing: 8) {
                        Text("¿Qué significa esto?")
                            .font(.headline)
                        Text(
                            "Este indicador refleja la confianza que tenemos en que los datos mostrados sean precisos y estén actualizados. " +
                            "Se calcula analizando distintos factores: si las URLs de los documentos se han verificado recientemente, " +
                            "si existe alguna actualización pendiente de los PDFs, y si la cantidad de horarios disponibles es la esperada para la fecha actual."
                        )
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    }

                    // Factor breakdown
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Factores")
                            .font(.headline)

                        ForEach(Array(result.factors.enumerated()), id: \.offset) { _, factor in
                            ConfidenceFactorRow(factor: factor)
                        }
                    }

                    // Legend
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Leyenda de colores")
                            .font(.headline)
                        LegendRow(color: .green,  label: "≥ \(Int(ConfidenceConfig.greenThreshold * 100))% — Alta confianza")
                        LegendRow(color: .yellow, label: "≥ \(Int(ConfidenceConfig.yellowThreshold * 100))% — Confianza moderada")
                        LegendRow(color: .red,    label: "< \(Int(ConfidenceConfig.yellowThreshold * 100))% — Confianza baja")
                    }
                }
                .padding()
            }
            .navigationTitle("Confianza en los datos")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

// MARK: - Supporting rows

private struct ConfidenceFactorRow: View {
    let factor: ConfidenceFactor

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: factor.isIssue ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                .foregroundColor(factor.isIssue ? .orange : .green)
                .frame(width: 20)

            Text(factor.localizedTitle)
                .font(.subheadline)
                .foregroundColor(factor.isIssue ? .primary : .secondary)

            Spacer()

            if factor.isIssue {
                Text("-\(Int(round(factor.deduction * 100)))%")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.red)
            }
        }
        .padding(.vertical, 4)
    }
}

private struct LegendRow: View {
    let color: Color
    let label: String

    var body: some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 3)
                .fill(color)
                .frame(width: 24, height: 8)
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}
