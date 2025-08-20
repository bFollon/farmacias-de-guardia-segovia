import SwiftUI

struct SplashScreen: View {
    @State private var isAnimating = false
    @StateObject private var preloadService = PreloadService.shared
    
    var body: some View {
        VStack(spacing: 30) {
            Spacer()
            
            // App Logo with animation
            VStack(spacing: 20) {
                // Use the splash logo
                Image("SplashLogo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 120, height: 120)
                    .clipShape(RoundedRectangle(cornerRadius: 26))
                    .shadow(color: .black.opacity(0.2), radius: 10, x: 0, y: 5)
                    .scaleEffect(isAnimating ? 1.0 : 0.8)
                    .opacity(isAnimating ? 1.0 : 0.0)
                    .animation(.easeOut(duration: 0.8), value: isAnimating)
                
                // App Title with gradient
                VStack(spacing: 8) {
                    Text("Farmacias de guardia")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .foregroundStyle(
                            LinearGradient(
                                colors: [.blue, .green],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                    
                    Text("en Segovia")
                        .font(.title2)
                        .fontWeight(.medium)
                        .foregroundColor(.secondary)
                }
                .opacity(isAnimating ? 1.0 : 0.0)
                .scaleEffect(isAnimating ? 1.0 : 0.9)
                .animation(.easeOut(duration: 0.8).delay(0.5), value: isAnimating)
            }
            
            Spacer()
            
            // Loading indicator with icon progression
            VStack(spacing: 16) {
                ProgressView()
                    .scaleEffect(1.2)
                    .tint(.blue)
                    .opacity(preloadService.isLoading ? 1.0 : 0.0)
                    .animation(.easeInOut(duration: 0.3), value: preloadService.isLoading)
                
                // Always show icon progression once we know the total regions
                if preloadService.totalRegions > 0 {
                    // Icon progression for each region
                    HStack(spacing: 8) {
                        ForEach(0..<preloadService.totalRegions, id: \.self) { index in
                            HStack(spacing: 8) {
                                Text(getRegionIcon(for: index))
                                    .font(.title2)
                                    .opacity(index < preloadService.completedRegions ? 1.0 : 0.3)
                                    .scaleEffect(index < preloadService.completedRegions ? 1.1 : 0.9)
                                    .animation(.spring(duration: 0.4), value: preloadService.completedRegions)
                                
                                if index < preloadService.totalRegions - 1 {
                                    Text("—")
                                        .font(.caption)
                                        .foregroundColor(.secondary.opacity(0.5))
                                }
                            }
                        }
                    }
                }
            }
            .opacity(isAnimating ? 1.0 : 0.0)
            .animation(.easeOut(duration: 0.8).delay(0.6), value: isAnimating)
            
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            // Subtle gradient background
            LinearGradient(
                colors: [
                    Color(.systemBackground),
                    Color.blue.opacity(0.05)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        )
        .edgesIgnoringSafeArea(.all)
        .onAppear {
            isAnimating = true
        }
    }
    
    // MARK: - Helper Methods
    
    private func getRegionIcon(for index: Int) -> String {
        let regions = [
            Region.segoviaCapital,
            Region.cuellar,
            Region.elEspinar,
            Region.segoviaRural
        ]
        
        guard index < regions.count else { return "💊" }
        return regions[index].icon
    }
}

struct SplashScreen_Previews: PreviewProvider {
    static var previews: some View {
        SplashScreen()
    }
}
