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

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service for downloading and caching PDF files
 */
class PDFDownloadService(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * Download a PDF file from URL and cache it locally
     * @param url The URL to download from
     * @param fileName The filename to save as (e.g., "segovia-capital.pdf")
     * @return The downloaded file, or null if download failed
     */
    suspend fun downloadPDF(url: String, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            println("PDFDownloadService: Starting download from $url")
            
            // Create cache directory if it doesn't exist
            val cacheDir = File(context.cacheDir, "pdfs")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val outputFile = File(cacheDir, fileName)
            
            // Check if file exists and is recent (less than 1 hour old)
            if (outputFile.exists() && (System.currentTimeMillis() - outputFile.lastModified()) < 3600000) {
                println("PDFDownloadService: Using cached file ${outputFile.absolutePath}")
                return@withContext outputFile
            }
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("PDFDownloadService: Download failed with code ${response.code}")
                    return@withContext null
                }
                
                val responseBody = response.body
                if (responseBody == null) {
                    println("PDFDownloadService: Response body is null")
                    return@withContext null
                }
                
                // Write to file
                outputFile.outputStream().use { outputStream ->
                    responseBody.byteStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                println("PDFDownloadService: Successfully downloaded ${outputFile.length()} bytes to ${outputFile.absolutePath}")
                outputFile
            }
        } catch (e: IOException) {
            println("PDFDownloadService: Error downloading PDF: ${e.message}")
            e.printStackTrace()
            null
        } catch (e: Exception) {
            println("PDFDownloadService: Unexpected error: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Clear all cached PDF files
     */
    fun clearCache() {
        val cacheDir = File(context.cacheDir, "pdfs")
        if (cacheDir.exists()) {
            val deletedFiles = cacheDir.listFiles()?.size ?: 0
            cacheDir.deleteRecursively()
            println("PDFDownloadService: Cleared $deletedFiles cached PDF files")
        }
    }
    
    /**
     * Get cache info for debugging
     */
    fun getCacheInfo(): String {
        val cacheDir = File(context.cacheDir, "pdfs")
        if (!cacheDir.exists()) {
            return "No cache directory"
        }
        
        val files = cacheDir.listFiles() ?: emptyArray()
        val totalSize = files.sumOf { it.length() }
        return "${files.size} cached files, ${totalSize / 1024} KB total"
    }
}
