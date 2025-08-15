import SwiftUI

struct GuardiaInfoSheet: View {
    let shiftType: DutyDate.ShiftType
    let date: Date
    
    private var isEarlyMorning: Bool {
        let hour = Calendar.current.component(.hour, from: Date())
        let minute = Calendar.current.component(.minute, from: Date())
        return hour < 10 || (hour == 10 && minute < 15)
    }
    
    private var isCurrentDay: Bool {
        Calendar.current.isDateInToday(date)
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Horarios de Guardia")
                .font(.headline)
            
            if shiftType == .day {
                Text("El turno diurno empieza a las 10:15 y se extiende hasta las 22:00 del mismo día.")
                    .multilineTextAlignment(.leading)
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Text("El turno nocturno empieza a las 22:00 y se extiende hasta las 10:15 del día siguiente.")
                        .multilineTextAlignment(.leading)
                    
                    if isCurrentDay && isEarlyMorning {
                        Text("Por ello, la farmacia que está de guardia ahora comenzó su turno ayer a las 22:00.")
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.leading)
                    }
                }
            }
            
            Spacer()
        }
        .padding()
        .presentationDetents([.medium])
    }
}
