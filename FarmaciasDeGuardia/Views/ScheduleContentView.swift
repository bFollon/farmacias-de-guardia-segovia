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
                
                Divider()
                    .padding(.vertical)
                
                VStack(alignment: .leading, spacing: 8) {
                    Text("Aviso")
                        .font(.footnote)
                        .fontWeight(.semibold)
                    
                    Text("La información mostrada puede no ser exacta. Por favor, consulte siempre la fuente oficial:")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                    
                    Link("Calendario de Guardias - COF Segovia",
                         destination: URL(string: "https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf")!)
                        .font(.footnote)
                    
                    let emailBody = """
                        Hola,
                        
                        He encontrado un error en la farmacia de guardia mostrada para:
                        
                        Fecha y hora: \(formattedDateTime)
                        Turno: \(shiftType == .day ? "Diurno" : "Nocturno")
                        Farmacia mostrada: \((shiftType == .day ? schedule.dayShiftPharmacies.first : schedule.nightShiftPharmacies.first)?.name ?? "")
                        Dirección: \((shiftType == .day ? schedule.dayShiftPharmacies.first : schedule.nightShiftPharmacies.first)?.address ?? "")
                        
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
