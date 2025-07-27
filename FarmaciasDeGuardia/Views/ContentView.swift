import SwiftUI

struct ContentView: View {
    @State private var selectedPDFURL: URL?

    var body: some View {
        VStack {
            Text("Farmacias de Guardia")
                .font(.largeTitle)
                .padding()
            Spacer()
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 20) {
                ForEach(buttonData, id: \.self) { button in
                    Button(action: {
                        if button.title == "Segovia Capital" {
                            selectedPDFURL = Bundle.main.url(forResource: "CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025", withExtension: "pdf")
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
        .sheet(item: $selectedPDFURL) { url in
            PDFViewScreen(url: url)
        }
    }
}

// Make URL conform to Identifiable so we can use sheet(item:)
extension URL: Identifiable {
    public var id: String { self.absoluteString }
}

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
