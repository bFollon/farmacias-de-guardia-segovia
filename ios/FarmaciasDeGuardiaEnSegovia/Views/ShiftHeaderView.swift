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

struct ShiftHeaderView: View {
    let shiftType: DutyDate.ShiftType
    let date: Date
    @Binding var isPresentingInfo: Bool
    
    var body: some View {
        HStack(alignment: .center, spacing: ViewConstants.iconSpacing) {
            Image(systemName: shiftType == .day ? "sun.max.fill" : "moon.stars.fill")
                .foregroundColor(.secondary.opacity(0.7))
                .frame(width: ViewConstants.iconColumnWidth)
            
            Text(shiftType == .day ? "Guardia diurna (10:15 - 22:00)" : "Guardia nocturna (22:00 - 10:15)")
                .font(.headline)
                .foregroundColor(.secondary)
            
            Button {
                isPresentingInfo = true
            } label: {
                Image(systemName: "info.circle")
                    .foregroundColor(.secondary)
            }
            .sheet(isPresented: $isPresentingInfo) {
                GuardiaInfoSheet(shiftType: shiftType, date: date)
            }
        }
    }
}
