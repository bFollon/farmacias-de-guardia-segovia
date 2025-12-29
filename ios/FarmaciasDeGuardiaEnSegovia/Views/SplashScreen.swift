/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

                // Show 4 PDF icons (representing the 4 PDFs being downloaded)
                if preloadService.isLoading {
                    // Icon progression for each PDF
                    HStack(spacing: 8) {
                        ForEach(0..<4, id: \.self) { index in
                            HStack(spacing: 8) {
                                Text(getRegionIcon(for: index))
                                    .font(.title2)
                                    .opacity(getPDFProgress(for: index) ? 1.0 : 0.3)
                                    .scaleEffect(getPDFProgress(for: index) ? 1.1 : 0.9)
                                    .animation(.spring(duration: 0.4), value: preloadService.completedRegions)

                                if index < 3 {
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

    private func getPDFProgress(for pdfIndex: Int) -> Bool {
        // Map PDF index to location progress
        // PDF 0 (Segovia Capital) = location 0
        // PDF 1 (Cuéllar) = location 1
        // PDF 2 (El Espinar) = location 2
        // PDF 3 (Segovia Rural) = locations 3-10 (8 ZBS)

        if pdfIndex < 3 {
            // Main regions: completed when that location is done
            return preloadService.completedRegions > pdfIndex
        } else {
            // Segovia Rural: completed when all ZBS are done (3 + 8 = 11 total)
            return preloadService.completedRegions >= 11
        }
    }
}

struct SplashScreen_Previews: PreviewProvider {
    static var previews: some View {
        SplashScreen()
    }
}
