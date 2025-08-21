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

package com.farmaciasdeGuardia.services

import com.bfollon.farmaciasdeGuardia.data.model.PharmacySchedule
import com.bfollon.farmaciasdeGuardia.data.model.Region
import com.farmaciasdeGuardia.services.pdfparsing.PDFParsingStrategyFactory
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android equivalent of iOS PDFProcessingService
 * Handles parsing PDF files to extract pharmacy schedule information using PDFBox
 */
@Singleton
class PDFProcessingService @Inject constructor(
    private val parsingStrategyFactory: PDFParsingStrategyFactory
) {
    
    // Cache for processed results to avoid reprocessing the same file
    private val processingCache = mutableMapOf<String, List<PharmacySchedule>>()
    
    /**
     * Load pharmacy schedules from a PDF file
     * Equivalent to iOS PDFProcessingService.loadPharmacies
     * 
     * @param pdfFile The PDF file to process
     * @param region The region this PDF belongs to
     * @param forceRefresh Whether to bypass any internal caching
     * @return List of pharmacy schedules extracted from the PDF
     */
    suspend fun loadPharmacies(
        pdfFile: File,
        region: Region,
        forceRefresh: Boolean = false
    ): List<PharmacySchedule> {
        val cacheKey = "${pdfFile.absolutePath}_${pdfFile.lastModified()}_${region.name}"
        
        // Check cache first (unless force refresh is requested)
        if (!forceRefresh && processingCache.containsKey(cacheKey)) {
            DebugConfig.debugPrint("📄 PDFProcessingService: Returning cached results for ${region.name}")
            return processingCache[cacheKey] ?: emptyList()
        }
        
        DebugConfig.debugPrint("📄 PDFProcessingService: Processing PDF for ${region.name}")
        DebugConfig.debugPrint("📄 PDF file: ${pdfFile.absolutePath}")
        DebugConfig.debugPrint("📄 File size: ${pdfFile.length()} bytes")
        DebugConfig.debugPrint("📄 File exists: ${pdfFile.exists()}")
        DebugConfig.debugPrint("📄 Force refresh: $forceRefresh")
        
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            DebugConfig.debugPrint("❌ PDFProcessingService: PDF file does not exist or is empty")
            return emptyList()
        }
        
        return try {
            // Load PDF document
            val document = PDDocument.load(pdfFile)
            
            try {
                DebugConfig.debugPrint("📄 PDFProcessingService: PDF loaded successfully, ${document.numberOfPages} pages")
                
                // Get the appropriate parsing strategy for this region
                val parsingStrategy = parsingStrategyFactory.getParsingStrategy(region)
                
                // Parse the schedules using the strategy
                val schedules = parsingStrategy.parseSchedules(document)
                
                // Cache the results
                processingCache[cacheKey] = schedules
                
                DebugConfig.debugPrint("✅ PDFProcessingService: Successfully parsed ${schedules.size} schedules from ${region.name} PDF")
                
                // Log some sample data for debugging
                if (schedules.isNotEmpty()) {
                    DebugConfig.debugPrint("📄 Sample schedule dates: ${schedules.take(3).map { "${it.date.day} ${it.date.month}" }}")
                    DebugConfig.debugPrint("📄 First schedule shifts: ${schedules.first().shifts.keys.map { it.name }}")
                }
                
                schedules
                
            } finally {
                // Always close the document to free memory
                document.close()
            }
            
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ PDFProcessingService: Error processing PDF for ${region.name}: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Clear the internal processing cache
     */
    suspend fun clearCache() {
        processingCache.clear()
        DebugConfig.debugPrint("📄 PDFProcessingService: Processing cache cleared")
    }
    
    /**
     * Clear cache for a specific region
     */
    suspend fun clearCacheForRegion(region: Region) {
        val keysToRemove = processingCache.keys.filter { it.contains(region.name) }
        keysToRemove.forEach { processingCache.remove(it) }
        DebugConfig.debugPrint("📄 PDFProcessingService: Cleared cache for region ${region.name} (${keysToRemove.size} entries)")
    }
    
    /**
     * Get processing statistics for debugging
     */
    fun getProcessingStats(): String {
        val cacheSize = processingCache.size
        val totalSchedules = processingCache.values.sumOf { it.size }
        return "PDFProcessingService: $cacheSize cached files, $totalSchedules total schedules"
    }
    
    /**
     * Get detailed cache information for debugging
     */
    fun getCacheInfo(): Map<String, Any> {
        return mapOf(
            "cacheSize" to processingCache.size,
            "totalSchedules" to processingCache.values.sumOf { it.size },
            "cachedFiles" to processingCache.keys.map { key ->
                val parts = key.split("_")
                mapOf(
                    "file" to parts.getOrNull(0)?.substringAfterLast("/"),
                    "region" to parts.getOrNull(2),
                    "scheduleCount" to (processingCache[key]?.size ?: 0)
                )
            }
        )
    }
}
