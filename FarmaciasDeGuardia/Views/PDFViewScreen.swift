import SwiftUI
import PDFKit

struct PDFViewScreen: View {
    @State private var schedules: [PharmacySchedule] = []
    @State private var isPresentingInfo = false
    @State private var isRefreshing = false
    @State private var isLoading = true
    @State private var selectedDate: Date = Date()
    @State private var isShowingDatePicker = false
    var url: URL
    var region: Region
    
    private var dateButtonText: String {
        if Calendar.current.isDateInToday(selectedDate) {
            return "Hoy"
        } else {
            let formatter = DateFormatter()
            formatter.dateStyle = .medium
            return formatter.string(from: selectedDate)
        }
    }
    
    var body: some View {
        NavigationView {
            Group {
                if isLoading || isRefreshing {
                    VStack(spacing: 0) {
                        VStack(spacing: 16) {
                            Text("Farmacia de Guardia en \(region.name)")
                                .font(.title)
                                .padding(.horizontal)
                                .padding(.top, 16)
                                .multilineTextAlignment(.center)
                            
                            Divider()
                                .background(Color.gray.opacity(0.3))
                                .padding(.horizontal)
                        }
                        .background(Color(.systemBackground))
                        
                        LoadingView()
                    }
                } else if let schedule = ScheduleService.findSchedule(for: selectedDate, in: schedules) {
                    VStack(spacing: 0) {
                        VStack(spacing: 16) {
                            Text("Farmacia de Guardia en \(region.name)")
                                .font(.title)
                                .padding(.horizontal)
                                .padding(.top, 16)
                                .multilineTextAlignment(.center)
                            
                            Divider()
                                .background(Color.gray.opacity(0.3))
                                .padding(.horizontal)
                        }
                        .background(Color(.systemBackground))
                        
                        if Calendar.current.isDateInToday(selectedDate),
                           let current = ScheduleService.findCurrentSchedule(in: schedules) {
                            ScheduleContentView(
                                schedule: current.0,
                                shiftType: current.1,
                                region: region,
                                isPresentingInfo: $isPresentingInfo,
                                formattedDateTime: ScheduleService.getCurrentDateTime()
                            )
                        } else {
                            DayScheduleView(
                                schedule: schedule,
                                region: region,
                                isPresentingInfo: $isPresentingInfo,
                                date: selectedDate
                            )
                        }
                    }
                } else {
                    NoScheduleView()
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        isShowingDatePicker = true
                    } label: {
                        HStack {
                            Image(systemName: "calendar")
                            Text(dateButtonText)
                        }
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        refreshData()
                    }) {
                        Image(systemName: isRefreshing ? "arrow.clockwise.circle.fill" : "arrow.clockwise.circle")
                            .imageScale(.large)
                    }
                    .disabled(isRefreshing)
                }
            }
            .sheet(isPresented: $isShowingDatePicker) {
                NavigationView {
                    DatePicker("Seleccionar fecha",
                             selection: $selectedDate,
                             in: ...Date().addingTimeInterval(86400 * 365), // Up to 1 year in the future
                             displayedComponents: .date
                    )
                    .datePickerStyle(.graphical)
                    .padding()
                    .onChange(of: selectedDate) { oldDate, newDate in
                        isShowingDatePicker = false
                    }
                    .navigationTitle("Seleccionar fecha")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("Listo") {
                                isShowingDatePicker = false
                            }
                        }
                    }
                }
                .presentationDetents([.medium])
            }
        }
        .onAppear {
            loadPharmacies()
        }
    }
    private func loadPharmacies() {
        isLoading = true
        DispatchQueue.global(qos: .userInitiated).async {
            let loadedSchedules = ScheduleService.loadSchedules(for: region)
            DispatchQueue.main.async {
                schedules = loadedSchedules
                isLoading = false
            }
        }
    }
    
    private func refreshData() {
        isRefreshing = true
        
        // Use GCD to prevent UI blocking
        DispatchQueue.global(qos: .userInitiated).async {
            let refreshedSchedules = ScheduleService.loadSchedules(for: region, forceRefresh: true)
            
            // Update UI on main thread
            DispatchQueue.main.async {
                schedules = refreshedSchedules
                isRefreshing = false
            }
        }
    }
}

struct PDFViewScreen_Previews: PreviewProvider {
    static var previews: some View {
        PDFViewScreen(
            url: Bundle.main.url(
                forResource: "CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025",
                withExtension: "pdf")!,
            region: .segoviaCapital
        )
    }
}
