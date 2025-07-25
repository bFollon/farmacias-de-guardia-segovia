import SwiftUI

struct ShiftHeaderView: View {
    let shiftType: DutyDate.ShiftType
    @Binding var isPresentingInfo: Bool
    
    var body: some View {
        HStack(alignment: .center, spacing: 8) {
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
                GuardiaInfoSheet(shiftType: shiftType)
            }
        }
    }
}
