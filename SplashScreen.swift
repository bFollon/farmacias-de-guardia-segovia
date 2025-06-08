import SwiftUI

struct SplashScreen: View {
    var body: some View {
        VStack {
            Spacer()
            Text("Farmacias de Guardia")
                .font(.largeTitle)
                .foregroundColor(.blue)
            Spacer()
        }
        .background(Color.white)
    }
}

struct SplashScreen_Previews: PreviewProvider {
    static var previews: some View {
        SplashScreen()
    }
}
