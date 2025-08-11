import SwiftUI
import PDFKit

struct ContentView: View {
    @State private var selectedRegion: Region?
    @State private var showingZBSSelection = false
    @State private var showingSettings = false

    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                HStack {
                    Spacer()
                    Button(action: {
                        showingSettings = true
                    }) {
                        Image(systemName: "gearshape")
                            .font(.title2)
                            .foregroundColor(.primary)
                    }
                }
                .padding(.horizontal)
                .padding(.top)
                
                Text("Farmacias de Guardia")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)
                    .padding(.top)
                
                Text("Selecciona tu región para consultar las farmacias de guardia.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                
                LazyVGrid(columns: [
                    GridItem(.flexible()),
                    GridItem(.flexible())
                ], spacing: 16) {
                    ForEach(buttonData, id: \.self) { button in
                        Button(action: {
                            switch button.title {
                            case "Segovia Capital":
                                selectedRegion = .segoviaCapital
                            case "Cuéllar":
                                selectedRegion = .cuellar
                            case "El espinar / San Rafael":
                                selectedRegion = .elEspinar
                            case "Segovia Rural":
                                showingZBSSelection = true
                            default:
                                break
                            }
                        }) {
                            VStack(spacing: 8) {
                                Text(button.image)
                                    .font(.largeTitle)
                                
                                Text(button.title)
                                    .font(.headline)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(2)
                            }
                            .frame(maxWidth: .infinity, minHeight: 100)
                            .background(Color.blue.opacity(0.1))
                            .foregroundColor(.primary)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.blue.opacity(0.3), lineWidth: 1)
                            )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal)
                
                Spacer()
                
                VStack(spacing: 8) {
                    Text("Información")
                        .font(.footnote)
                        .fontWeight(.semibold)
                    
                    Text("Consulta siempre la fuente oficial para confirmar la información mostrada.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal)
                .padding(.bottom)
            }
            .navigationBarHidden(true)
        }
        .sheet(item: $selectedRegion) { region in
            PDFViewScreen(url: region.pdfURL, region: region)
        }
        .sheet(isPresented: $showingZBSSelection) {
            ZBSSelectionView(selectedRegion: $selectedRegion)
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView()
        }
    }
}

// Make Region conform to Identifiable
extension Region: Identifiable {}

let buttonData: [ButtonData] = [
    ButtonData(title: "Segovia Capital", image: "🏙"),
    ButtonData(title: "Cuéllar", image: "🌳"),
    ButtonData(title: "El espinar / San Rafael", image: "⛰"),
    ButtonData(title: "Segovia Rural", image: "🚜")
]

struct ButtonData: Hashable {
    let title: String
    let image: String
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
