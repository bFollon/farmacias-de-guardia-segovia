import SwiftUI
import PDFKit

struct ContentView: View {
    @State private var selectedRegion: Region?

    var body: some View {
        VStack {
            Text("Farmacias de Guardia")
                .font(.largeTitle)
                .padding()
            Spacer()
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 20) {
                ForEach(buttonData, id: \.self) { button in
                    Button(action: {
                        switch button.title {
                        case "Segovia Capital":
                            selectedRegion = .segoviaCapital
                        case "Cuéllar":
                            selectedRegion = .cuellar
                        case "El espinar / San Rafael":
                            selectedRegion = .elEspinar
                        default:
                            break
                        }
                    }) {
                        VStack {
                            Text(button.image)
                                .font(.largeTitle)
                            Text(button.title)
                                .font(.headline)
                        }
                    }
                    .frame(width: 100, height: 100)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
            }
            Spacer()
        }
        .sheet(item: $selectedRegion) { region in
            PDFViewScreen(url: region.pdfURL, region: region)
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
