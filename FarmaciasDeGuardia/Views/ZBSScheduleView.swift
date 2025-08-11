import SwiftUI

struct ZBSScheduleView: View {
    let selectedZBS: ZBS
    @State private var zbsSchedules: [ZBSSchedule] = []
    @State private var selectedDate = Date()
    @State private var isLoading = true
    @State private var isShowingDatePicker = false
    @Environment(\.presentationMode) var presentationMode
    
    private var dateButtonText: String {
        if Calendar.current.isDateInToday(selectedDate) {
            return "Hoy"
        } else {
            let formatter = DateFormatter()
            formatter.dateStyle = .medium
            formatter.locale = Locale(identifier: "es_ES")
            return formatter.string(from: selectedDate)
        }
    }
    
    private var selectedSchedule: ZBSSchedule? {
        let calendar = Calendar.current
        let selectedComponents = calendar.dateComponents([.day, .month, .year], from: selectedDate)
        
        print("🔍 ZBSScheduleView DEBUG: Looking for schedule on \(selectedComponents.day!)/\(selectedComponents.month!)/\(selectedComponents.year!) for ZBS '\(selectedZBS.id)'")
        print("🔍 ZBSScheduleView DEBUG: Available schedules count: \(zbsSchedules.count)")
        
        // Try to find the selected date's schedule
        if let schedule = zbsSchedules.first(where: { schedule in
            let scheduleDay = schedule.date.day
            let scheduleMonth = monthNameToNumber(schedule.date.month) ?? 0
            let scheduleYear = schedule.date.year ?? Calendar.current.component(.year, from: selectedDate)
            
            return scheduleDay == selectedComponents.day &&
                   scheduleMonth == selectedComponents.month &&
                   scheduleYear == selectedComponents.year
        }) {
            print("🔍 ZBSScheduleView DEBUG: Found schedule for selected date")
            let pharmacies = schedule.pharmacies(for: selectedZBS.id)
            print("🔍 ZBSScheduleView DEBUG: Pharmacies for ZBS '\(selectedZBS.id)': \(pharmacies.map { $0.name })")
            return schedule
        }
        
        print("🔍 ZBSScheduleView DEBUG: No schedule found for selected date")
        return nil
    }
    
    private func monthNameToNumber(_ monthName: String) -> Int? {
        let monthNames = ["ene": 1, "feb": 2, "mar": 3, "abr": 4, "may": 5, "jun": 6,
                         "jul": 7, "ago": 8, "sep": 9, "oct": 10, "nov": 11, "dic": 12]
        return monthNames[monthName]
    }
    
