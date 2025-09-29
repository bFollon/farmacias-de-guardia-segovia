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

package com.github.bfollon.farmaciasdeguardiaensegovia.services

import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.github.bfollon.farmaciasdeguardiaensegovia.data.Region
import com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.PDFParsingStrategy
import com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.strategies.CuellarParser
import com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.strategies.ElEspinarParser
import com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.strategies.SegoviaCapitalParser
import com.github.bfollon.farmaciasdeguardiaensegovia.services.pdfparsing.strategies.SegoviaRuralParser
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Android equivalent of iOS PDFProcessingService
 * Handles parsing PDF files to extract pharmacy schedule information using PDFBox
 */
class PDFProcessingService {

    // Registry of parsing strategies for each region
    private val parsingStrategies: Map<String, PDFParsingStrategy> = mapOf(
        "segovia-capital" to SegoviaCapitalParser(),
        "cuellar" to CuellarParser(),
        "el-espinar" to ElEspinarParser(),
        "segovia-rural" to SegoviaRuralParser()
    )

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
        region: Region
    ): Map<DutyLocation, List<PharmacySchedule>> {
        DebugConfig.debugPrint("\n=== PDF Processing Started ===")
        DebugConfig.debugPrint("PDFProcessingService: Processing PDF for ${region.name}")
        DebugConfig.debugPrint("📁 PDF file: ${pdfFile.absolutePath}")
        DebugConfig.debugPrint("📏 File size: ${pdfFile.length()} bytes")
        DebugConfig.debugPrint("📄 File exists: ${pdfFile.exists()}")
        DebugConfig.debugPrint("🔄 Force refresh: ${region.forceRefresh}")

        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            DebugConfig.debugError("PDFProcessingService: PDF file does not exist or is empty")
            return emptyMap()
        }

        return try {
            // Get the appropriate parsing strategy for this region
            val parsingStrategy = getParsingStrategy(region)
            DebugConfig.debugPrint("📋 Using parsing strategy: ${parsingStrategy::class.simpleName}")

            // Parse the schedules using the strategy
            DebugConfig.debugPrint("⚙️ Starting PDF parsing...")
            val scheduleMap = parsingStrategy.parseSchedules(pdfFile)

            scheduleMap.forEach { (location, schedules) ->
                DebugConfig.debugPrint("✅ PDFProcessingService: Successfully parsed ${schedules.size} schedules from ${location.name} PDF")
            }

            // Log some sample data for debugging
            if (scheduleMap.isEmpty()) {
                DebugConfig.debugWarn("⚠️ No schedules were parsed from the PDF!")
            }

            DebugConfig.debugPrint("=== PDF Processing Complete ===\n")
            scheduleMap
        } catch (e: Exception) {
            DebugConfig.debugError(
                "❌ PDFProcessingService: Error processing PDF for ${region.name}: ${e.message}",
                e
            )
            emptyMap()
        }
    }

    /**
     * Get the appropriate parsing strategy for a region
     */
    private fun getParsingStrategy(region: Region): PDFParsingStrategy {
        return parsingStrategies[region.id] ?: SegoviaCapitalParser()
    }

}
