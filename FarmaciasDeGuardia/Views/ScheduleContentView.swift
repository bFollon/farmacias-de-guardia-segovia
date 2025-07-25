import SwiftUI

struct ScheduleContentView: View {
    let schedule: PharmacySchedule
    let shiftType: DutyDate.ShiftType
    @Binding var isPresentingInfo: Bool
    let formattedDateTime: String
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                // Date and time with calendar icon
                HStack(spacing: ViewConstants.iconSpacing) {
                    Image(systemName: "calendar")
                        .foregroundColor(.secondary.opacity(0.7))
                        .frame(width: ViewConstants.iconColumnWidth)
                    Text(formattedDateTime)
                }
                .font(.title2)
                .padding(.bottom, 5)
                
                // Show the active shift with context help
                ShiftHeaderView(shiftType: shiftType, isPresentingInfo: $isPresentingInfo)
                
                // Show active pharmacy
                if let pharmacy = (shiftType == .day ? schedule.dayShiftPharmacies.first : schedule.nightShiftPharmacies.first) {
                    PharmacyView(pharmacy: pharmacy)
                }
            }
            .padding()
        }
    }
}
