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

package com.example.farmaciasdeguardiaensegovia.services

import kotlinx.coroutines.runBlocking

/**
 * Demo class to test PDF URL scraping functionality
 * This can be used to verify that the scraping works correctly
 */
object PDFURLScrapingDemo {
    
    /**
     * Run a demonstration of the PDF URL scraping
     * This will print the results to the console
     */
    fun runDemo() {
        println("=== PDF URL Scraping Demo ===")
        println("Scraping PDF URLs from cofsegovia.com...")
        
        runBlocking {
            try {
                val scrapedData = PDFURLScrapingService.scrapePDFURLs()
                
                println("\n=== SCRAPING RESULTS ===")
                if (scrapedData.isEmpty()) {
                    println("No PDF URLs found on the website")
                } else {
                    println("Found ${scrapedData.size} PDF URLs:")
                    println()
                    
                    scrapedData.forEachIndexed { index, data ->
                        println("${index + 1}. Region: ${data.regionName}")
                        println("   URL: ${data.pdfUrl}")
                        data.lastUpdated?.let { 
                            println("   Last Updated: $it")
                        }
                        println()
                    }
                    
                    // Show comparison with current hardcoded URLs
                    println("=== COMPARISON WITH CURRENT URLS ===")
                    val currentRegions = listOf(
                        "Segovia Capital" to "https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf",
                        "Cuéllar" to "https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf",
                        "El Espinar" to "https://cofsegovia.com/wp-content/uploads/2025/01/Guardias-EL-ESPINAR_2025.pdf",
                        "Segovia Rural" to "https://cofsegovia.com/wp-content/uploads/2025/06/SERVICIOS-DE-URGENCIA-RURALES-2025.pdf"
                    )
                    
                    currentRegions.forEach { (regionName, currentUrl) ->
                        val scrapedUrl = scrapedData.find { 
                            it.regionName.contains(regionName, ignoreCase = true) 
                        }?.pdfUrl
                        
                        println("$regionName:")
                        println("  Current: $currentUrl")
                        println("  Scraped: ${scrapedUrl ?: "Not found"}")
                        println("  Match: ${if (scrapedUrl == currentUrl) "✅ YES" else "❌ NO"}")
                        println()
                    }
                }
                
                println("=== DEMO COMPLETED ===")
                
            } catch (e: Exception) {
                println("Error during scraping demo: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
