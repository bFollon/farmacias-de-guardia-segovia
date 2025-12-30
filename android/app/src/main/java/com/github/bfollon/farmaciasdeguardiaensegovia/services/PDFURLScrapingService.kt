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

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Service for scraping PDF URLs from the stable cofsegovia.com page
 * This prevents PDF URLs from becoming stale by fetching the latest links at app startup
 * Uses OkHttp and regex patterns - much lighter than Jsoup!
 */
object PDFURLScrapingService {
    
    private const val TAG = "PDFURLScrapingService"
    private const val BASE_URL = "https://cofsegovia.com/farmacias-de-guardia/"
    
    // Cache for scraped PDF URLs by region name
    private val scrapedURLs = mutableMapOf<String, String>()
    
    // Flag to track if scraping has completed
    @Volatile
    private var scrapingCompleted = false
    
    // OkHttp client for making requests
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Scraped PDF URL data for each region
     */
    data class ScrapedPDFData(
        val regionName: String,
        val pdfUrl: String,
        val lastUpdated: String? = null
    )
    
    /**
     * Get the scraped URL for a specific region
     * Returns the scraped URL if available, otherwise returns null
     */
    fun getScrapedURL(regionName: String): String? {
        return if (scrapingCompleted) {
            scrapedURLs[regionName]
        } else {
            null
        }
    }
    
    /**
     * Check if scraping has completed
     */
    fun isScrapingCompleted(): Boolean {
        return scrapingCompleted
    }
    
    /**
     * Scrape the cofsegovia.com page to extract current PDF URLs
     * This runs on the IO dispatcher to avoid blocking the main thread
     */
    suspend fun scrapePDFURLs(): List<ScrapedPDFData> = withContext(Dispatchers.IO) {
        // Start performance span
        val span = TelemetryService.startSpan("pdf.url.scraping", SpanKind.CLIENT)
        span.setAttribute("url", BASE_URL)
        span.setAttribute("source", "web_scraping")

        try {
            DebugConfig.debugPrint("$TAG: Starting PDF URL scraping from $BASE_URL")

            // Make HTTP request
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:13.0) Gecko/13.0 Firefox/13.0")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                DebugConfig.debugError("$TAG: HTTP request failed: ${response.code}")
                span.setAttribute("http_status", response.code.toLong())
                span.setAttribute("error_message", "HTTP ${response.code}")
                span.setAttribute("urls_found", 0L)
                span.setAttribute("status", "failed")
                span.setAttribute("error.type", "unavailable")
                span.setStatus(StatusCode.ERROR, "HTTP ${response.code}")
                span.end()
                return@withContext emptyList()
            }

            val htmlContent = response.body?.string() ?: ""
            DebugConfig.debugPrint("$TAG: Successfully fetched HTML content (${htmlContent.length} chars)")

            // Extract PDF links using regex patterns
            val scrapedData = extractPDFDataFromHTML(htmlContent)

            DebugConfig.debugPrint("$TAG: Successfully scraped ${scrapedData.size} PDF URLs")

            // Cache the scraped URLs for later use
            scrapedData.forEach { data ->
                scrapedURLs[data.regionName] = data.pdfUrl
                DebugConfig.debugPrint("$TAG: Found PDF for ${data.regionName}: ${data.pdfUrl}")
                data.lastUpdated?.let {
                    DebugConfig.debugPrint("$TAG: Last updated: $it")
                }
            }

            // Mark scraping as completed
            scrapingCompleted = true
            DebugConfig.debugPrint("$TAG: Scraping completed, ${scrapedURLs.size} URLs cached")

            // Finish span successfully
            span.setAttribute("urls_found", scrapedData.size.toLong())
            span.setAttribute("status", if (scrapedData.size == 4) "success" else "partial")
            span.setStatus(StatusCode.OK)
            span.end()

