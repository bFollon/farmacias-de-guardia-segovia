import SwiftUI
import PDFKit

struct PDFViewScreen: View {
    @State private var schedules: [PharmacySchedule] = []
    @State private var isPresentingInfo = false
    private let pdfService = PDFProcessingService()
    var url: URL
    
    private var formattedDateTime: String {
        let today = Date()
        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "es_ES")
        dateFormatter.setLocalizedDateFormatFromTemplate("EEEE d MMMM")
        return "\(dateFormatter.string(from: today)) · \(today.formatted(.dateTime.hour().minute()))"
    }

    var body: some View {
        NavigationView {
            Group {
                if let (schedule, shiftType) = findTodaysSchedule() {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(formattedDateTime)
                                .font(.title2)
                                .padding(.bottom, 5)
                            
                            // Show the active shift with context help
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
