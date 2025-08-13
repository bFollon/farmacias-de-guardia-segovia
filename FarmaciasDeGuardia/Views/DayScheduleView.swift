import SwiftUI

struct DayScheduleView: View {
    let schedule: PharmacySchedule
    let region: Region
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
                // Region name at the top (matching ZBS style)
                HStack {
                    Text(region.icon)
                        .font(.title)
                    Text(region.name)
                        .font(.title)
                        .fontWeight(.medium)
                }
                .padding(.bottom, 5)
                
                // Date with calendar icon
                HStack(spacing: 8) {
                    Image(systemName: "calendar.circle.fill")
                        .foregroundColor(.blue)
                        .frame(width: 20)
                    Text(formattedDate)
                        .font(.title2)
                        .fontWeight(.medium)
                }
                .padding(.bottom, 20)
                
                // Pharmacy section with header
                VStack(alignment: .leading, spacing: 12) {
                    Text("Farmacias de Guardia")
                        .font(.headline)
                    
                    if region.id == "segovia-capital" {
                        // Show day/night shifts for Segovia Capital
                        VStack(alignment: .leading, spacing: 12) {
                            ShiftHeaderView(shiftType: .day, date: date, isPresentingInfo: $isPresentingDayInfo)
                            if let pharmacy = schedule.dayShiftPharmacies.first {
                                PharmacyView(pharmacy: pharmacy)
                            }
                        }
                        .padding(.vertical)
                        
                        Divider()
                        
                        VStack(alignment: .leading, spacing: 12) {
                            ShiftHeaderView(shiftType: .night, date: date, isPresentingInfo: $isPresentingNightInfo)
                            if let pharmacy = schedule.nightShiftPharmacies.first {
                                PharmacyView(pharmacy: pharmacy)
                            }
                        }
                        .padding(.vertical)
                    } else {
                        // Show single pharmacy for other regions
                        if let pharmacy = schedule.dayShiftPharmacies.first {
                            PharmacyView(pharmacy: pharmacy)
                        }
                    }
                }
                
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
                    
                    Link("Calendario de Guardias - \(region.name)",
                         destination: region.pdfURL)
                        .font(.footnote)
                    
                    let emailBody = AppConfig.EmailLinks.dayScheduleErrorBody(
                        date: formattedDate,
                        dayPharmacyName: schedule.dayShiftPharmacies.first?.name ?? "",
                        dayPharmacyAddress: schedule.dayShiftPharmacies.first?.address ?? "",
                        nightPharmacyName: schedule.nightShiftPharmacies.first?.name ?? "",
                        nightPharmacyAddress: schedule.nightShiftPharmacies.first?.address ?? ""
                    )
                    
                    if let errorURL = AppConfig.EmailLinks.errorReport(body: emailBody) {
                        Link("¿Ha encontrado algún error? Repórtelo aquí",
                             destination: errorURL)
                            .font(.footnote)
                            .padding(.top, 8)
                    }
                }
            }
            .padding()
        }
    }
}
