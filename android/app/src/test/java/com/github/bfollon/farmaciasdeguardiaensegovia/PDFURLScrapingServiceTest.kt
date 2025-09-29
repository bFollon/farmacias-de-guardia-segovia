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

package com.github.bfollon.farmaciasdeguardiaensegovia

import com.github.bfollon.farmaciasdeguardiaensegovia.services.PDFURLScrapingService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/**
 * Test class for PDFURLScrapingService
 * Tests the web scraping functionality to ensure PDF URLs are extracted correctly
 */
class PDFURLScrapingServiceTest {
    
    @Test
    fun testScrapePDFURLs() = runBlocking {
        // This test will actually make a network request to the real website
        // Uses OkHttp + regex instead of Jsoup - much lighter!
        // In a production environment, you might want to mock this or use a test server
        
        println("Testing PDF URL scraping with OkHttp + regex...")
        
        val scrapedData = PDFURLScrapingService.scrapePDFURLs()
        
        // Print results for manual verification
        println("Scraped ${scrapedData.size} PDF URLs:")
        scrapedData.forEachIndexed { index, data ->
            println("${index + 1}. ${data.regionName}: ${data.pdfUrl}")
            data.lastUpdated?.let { println("   Last updated: $it") }
        }
        
        // Basic assertions
        assertNotNull("Scraped data should not be null", scrapedData)
        
        // We expect to find at least some PDF URLs
        // The exact number may vary depending on the website structure
        assertTrue("Should find at least one PDF URL", scrapedData.isNotEmpty())
        
        // Verify that all found URLs are actually PDFs
        scrapedData.forEach { data ->
            assertTrue("URL should end with .pdf: ${data.pdfUrl}", data.pdfUrl.endsWith(".pdf"))
            assertTrue("Region name should not be empty", data.regionName.isNotEmpty())
        }
        
        println("PDF URL scraping test completed successfully!")
    }
    
    @Test
    fun testScrapedDataStructure() = runBlocking {
        val scrapedData = PDFURLScrapingService.scrapePDFURLs()
        
        // Test the structure of scraped data
        scrapedData.forEach { data ->
            // Test that region names are valid
            val validRegions = listOf(
                "Segovia Capital", 
                "Cuéllar", 
                "El Espinar", 
                "Segovia Rural"
            )
            
            assertTrue(
                "Region name should be one of the expected regions: ${data.regionName}",
                validRegions.any { data.regionName.contains(it, ignoreCase = true) }
            )
            
            // Test that URLs are valid
            assertTrue("URL should start with http: ${data.pdfUrl}", data.pdfUrl.startsWith("http"))
            assertTrue("URL should end with .pdf: ${data.pdfUrl}", data.pdfUrl.endsWith(".pdf"))
        }
    }
}
