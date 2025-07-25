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
                    VStack(spacing: 0) {
                        VStack(spacing: 16) {
                            Text("Farmacia de Guardia en Segovia Capital")
                                .font(.title)
                                .padding(.horizontal)
                                .padding(.top, 16)
                                .multilineTextAlignment(.center)
                            
                            Divider()
                                .background(Color.gray.opacity(0.3))
                                .padding(.horizontal)
                        }
                        .background(Color(.systemBackground))
                        
                        ScheduleContentView(
                            schedule: schedule,
                            shiftType: shiftType,
                            isPresentingInfo: $isPresentingInfo,
                            formattedDateTime: ScheduleService.getCurrentDateTime()
                        )
                    }
                } else {
                    NoScheduleView()
                }
            }
            .navigationBarTitleDisplayMode(.inline)
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
