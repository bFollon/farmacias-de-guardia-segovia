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

import Foundation

/// Demo class to test PDF URL scraping functionality
/// This can be used to verify that the scraping works correctly
class PDFURLScrapingDemo {
    
    /// Run a demonstration of the PDF URL scraping
    /// This will print the results to the console
    static func runDemo() async {
        print("=== PDF URL Scraping Demo ===")
        print("Scraping PDF URLs from cofsegovia.com...")
        
        let scrapedData = await PDFURLScrapingService.shared.scrapePDFURLs()
        
        print("\n=== SCRAPING RESULTS ===")
        if scrapedData.isEmpty {
            print("No PDF URLs found")
        } else {
            print("Found \(scrapedData.count) PDF URLs:")
            print()
            
            for (index, data) in scrapedData.enumerated() {
                print("\(index + 1). Region: \(data.regionName)")
                print("   URL: \(data.pdfURL.absoluteString)")
                if let lastUpdated = data.lastUpdated {
                    print("   Last Updated: \(lastUpdated)")
                }
                print()
            }
            
            // Show comparison with current hardcoded URLs
            print("=== COMPARISON WITH CURRENT URLS ===")
            let currentRegions = [
                ("Segovia Capital", "https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf"),
                ("Cuéllar", "https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf"),
                ("El Espinar", "https://cofsegovia.com/wp-content/uploads/2025/01/Guardias-EL-ESPINAR_2025.pdf"),
                ("Segovia Rural", "https://cofsegovia.com/wp-content/uploads/2025/06/SERVICIOS-DE-URGENCIA-RURALES-2025.pdf")
            ]
            
            for (regionName, currentURL) in currentRegions {
                let scrapedURL = scrapedData.first { 
                    $0.regionName.contains(regionName) 
                }?.pdfURL.absoluteString
                
                print("\(regionName):")
                print("  Current: \(currentURL)")
                print("  Scraped: \(scrapedURL ?? "Not found")")
                print("  Match: \(scrapedURL == currentURL ? "✅ YES" : "❌ NO")")
                print()
            }
        }
        
        print("=== DEMO COMPLETED ===")
    }
}
