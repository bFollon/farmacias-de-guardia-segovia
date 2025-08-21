/*
 * Farmacias de Guardia - Segovia
 * Copyright (C) 2024 Bruno Follón
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.farmaciasdeGuardia.services.pdfparsing

import com.bfollon.farmaciasdeGuardia.data.model.Region
import com.farmaciasdeGuardia.services.pdfparsing.strategies.CuellarParser
import com.farmaciasdeGuardia.services.pdfparsing.strategies.ElEspinarParser
import com.farmaciasdeGuardia.services.pdfparsing.strategies.SegoviaCapitalParser
import com.farmaciasdeGuardia.services.pdfparsing.strategies.SegoviaRuralParser
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating the appropriate PDF parsing strategy for each region.
 * Android equivalent of the iOS parser selection logic.
 */
@Singleton
class PDFParsingStrategyFactory @Inject constructor() {
    
    /**
     * Get the appropriate parsing strategy for a given region
     */
    fun getParsingStrategy(region: Region): PDFParsingStrategy {
        val strategy = when (region) {
            Region.Capital -> SegoviaCapitalParser()
            Region.Rural -> SegoviaRuralParser()
            Region.ElEspinar -> ElEspinarParser()
            Region.Cuellar -> CuellarParser()
            Region.Cantalejos -> {
                DebugConfig.debugPrint("⚠️ Using rural parser for Cantalejo (specialized parser not implemented)")
                SegoviaRuralParser() // Fallback to rural parser
            }
        }
        
        DebugConfig.debugPrint("📄 Selected parsing strategy: ${strategy.getStrategyName()} for region: ${region.name}")
        return strategy
    }
    
    /**
     * Get all available parsing strategies for testing/debugging
     */
    fun getAllStrategies(): List<Pair<Region, PDFParsingStrategy>> {
        return listOf(
            Region.Capital to SegoviaCapitalParser(),
            Region.Rural to SegoviaRuralParser(),
            Region.ElEspinar to ElEspinarParser(),
            Region.Cuellar to CuellarParser(),
            Region.Cantalejos to SegoviaRuralParser()
        )
    }
}
