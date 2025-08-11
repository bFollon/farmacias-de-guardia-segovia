import SwiftUI

struct ZBSSelectionView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedRegion: Region?
    @State private var selectedZBS: ZBS?
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Selecciona tu Zona Básica de Salud")
                    .font(.title2)
                    .fontWeight(.semibold)
                    .multilineTextAlignment(.center)
                    .padding(.top)
                
                Text("Elige la zona rural de Segovia para ver las farmacias de guardia correspondientes.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                
                LazyVGrid(columns: [
                    GridItem(.flexible()),
                    GridItem(.flexible())
                ], spacing: 16) {
                    ForEach(ZBS.availableZBS, id: \.id) { zbs in
                        Button(action: {
                            selectedZBS = zbs
                        }) {
                            VStack(spacing: 8) {
                                Text(zbs.icon)
                                    .font(.largeTitle)
                                
                                Text(zbs.name)
                                    .font(.headline)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(2)
                            }
                            .frame(maxWidth: .infinity, minHeight: 100)
                            .background(Color.green.opacity(0.1))
                            .foregroundColor(.primary)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.green.opacity(0.3), lineWidth: 1)
                            )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal)
                
                Spacer()
                
                VStack(spacing: 8) {
                    Text("Nota")
                        .font(.footnote)
                        .fontWeight(.semibold)
                    
                    Text("Cada zona básica de salud tiene sus farmacias asignadas según el calendario oficial.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal)
                .padding(.bottom)
            }
            .navigationTitle("Segovia Rural")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") {
                        dismiss()
                    }
                }
            }
        }
        .sheet(item: $selectedZBS) { zbs in
            ZBSScheduleView(selectedZBS: zbs)
        }
    }
}
