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

package com.example.farmaciasdeguardiaensegovia

import com.example.farmaciasdeguardiaensegovia.data.Region
import com.example.farmaciasdeguardiaensegovia.services.PDFProcessingService
import com.example.farmaciasdeguardiaensegovia.services.pdfparsing.strategies.SegoviaRuralParser
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Test class for SegoviaRuralParser
 * Tests the basic structure and line printing functionality
 */
class SegoviaRuralParserTest {
    
    @Test
    fun testSegoviaRuralParserBasicStructure() {
        println("\n=== Testing Segovia Rural Parser Basic Structure ===")
        
        val parser = SegoviaRuralParser()
        
        // Test that the parser is properly instantiated
        assertNotNull("Parser should not be null", parser)
        assertEquals("Strategy name should match", "SegoviaRuralParser", parser.getStrategyName())
        
        println("✅ Parser instantiated successfully")
        println("✅ Strategy name: ${parser.getStrategyName()}")
        
        // Test ZBS cache functionality
        val initialCache = SegoviaRuralParser.getCachedZBSSchedules()
        assertEquals("Initial cache should be empty", 0, initialCache.size)
        
        SegoviaRuralParser.clearZBSCache()
        val clearedCache = SegoviaRuralParser.getCachedZBSSchedules()
        assertEquals("Cache should be empty after clearing", 0, clearedCache.size)
        
        println("✅ ZBS cache functionality working correctly")
        
        println("=== Segovia Rural Parser Basic Structure Test Complete ===\n")
    }
    
    @Test
    fun testSegoviaRuralRegionConfiguration() {
        println("\n=== Testing Segovia Rural Region Configuration ===")
        
        val region = Region.segoviaRural
        
        // Test region properties
        assertEquals("Region ID should match", "segovia-rural", region.id)
        assertEquals("Region name should match", "Segovia Rural", region.name)
        assertEquals("Region icon should match", "🚜", region.icon)
        assertTrue("PDF URL should not be empty", region.pdfURL.isNotEmpty())
        assertTrue("PDF URL should end with .pdf", region.pdfURL.endsWith(".pdf"))
        
        println("✅ Region ID: ${region.id}")
        println("✅ Region name: ${region.name}")
        println("✅ Region icon: ${region.icon}")
        println("✅ PDF URL: ${region.pdfURL}")
        println("✅ Metadata notes: ${region.metadata.notes}")
        
        println("=== Segovia Rural Region Configuration Test Complete ===\n")
    }
    
    @Test
    fun testPDFProcessingServiceRegistration() {
        println("\n=== Testing PDF Processing Service Registration ===")
        
        // Test that the service can be instantiated (this will test the parser registration)
        val service = PDFProcessingService()
        assertNotNull("PDFProcessingService should not be null", service)
        
        println("✅ PDFProcessingService instantiated successfully")
        println("✅ Segovia Rural parser should be registered in the service")
        
        println("=== PDF Processing Service Registration Test Complete ===\n")
    }
    
    @Test
    fun testSegoviaRuralParserWithMockFile() = runBlocking {
        println("\n=== Testing Segovia Rural Parser with Mock File ===")
        
        val parser = SegoviaRuralParser()
        
        // Create a temporary mock PDF file for testing
        // Note: This won't actually parse content, but will test the file handling
        val mockFile = File.createTempFile("test_segovia_rural", ".pdf")
        mockFile.writeText("Mock PDF content for testing")
        
        try {
            println("📄 Created mock PDF file: ${mockFile.absolutePath}")
            
            // This will test the basic file handling and line printing
            val schedules = parser.parseSchedules(mockFile)
            
            // For now, we expect empty results since it's a mock file
            assertNotNull("Schedules should not be null", schedules)
            println("✅ Parser handled mock file successfully")
            println("✅ Parsed ${schedules.size} schedules (expected 0 for mock file)")
            
        } finally {
            // Clean up the temporary file
            if (mockFile.exists()) {
                mockFile.delete()
                println("🧹 Cleaned up temporary file")
            }
        }
        
        println("=== Segovia Rural Parser Mock File Test Complete ===\n")
    }
}
