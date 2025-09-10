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
 * Simple test class to run PDF URL scraping and see the output
 * This can be run from the terminal to debug the scraping
 */
object PDFURLScrapingTest {
    
    /**
     * Run the scraping test and print detailed output
     */
    fun runTest() {
        println("=== PDF URL Scraping Test ===")
        println("Testing PDF URL extraction...")
        
        runBlocking {
            try {
                val scrapedData = PDFURLScrapingService.scrapePDFURLs()
                
                println("=== TEST RESULTS ===")
                println("Scraped ${scrapedData.size} PDF URLs")
                
                if (scrapedData.isEmpty()) {
                    println("No PDF URLs found")
                } else {
                    scrapedData.forEachIndexed { index, data ->
                        println("${index + 1}. ${data.regionName}: ${data.pdfUrl}")
                    }
                }
                
            } catch (e: Exception) {
                println("Error during test: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}

// Main function for running from command line
fun main() {
    PDFURLScrapingTest.runTest()
}
