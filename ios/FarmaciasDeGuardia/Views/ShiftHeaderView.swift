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
