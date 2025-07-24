import SwiftUI
import PDFKit

struct PDFViewScreen: View {
    @State private var schedules: [PharmacySchedule] = []
    private let pdfService = PDFProcessingService()
    var url: URL

    var body: some View {
        NavigationView {
            Group {
                if let schedule = findTodaysSchedule() {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("\(schedule.date.dayOfWeek), \(schedule.date.day) de \(schedule.date.month) \(String(schedule.date.year ?? DutyDate.getCurrentYear()))")
                                .font(.title2)
                                .padding(.bottom, 5)
                            
                            // Day shift section
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Guardia diurna")
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                                ForEach(schedule.dayShiftPharmacies) { pharmacy in
                                    PharmacyView(pharmacy: pharmacy)
                                }
                            }
                            .padding(.bottom, 10)
                            
                            // Night shift section
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Guardia nocturna")
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                                ForEach(schedule.nightShiftPharmacies) { pharmacy in
                                    PharmacyView(pharmacy: pharmacy)
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

    private func findTodaysSchedule() -> PharmacySchedule? {
        let calendar = Calendar.current
        var components = DateComponents()
        components.year = calendar.component(.year, from: Date())
        components.month = calendar.component(.month, from: Date())
        components.day = calendar.component(.day, from: Date())
        components.hour = 0
        components.minute = 0
        components.second = 0
        
        guard let today = calendar.date(from: components) else { return nil }
        let todayTimestamp = today.timeIntervalSince1970
        
        return schedules.first { schedule in
            guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
            return Int(scheduleTimestamp) == Int(todayTimestamp)
        }
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
