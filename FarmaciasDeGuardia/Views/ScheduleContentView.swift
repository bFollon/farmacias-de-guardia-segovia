import SwiftUI

struct ScheduleContentView: View {
    let schedule: PharmacySchedule
    let activeShift: DutyTimeSpan
    let region: Region
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
                
                // Show shift info if applicable (for day/night regions)
                if activeShift == .capitalDay || activeShift == .capitalNight {
                    // Convert DutyTimeSpan back to ShiftType for ShiftHeaderView compatibility
                    let legacyShiftType: DutyDate.ShiftType = activeShift == .capitalDay ? .day : .night
                    ShiftHeaderView(shiftType: legacyShiftType, date: Date(), isPresentingInfo: $isPresentingInfo)
                }
                
                // Show active pharmacy for current shift
                if let pharmacies = schedule.shifts[activeShift], let pharmacy = pharmacies.first {
                    PharmacyView(pharmacy: pharmacy)
                } else {
                    // Fallback to legacy properties if shift-specific data isn't available
                    if let pharmacy = schedule.dayShiftPharmacies.first {
                        PharmacyView(pharmacy: pharmacy)
                    }
                }
                
                Divider()
                    .padding(.vertical)
                
                VStack(alignment: .leading, spacing: 8) {
                    Text("Aviso")
                        .font(.footnote)
                        .fontWeight(.semibold)
                    
                    Text("La información mostrada puede no ser exacta. Por favor, consulte siempre la fuente oficial:")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                    
                    Link("Calendario de Guardias - \(region.name)",
                         destination: region.pdfURL)
                        .font(.footnote)
                    
                    let currentPharmacy = schedule.shifts[activeShift]?.first ?? schedule.dayShiftPharmacies.first
                    let shiftName = activeShift == .fullDay ? "24 horas" : (activeShift == .capitalDay ? "Diurno" : "Nocturno")
                    
                    let emailBody = """
                        Hola,
                        
                        He encontrado un error en la farmacia de guardia mostrada para:
                        
                        Fecha y hora: \(formattedDateTime)
                        Turno: \(shiftName)
                        Farmacia mostrada: \(currentPharmacy?.name ?? "")
                        Dirección: \(currentPharmacy?.address ?? "")
                        
                        La farmacia correcta es:
                        
                        
                        Gracias.
                        """.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                    
                    Link("¿Has encontrado algún error? Repórtalo aquí",
                         destination: URL(string: "mailto:alive.intake_0b@icloud.com?subject=Error%20en%20Farmacias%20de%20Guardia&body=\(emailBody)")!)
                        .font(.footnote)
                        .padding(.top, 8)
                }
            }
            .padding()
        }
    }
}
