import SwiftUI
import PDFKit

struct PDFViewScreen: View {
    @State private var schedules: [PharmacySchedule] = []
    @State private var isPresentingInfo = false
    private let pdfService = PDFProcessingService()
    var url: URL
    
    var body: some View {
        NavigationView {
            Group {
                if let (schedule, shiftType) = ScheduleService.findCurrentSchedule(in: schedules) {
                    ScheduleContentView(
                        schedule: schedule,
                        shiftType: shiftType,
                        isPresentingInfo: $isPresentingInfo,
                        formattedDateTime: ScheduleService.getCurrentDateTime()
                    )
                } else {
                    NoScheduleView()
                }
            }
            .navigationTitle("Farmacias de Guardia Hoy")
        }
        .onAppear {
            loadPharmacies()
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
