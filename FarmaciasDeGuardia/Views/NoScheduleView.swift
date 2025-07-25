import SwiftUI

struct NoScheduleView: View {
    var body: some View {
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