    private var formattedDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .full
        formatter.locale = Locale(identifier: "es_ES")
        return formatter.string(from: selectedDate)
    }
    
    private var dateRange: ClosedRange<Date> {
        let calendar = Calendar.current
        
        // Find the earliest and latest dates from available schedules
        guard !zbsSchedules.isEmpty else {
            // Default range: today ± 1 year
            let today = Date()
            let yearAgo = calendar.date(byAdding: .year, value: -1, to: today) ?? today
            let yearFromNow = calendar.date(byAdding: .year, value: 1, to: today) ?? today
            return yearAgo...yearFromNow
        }
        
        var earliestDate = Date()
        var latestDate = Date()
        
        for schedule in zbsSchedules {
            if let year = schedule.date.year,
               let month = monthNameToNumber(schedule.date.month) {
                var components = DateComponents()
                components.year = year
                components.month = month
                components.day = schedule.date.day
                
                if let date = calendar.date(from: components) {
                    if zbsSchedules.firstIndex(where: { $0.date.day == schedule.date.day && $0.date.month == schedule.date.month && $0.date.year == schedule.date.year }) == 0 {
                        earliestDate = date
                        latestDate = date
                    } else {
                        if date < earliestDate { earliestDate = date }
                        if date > latestDate { latestDate = date }
                    }
                }
            }
        }
        
        return earliestDate...latestDate
    }
    
    var body: some View {
        NavigationView {
            VStack {
                if isLoading {
                    LoadingView()
                } else if zbsSchedules.isEmpty {
                    NoScheduleView()
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            // Selected date display
                            HStack(spacing: 8) {
                                Image(systemName: "calendar.circle.fill")
                                    .foregroundColor(.blue)
                                    .frame(width: 20)
                                Text(formattedDate)
                                    .font(.title2)
                                    .fontWeight(.medium)
                            }
                            .padding(.bottom, 5)
                            
                            // ZBS Info
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Zona Básica de Salud")
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                                
                                HStack {
                                    Text(selectedZBS.icon)
                                        .font(.title)
                                    Text(selectedZBS.name)
                                        .font(.title2)
                                        .fontWeight(.semibold)
                                }
                            }
                            .padding(.bottom)
                            
                            // Pharmacies for this ZBS on selected date
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Farmacias de Guardia")
                                    .font(.headline)
                                
                                if let schedule = selectedSchedule {
                                    let pharmacies = schedule.pharmacies(for: selectedZBS.id)
                                    
                                    if !pharmacies.isEmpty {
                                        ForEach(pharmacies.indices, id: \.self) { index in
                                            PharmacyView(pharmacy: pharmacies[index])
                                        }
                                    } else {
                                        VStack(alignment: .leading, spacing: 8) {
                                            HStack {
                                                Image(systemName: "exclamationmark.triangle")
                                                    .foregroundColor(.orange)
                                                Text("Sin farmacia de guardia")
                                                    .font(.headline)
                                                    .foregroundColor(.orange)
                                            }
                                            
                                            Text("No hay farmacia de guardia asignada para \(selectedZBS.name) en esta fecha.")
                                                .font(.body)
                                                .foregroundColor(.secondary)
                                            
                                            Text("Por favor, consulte las farmacias de guardia de otras zonas cercanas o el calendario oficial.")
                                                .font(.footnote)
                                                .foregroundColor(.secondary)
                                        }
                                        .padding()
                                        .background(Color.orange.opacity(0.1))
                                        .cornerRadius(8)
                                    }
                                } else {
                                    VStack(alignment: .leading, spacing: 8) {
                                        HStack {
                                            Image(systemName: "calendar.badge.exclamationmark")
                                                .foregroundColor(.red)
                                            Text("Fecha no disponible")
                                                .font(.headline)
                                                .foregroundColor(.red)
                                        }
                                        
                                        Text("No hay información disponible para la fecha seleccionada.")
                                            .font(.body)
                                            .foregroundColor(.secondary)
                                        
                                        Text("Por favor, seleccione una fecha diferente del calendario de guardias.")
                                            .font(.footnote)
                                            .foregroundColor(.secondary)
                                    }
                                    .padding()
                                    .background(Color.red.opacity(0.1))
                                    .cornerRadius(8)
                                }
                            }
                            
                            Divider()
                                .padding(.vertical)
                            
                            // Disclaimer section
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Aviso")
                                    .font(.footnote)
                                    .fontWeight(.semibold)
                                
                                Text("La información mostrada puede no ser exacta. Por favor, consulte siempre la fuente oficial.")
                                    .font(.footnote)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle("\(selectedZBS.icon) \(selectedZBS.name)")
            .navigationBarTitleDisplayMode(.large)
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
                    Button("Cerrar") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
        }
        .onAppear {
            loadZBSSchedules()
        }
        .sheet(isPresented: $isShowingDatePicker) {
            NavigationView {
                DatePicker("Seleccionar fecha",
                         selection: $selectedDate,
                         in: dateRange,
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
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Hoy") {
                            selectedDate = Date()
                        }
                        .disabled(Calendar.current.isDate(selectedDate, inSameDayAs: Date()))
                    }
                    
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
    
    private func loadZBSSchedules() {
        isLoading = true
        
        DispatchQueue.global(qos: .userInitiated).async {
            let schedules = ZBSScheduleService.getZBSSchedules(for: .segoviaRural) ?? []
            
            DispatchQueue.main.async {
                self.zbsSchedules = schedules
                self.isLoading = false
            }
        }
    }
}

struct ZBSScheduleView_Previews: PreviewProvider {
    static var previews: some View {
        ZBSScheduleView(selectedZBS: ZBS.availableZBS[0])
    }
}
