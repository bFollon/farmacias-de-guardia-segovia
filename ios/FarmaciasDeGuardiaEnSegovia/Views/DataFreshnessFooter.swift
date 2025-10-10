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

/// Subtle footnote indicator showing when cached data was last updated
/// Shows relative time for recent updates, absolute date for older ones
/// Positioned at the bottom of content like a footnote
struct DataFreshnessFooter: View {
    let downloadDate: Date?
    
    var body: some View {
        if let downloadDate = downloadDate {
            VStack(spacing: 0) {
                Divider()
                
                Text(formattedDate(downloadDate))
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .padding(.vertical, 8)
                    .padding(.horizontal)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
            .background(Color(UIColor.systemBackground))
        }
    }
    
    /// Format the date to match Android's LastUpdatedIndicator
    private func formattedDate(_ date: Date) -> String {
        let now = Date()
        let diff = now.timeIntervalSince(date)
        let daysDiff = Int(diff / (24 * 60 * 60))
        
        switch daysDiff {
        case 0:
            // Same day - show hours
            let hoursDiff = Int(diff / (60 * 60))
            if hoursDiff == 0 {
                return "Actualizado hace menos de una hora"
            } else if hoursDiff == 1 {
                return "Actualizado hace 1 hora"
            } else {
                return "Actualizado hace \(hoursDiff) horas"
            }
            
        case 1:
            return "Actualizado ayer"
            
        case 2..<7:
            return "Actualizado hace \(daysDiff) días"
            
        default:
            // For older updates, show absolute date
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "es_ES")
            formatter.dateFormat = "d 'de' MMMM, yyyy"
            return "Actualizado el \(formatter.string(from: date))"
        }
    }
}

#Preview {
    VStack {
        Spacer()
        
        // Test various time differences
        DataFreshnessFooter(downloadDate: Date().addingTimeInterval(-30 * 60)) // 30 min ago
        DataFreshnessFooter(downloadDate: Date().addingTimeInterval(-2 * 60 * 60)) // 2 hours ago
        DataFreshnessFooter(downloadDate: Date().addingTimeInterval(-24 * 60 * 60)) // Yesterday
        DataFreshnessFooter(downloadDate: Date().addingTimeInterval(-3 * 24 * 60 * 60)) // 3 days ago
        DataFreshnessFooter(downloadDate: Date().addingTimeInterval(-30 * 24 * 60 * 60)) // 30 days ago
    }
}

