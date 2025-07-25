import SwiftUI
import PDFKit

struct PDFViewScreen: View {
    @State private var schedules: [PharmacySchedule] = []
    private let pdfService = PDFProcessingService()
    var url: URL

    var body: some View {
        NavigationView {
            Group {
                if let (schedule, shiftType) = findTodaysSchedule() {
                    let today = Date()
                    let dateFormatter: DateFormatter = {
                        let formatter = DateFormatter()
                        formatter.locale = Locale(identifier: "es_ES")
                        formatter.setLocalizedDateFormatFromTemplate("EEEE d MMMM")
                        return formatter
                    }()
                    
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Para hoy, \(dateFormatter.string(from: today)) a las \(today.formatted(.dateTime.hour().minute()))")
                                .font(.title2)
                                .padding(.bottom, 5)
                            
                            // Show the active shift with context help
                            HStack(alignment: .center, spacing: 8) {
                                Text(shiftType == .day ? "Guardia diurna (10:15 - 22:00)" : "Guardia nocturna (22:00 - 10:15)")
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                                
                                Button(action: {}) {
                                    Image(systemName: "info.circle")
                                }
                                .help(shiftType == .day ? "Farmacia de guardia para el horario diurno de hoy" : "Farmacia que comenzó su guardia nocturna el \(schedule.date.dayOfWeek) \(schedule.date.day) a las 22:00 y continúa hasta las 10:15 de hoy")
                                .foregroundColor(.secondary)
                            }
                            
                            // Show pharmacy list
                            VStack(alignment: .leading, spacing: 8) {
                                if shiftType == .day {
                                    ForEach(schedule.dayShiftPharmacies) { pharmacy in
                                        PharmacyView(pharmacy: pharmacy)
                                    }
                                } else {
                                    ForEach(schedule.nightShiftPharmacies) { pharmacy in
                                        PharmacyView(pharmacy: pharmacy)
                                    }
                                }
                            }
                        }
                        .padding()
                    }
                } else {
                    VStack(spacing: 20) {
                        Text("No hay farmacias de guardia programadas para hoy")
                            .font(.headline)
                            .multilineTextAlignment(.center)
                        
                        Text("(\(Date().formatted(date: .long, time: .omitted)))")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                }
            }
            .navigationTitle("Farmacias de Guardia Hoy")
        }
        .onAppear {
            loadPharmacies()
        }
    }

    private func findTodaysSchedule() -> (PharmacySchedule, DutyDate.ShiftType)? {
        let now = Date()
        let currentTimestamp = now.timeIntervalSince1970
        
        // Get the duty time info for current timestamp
        let dutyInfo = DutyDate.dutyTimeInfoForTimestamp(currentTimestamp)
        
        // Find the schedule for the required date (using dutyInfo.date)
        guard let schedule = schedules.first(where: { schedule in
            // Both dates should have the same day and month
            return schedule.date.day == dutyInfo.date.day &&
                   schedule.date.year == dutyInfo.date.year &&
                   DutyDate.monthToNumber(schedule.date.month) == Calendar.current.component(.month, from: now)
        }) else {
            return nil
        }
        
        return (schedule, dutyInfo.shiftType)
    }
    private func loadPharmacies() {
        schedules = pdfService.loadPharmacies(from: url)
    }
}

struct PDFViewScreen_Previews: PreviewProvider {
    static var previews: some View {
        PDFViewScreen(
            url: Bundle.main.url(
                forResource: "CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025",
                withExtension: "pdf")!)
    }
}
