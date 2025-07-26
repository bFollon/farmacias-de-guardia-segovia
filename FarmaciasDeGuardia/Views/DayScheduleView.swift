import SwiftUI

struct DayScheduleView: View {
    let schedule: PharmacySchedule
    @Binding var isPresentingInfo: Bool // Keep for backward compatibility
    @State private var isPresentingDayInfo: Bool = false
    @State private var isPresentingNightInfo: Bool = false
    let date: Date
    
    private var formattedDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .full
        formatter.locale = Locale(identifier: "es_ES")
        return formatter.string(from: date)
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                // Date with calendar icon
                HStack(spacing: ViewConstants.iconSpacing) {
                    Image(systemName: "calendar")
                        .foregroundColor(.secondary.opacity(0.7))
                        .frame(width: ViewConstants.iconColumnWidth)
                    Text(formattedDate)
                }
                .font(.title2)
                .padding(.bottom, 5)
                
                // Day shift section
                VStack(alignment: .leading, spacing: 12) {
                    ShiftHeaderView(shiftType: .day, date: date, isPresentingInfo: $isPresentingDayInfo)
                    if let pharmacy = schedule.dayShiftPharmacies.first {
                        PharmacyView(pharmacy: pharmacy)
                    }
                }
                .padding(.vertical)
                
                Divider()
                
                // Night shift section
                VStack(alignment: .leading, spacing: 12) {
                    ShiftHeaderView(shiftType: .night, date: date, isPresentingInfo: $isPresentingNightInfo)
                    if let pharmacy = schedule.nightShiftPharmacies.first {
                        PharmacyView(pharmacy: pharmacy)
                    }
                }
                .padding(.vertical)
                
                Divider()
                    .padding(.vertical)
                
                // Disclaimer and support section
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
                        
                        He encontrado un error en las farmacias de guardia mostradas para:
                        
                        Fecha: \(formattedDate)
                        
                        Turno diurno:
                        Farmacia mostrada: \(schedule.dayShiftPharmacies.first?.name ?? "")
                        Dirección: \(schedule.dayShiftPharmacies.first?.address ?? "")
                        
                        Turno nocturno:
                        Farmacia mostrada: \(schedule.nightShiftPharmacies.first?.name ?? "")
                        Dirección: \(schedule.nightShiftPharmacies.first?.address ?? "")
                        
                        La información correcta es:
                        
                        
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