            scrapedData

        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: Error scraping PDF URLs", e)
            span.setAttribute("error_message", e.message ?: "Unknown error")
            span.setAttribute("urls_found", 0L)
            span.setAttribute("status", "failed")
            span.setAttribute("error.type", "internal_error")
            span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
            span.end()
            emptyList()
        }
    }
    
    /**
     * Extract PDF data from HTML content using regex patterns
     * Much simpler and lighter than DOM parsing!
     */
    private fun extractPDFDataFromHTML(htmlContent: String): List<ScrapedPDFData> {
        val scrapedData = mutableListOf<ScrapedPDFData>()
        
        try {
            // Use the simple pattern that works and remove duplicates
            val simplePdfPattern = Regex("""href="([^"]*\.pdf)"""", RegexOption.IGNORE_CASE)
            val simpleMatches = simplePdfPattern.findAll(htmlContent)
            DebugConfig.debugPrint("$TAG: Found ${simpleMatches.count()} PDF links in HTML")
            
            // Convert to set to remove duplicates, then back to list
            val uniquePdfUrls = simpleMatches.map { it.groupValues[1] }.toSet()
            DebugConfig.debugPrint("$TAG: After removing duplicates: ${uniquePdfUrls.size} unique PDF URLs")
            
            // Process each unique PDF link
            uniquePdfUrls.forEach { pdfUrl ->
                // Convert relative URLs to absolute
                val absoluteUrl = if (pdfUrl.startsWith("http")) {
                    pdfUrl
                } else {
                    "https://cofsegovia.com$pdfUrl"
                }
                
                // Try to determine the region name from context
                // Since we don't have link text from the simple pattern, we'll use the URL and surrounding context
                val regionName = determineRegionNameFromURL(absoluteUrl, htmlContent)
                
                if (regionName.isNotEmpty()) {
                    scrapedData.add(
                        ScrapedPDFData(
                            regionName = regionName,
                            pdfUrl = absoluteUrl,
                            lastUpdated = extractLastUpdatedDateFromHTML(htmlContent)
                        )
                    )
                } else {
                    DebugConfig.debugWarn("$TAG: Could not determine region for PDF: $absoluteUrl")
                }
            }
            
            // If we didn't find any region-specific PDFs, try alternative extraction methods
            if (scrapedData.isEmpty()) {
                DebugConfig.debugPrint("$TAG: No region-specific PDFs found, trying alternative extraction...")
                val alternativeData = extractPDFsByRegionSections(htmlContent)
                scrapedData.addAll(alternativeData)
            }
            
        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: Error extracting PDF data from HTML", e)
        }
        
        return scrapedData
    }
    
    /**
     * Determine the region name based on PDF URL and surrounding context
     */
    private fun determineRegionNameFromURL(pdfUrl: String, htmlContent: String): String {
        // First try to determine from the URL itself
        val urlLower = pdfUrl.lowercase()
        
        when {
            urlLower.contains("segovia") && urlLower.contains("capital") -> return "Segovia Capital"
            urlLower.contains("cuellar") || urlLower.contains("cuéllar") -> return "Cuéllar"
            urlLower.contains("espinar") -> return "El Espinar"
            urlLower.contains("rural") -> return "Segovia Rural"
        }
        
        // If URL doesn't contain region info, look in surrounding context
        // Find the context around this URL in the HTML
        val contextStart = maxOf(0, htmlContent.indexOf(pdfUrl) - 200)
        val contextEnd = minOf(htmlContent.length, htmlContent.indexOf(pdfUrl) + pdfUrl.length + 200)
        val context = htmlContent.substring(contextStart, contextEnd).lowercase()
        
        val regionKeywords = mapOf(
            "segovia" to "Segovia Capital",
            "capital" to "Segovia Capital", 
            "cuellar" to "Cuéllar",
            "cuéllar" to "Cuéllar",
            "espinar" to "El Espinar",
            "san rafael" to "El Espinar",
            "rural" to "Segovia Rural"
        )
        
        regionKeywords.forEach { (keyword, regionName) ->
            if (context.contains(keyword)) {
                return regionName
            }
        }
        
        return ""
    }
    
    /**
     * Determine the region name based on link text and surrounding context
     */
    private fun determineRegionNameFromText(linkText: String, htmlContent: String): String {
        // Check if the link text itself contains region information
        val regionKeywords = mapOf(
            "segovia" to "Segovia Capital",
            "capital" to "Segovia Capital", 
            "cuellar" to "Cuéllar",
            "cuéllar" to "Cuéllar",
            "espinar" to "El Espinar",
            "san rafael" to "El Espinar",
            "rural" to "Segovia Rural"
        )
        
        val searchText = linkText.lowercase()
        
        regionKeywords.forEach { (keyword, regionName) ->
            if (searchText.contains(keyword)) {
                return regionName
            }
        }
        
        // If link text doesn't contain region info, look in surrounding context
        // Find the context around this link in the HTML
        val contextStart = maxOf(0, htmlContent.indexOf(linkText) - 200)
        val contextEnd = minOf(htmlContent.length, htmlContent.indexOf(linkText) + linkText.length + 200)
        val context = htmlContent.substring(contextStart, contextEnd).lowercase()
        
        regionKeywords.forEach { (keyword, regionName) ->
            if (context.contains(keyword)) {
                return regionName
            }
        }
        
        return ""
    }
    
    /**
     * Alternative extraction method that looks for region sections using regex
     */
    private fun extractPDFsByRegionSections(htmlContent: String): List<ScrapedPDFData> {
        val scrapedData = mutableListOf<ScrapedPDFData>()
        
        try {
            // Look for headings that might indicate regions using regex
            val headingPattern = Regex("""<h[1-6][^>]*>(.*?)</h[1-6]>""", RegexOption.IGNORE_CASE)
            val headings = headingPattern.findAll(htmlContent)
            
            headings.forEach { headingMatch ->
                val headingText = headingMatch.groupValues[1].trim().lowercase()
                val regionName = when {
                    headingText.contains("segovia") && headingText.contains("capital") -> "Segovia Capital"
                    headingText.contains("cuellar") || headingText.contains("cuéllar") -> "Cuéllar"
                    headingText.contains("espinar") || headingText.contains("san rafael") -> "El Espinar"
                    headingText.contains("rural") -> "Segovia Rural"
                    else -> null
                }
                
                if (regionName != null) {
                    // Look for PDF links in the section after this heading
                    val sectionStart = headingMatch.range.last
                    val sectionEnd = minOf(htmlContent.length, sectionStart + 1000) // Look in next 1000 chars
                    val section = htmlContent.substring(sectionStart, sectionEnd)
                    
                    val pdfPattern = Regex("""<a[^>]*href="([^"]*\.pdf)"[^>]*>""", RegexOption.IGNORE_CASE)
                    val pdfMatches = pdfPattern.findAll(section)
                    
                    pdfMatches.forEach { pdfMatch ->
                        val pdfUrl = pdfMatch.groupValues[1]
                        val absoluteUrl = if (pdfUrl.startsWith("http")) {
                            pdfUrl
                        } else {
                            "https://cofsegovia.com$pdfUrl"
                        }
                        
                        if (absoluteUrl.isNotEmpty()) {
                            scrapedData.add(
                                ScrapedPDFData(
                                    regionName = regionName,
                                    pdfUrl = absoluteUrl,
                                    lastUpdated = extractLastUpdatedDateFromHTML(htmlContent)
                                )
                            )
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: Error in alternative PDF extraction", e)
        }
        
        return scrapedData
    }
    
    /**
     * Extract the last updated date from the HTML content using regex
     */
    private fun extractLastUpdatedDateFromHTML(htmlContent: String): String? {
        try {
            // Look for text that might contain update dates
            val updatePatterns = listOf(
                "actualización",
                "última actualización", 
                "fecha de actualización",
                "updated"
            )
            
            updatePatterns.forEach { pattern ->
                val regex = Regex("$pattern[^\\d]*(\\d{1,2}[^\\d]*\\d{4})", RegexOption.IGNORE_CASE)
                val match = regex.find(htmlContent)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
            
        } catch (e: Exception) {
            DebugConfig.debugError("$TAG: Error extracting last updated date", e)
        }
        
        return null
    }
    
    /**
     * Print scraped data to console for debugging
     */
    fun printScrapedData(data: List<ScrapedPDFData>) {
        DebugConfig.debugPrint("$TAG: ===== SCRAPED PDF URLS =====")
        if (data.isEmpty()) {
            DebugConfig.debugPrint("$TAG: No PDF URLs found")
        } else {
            data.forEachIndexed { index, pdfData ->
                DebugConfig.debugPrint("$TAG: ${index + 1}. ${pdfData.regionName}")
                DebugConfig.debugPrint("$TAG:    URL: ${pdfData.pdfUrl}")
                pdfData.lastUpdated?.let { 
                    DebugConfig.debugPrint("$TAG:    Last Updated: $it")
                }
            }
        }
        DebugConfig.debugPrint("$TAG: ============================")
    }
}
