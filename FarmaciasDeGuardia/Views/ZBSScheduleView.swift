import SwiftUI

struct ZBSScheduleView: View {
    let selectedZBS: ZBS
    @State private var zbsSchedules: [ZBSSchedule] = []
    @State private var selectedDate = Date()
    @State private var isLoading = true
    @Environment(\.presentationMode) var presentationMode
    
    private var todaySchedule: ZBSSchedule? {
        let calendar = Calendar.current
        return zbsSchedules.first { schedule in
            // For now, find the first available schedule
            // In the future, match by actual date
            return !schedule.pharmacies(for: selectedZBS.id).isEmpty
        }
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
                            if !pharmacies.isEmpty {
                                VStack(alignment: .leading, spacing: 12) {
                                    Text("Farmacias de Guardia")
                                        .font(.headline)
                                    
                                    ForEach(pharmacies.indices, id: \.self) { index in
                                        PharmacyView(pharmacy: pharmacies[index])
                                    }
                                }
                            } else {
                                Text("No hay farmacias de guardia disponibles para esta zona hoy.")
                                    .foregroundColor(.secondary)
                                    .padding()
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
