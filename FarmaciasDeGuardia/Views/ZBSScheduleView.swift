import SwiftUI

struct ZBSScheduleView: View {
    let selectedZBS: ZBS
    @State private var zbsSchedules: [ZBSSchedule] = []
    @State private var selectedDate = Date()
    @State private var isLoading = true
    @Environment(\.presentationMode) var presentationMode
    
    private var todaySchedule: ZBSSchedule? {
        let calendar = Calendar.current
        
        // First try to find today's schedule
        let today = Date()
        let todayComponents = calendar.dateComponents([.day, .month, .year], from: today)
        
        print("🔍 ZBSScheduleView DEBUG: Looking for schedule on \(todayComponents.day!)/\(todayComponents.month!)/\(todayComponents.year!) for ZBS '\(selectedZBS.id)'")
        print("🔍 ZBSScheduleView DEBUG: Available schedules count: \(zbsSchedules.count)")
        
        // Try to find today's exact schedule
        if let todaySchedule = zbsSchedules.first(where: { schedule in
            let scheduleDay = schedule.date.day
            let scheduleMonth = monthNameToNumber(schedule.date.month) ?? 0
            let scheduleYear = schedule.date.year ?? Calendar.current.component(.year, from: today)
            
            return scheduleDay == todayComponents.day &&
                   scheduleMonth == todayComponents.month &&
                   scheduleYear == todayComponents.year
        }) {
            print("🔍 ZBSScheduleView DEBUG: Found today's schedule")
            let pharmacies = todaySchedule.pharmacies(for: selectedZBS.id)
            print("🔍 ZBSScheduleView DEBUG: Pharmacies for ZBS '\(selectedZBS.id)': \(pharmacies.map { $0.name })")
            return todaySchedule
        }
        
        // Fallback: find the first available schedule with pharmacies
        let fallbackSchedule = zbsSchedules.first { schedule in
            let pharmacies = schedule.pharmacies(for: selectedZBS.id)
            let hasPharmacies = !pharmacies.isEmpty
            if hasPharmacies {
                print("🔍 ZBSScheduleView DEBUG: Using fallback schedule for date \(schedule.date.day)/\(schedule.date.month)/\(schedule.date.year ?? 0)")
                print("🔍 ZBSScheduleView DEBUG: Pharmacies: \(pharmacies.map { $0.name })")
            }
            return hasPharmacies
        }
        
        return fallbackSchedule
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
    
    var body: some View {
        NavigationView {
            VStack {
                if isLoading {
                    LoadingView()
                } else if zbsSchedules.isEmpty {
                    NoScheduleView()
                } else if let schedule = todaySchedule {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            // Date with calendar icon
                            HStack(spacing: 8) {
                                Image(systemName: "calendar")
                                    .foregroundColor(.secondary.opacity(0.7))
                                    .frame(width: 20)
                                Text(formattedDate)
                            }
                            .font(.title2)
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
                            
                            // Pharmacies for this ZBS
                            let pharmacies = schedule.pharmacies(for: selectedZBS.id)
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Farmacias de Guardia")
                                    .font(.headline)
                                
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
                } else {
                    Text("No se encontraron farmacias para esta zona.")
                        .foregroundColor(.secondary)
                        .padding()
                }
            }
            .navigationTitle("\(selectedZBS.icon) \(selectedZBS.name)")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cerrar") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
        }
        .onAppear {
            loadZBSSchedules()
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
