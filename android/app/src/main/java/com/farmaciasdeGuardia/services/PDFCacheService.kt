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

import android.content.Context
import android.content.SharedPreferences
import com.bfollon.farmaciasdeGuardia.data.model.Region
import com.bfollon.farmaciasdeGuardia.util.DebugConfig
import com.farmaciasdeGuardia.database.dao.CachedPDFDao
import com.farmaciasdeGuardia.database.entities.CachedPDF
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.buffer
import okio.sink
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress state for update operations
 * Equivalent to iOS UpdateProgressState
 */
sealed class UpdateProgressState {
    object Checking : UpdateProgressState()
    object UpToDate : UpdateProgressState()
    object Downloading : UpdateProgressState()
    object Downloaded : UpdateProgressState()
    data class Error(val message: String) : UpdateProgressState()
}

/**
 * PDF version information for tracking updates
 * Equivalent to iOS PDFVersion struct
 */
data class PDFVersion(
    val url: String,
    val lastModified: Date? = null,
    val contentLength: Long? = null,
    val etag: String? = null,
    val downloadDate: Date = Date()
)

/**
 * Cache status information for a specific region
 * Equivalent to iOS RegionCacheStatus
 */
data class RegionCacheStatus(
    val region: Region,
    val isCached: Boolean,
    val downloadDate: Date?,
    val fileSize: Long?,
    val lastChecked: Date?,
    val needsUpdate: Boolean
) {
    val formattedFileSize: String
        get() = fileSize?.let { formatFileSize(it) } ?: "Unknown"
    
    val formattedDownloadDate: String
        get() = downloadDate?.let { formatDate(it) } ?: "Never"
    
    val formattedLastChecked: String
        get() = lastChecked?.let { formatDate(it) } ?: "Never"
    
    val statusText: String
        get() = when {
            !isCached -> "Not Downloaded"
            needsUpdate -> "Update Available"
            else -> "Up to Date"
        }
    
    companion object {
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "${bytes} B"
                bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
                else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            }
        }
        
        private fun formatDate(date: Date): String {
            val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            return formatter.format(date)
        }
    }
}

/**
 * Android equivalent of iOS PDFCacheManager
 * Manages local caching and updating of PDF files using Room database
 */
@Singleton
class PDFCacheService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cachedPDFDao: CachedPDFDao,
    private val httpClient: OkHttpClient,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val LAST_UPDATE_CHECK_KEY = "pdf_cache_last_update_check"
        private const val CACHE_DIR_NAME = "pdf_cache"
        private val HTTP_DATE_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss 'GMT'",     // RFC 1123
            "EEEE, dd-MMM-yy HH:mm:ss 'GMT'",     // RFC 850
            "EEE MMM d HH:mm:ss yyyy"             // ANSI C asctime()
        )
    }
    
    // MARK: - Storage Locations
    
    private val cacheDirectory: File by lazy {
        File(context.filesDir, CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
                DebugConfig.debugPrint("📁 PDFCacheService: Cache directory created at $absolutePath")
            }
        }
    }
    
    // MARK: - File Management
    
    /**
     * Get the local cache file for a region if it exists
     */
    suspend fun getCachedFile(for region: Region): File? {
        val cachedPDF = cachedPDFDao.getCachedPDF(region.name) ?: return null
        val file = File(cachedPDF.localFilePath)
        return if (file.exists()) file else null
    }
    
    /**
     * Generate cache filename for a region
     */
    private fun getCacheFileName(for region: Region): String {
        return when (region) {
            Region.SEGOVIA_CAPITAL -> "segovia-capital.pdf"
            Region.CUELLAR -> "cuellar.pdf"
            Region.EL_ESPINAR -> "el-espinar.pdf"
            Region.SEGOVIA_RURAL -> "segovia-rural.pdf"
        }
    }
    
    /**
     * Get the target file path for a region
     */
    private fun getCacheFilePath(for region: Region): String {
        return File(cacheDirectory, getCacheFileName(for region)).absolutePath
    }
    
    // MARK: - Version Checking
    
    /**
     * Check remote PDF version without downloading the full file
     */
    suspend fun checkRemoteVersion(for region: Region): Result<PDFVersion> {
        return try {
            val request = Request.Builder()
                .url(region.pdfURL)
                .head() // HEAD request like iOS
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(
                    Exception("Failed to get remote PDF info: HTTP ${response.code}")
                )
            }
            
            val lastModifiedString = response.header("Last-Modified")
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            val etag = response.header("ETag")
            
            val lastModifiedDate = lastModifiedString?.let { parseHttpDate(it) }
            
            val version = PDFVersion(
                url = region.pdfURL,
                lastModified = lastModifiedDate,
                contentLength = contentLength,
                etag = etag
            )
            
            response.close()
            Result.success(version)
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ PDFCacheService: Failed to check remote version for ${region.name}: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Check if cached version is up to date
     */
    suspend fun isCacheUpToDate(for region: Region, debugMode: Boolean = false): Boolean {
        val cachedPDF = cachedPDFDao.getCachedPDF(region.name)
        val cacheFile = getCachedFile(for region)
        
        if (cachedPDF == null || cacheFile == null || !cacheFile.exists()) {
            DebugConfig.debugPrint("🔍 PDFCacheService: No cached file or database entry for ${region.name}")
            return false
        }
        
        val remoteVersionResult = checkRemoteVersion(for region)
        if (remoteVersionResult.isFailure) {
            DebugConfig.debugPrint("❌ PDFCacheService: Failed to check remote version for ${region.name}")
            // If we can't check remote, assume cache is valid for now
            return true
        }
        
        val remoteVersion = remoteVersionResult.getOrThrow()
        
        DebugConfig.debugPrint("🔍 PDFCacheService: Comparing versions for ${region.name}:")
        if (debugMode) {
            DebugConfig.debugPrint("   Cached ETag: ${cachedPDF.etag}")
            DebugConfig.debugPrint("   Remote ETag: ${remoteVersion.etag}")
            DebugConfig.debugPrint("   Cached Last-Modified: ${cachedPDF.lastModified}")
            DebugConfig.debugPrint("   Remote Last-Modified: ${remoteVersion.lastModified}")
            DebugConfig.debugPrint("   Cached Size: ${cachedPDF.contentLength}")
            DebugConfig.debugPrint("   Remote Size: ${remoteVersion.contentLength}")
        }
        
        // 1. First try Last-Modified (most reliable for this server)
        if (cachedPDF.lastModified != null && remoteVersion.lastModified != null) {
            val isMatch = cachedPDF.lastModified == remoteVersion.lastModified
            if (debugMode) {
                DebugConfig.debugPrint("   ✅ Last-Modified comparison: ${if (isMatch) "MATCH" else "DIFFERENT"}")
            }
            return isMatch
        }
        
        // 2. Then try Content-Length as backup
        if (cachedPDF.contentLength != null && remoteVersion.contentLength != null) {
            val isMatch = cachedPDF.contentLength == remoteVersion.contentLength
            if (debugMode) {
                DebugConfig.debugPrint("   ✅ Content-Length comparison: ${if (isMatch) "MATCH" else "DIFFERENT"}")
            }
            return isMatch
        }
        
        // 3. Finally try ETag (least reliable for this server)
        if (cachedPDF.etag != null && remoteVersion.etag != null) {
            val isMatch = cachedPDF.etag == remoteVersion.etag
            if (debugMode) {
                DebugConfig.debugPrint("   ✅ ETag comparison: ${if (isMatch) "MATCH" else "DIFFERENT"}")
            }
            return isMatch
        }
        
        // If no comparison criteria available, consider outdated
        DebugConfig.debugPrint("   ❌ No comparison criteria available, assuming outdated")
        return false
    }
    
    // MARK: - Download and Cache
    
    /**
     * Download and cache a PDF file
     */
    suspend fun downloadAndCache(
        region: Region,
        progressCallback: ((Double) -> Unit)? = null
    ): Result<File> {
        return try {
            val request = Request.Builder()
                .url(region.pdfURL)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                response.close()
                return Result.failure(
                    Exception("Failed to download PDF: HTTP ${response.code}")
                )
            }
            
            val responseBody = response.body ?: run {
                response.close()
                return Result.failure(Exception("Empty response body"))
            }
            
            val targetFile = File(getCacheFilePath(for region))
            
            // Ensure parent directory exists
            targetFile.parentFile?.mkdirs()
            
            // Download with progress tracking
            downloadWithProgress(responseBody, targetFile, progressCallback)
            
            // Extract response headers
            val lastModifiedString = response.header("Last-Modified")
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            val etag = response.header("ETag")
            
            val lastModifiedDate = lastModifiedString?.let { parseHttpDate(it) }
            val actualFileSize = targetFile.length()
            
            // Store in database
            val cachedPDF = CachedPDF.create(
                regionName = region.name,
                localFilePath = targetFile.absolutePath,
                remoteUrl = region.pdfURL,
                lastModified = lastModifiedDate,
                contentLength = contentLength,
                etag = etag,
                fileSize = actualFileSize
            )
            
            cachedPDFDao.insertOrUpdateCachedPDF(cachedPDF)
            
            response.close()
            DebugConfig.debugPrint("✅ PDFCacheService: Successfully cached PDF for ${region.name}")
            Result.success(targetFile)
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ PDFCacheService: Failed to download PDF for ${region.name}: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Download file with progress tracking
     */
    private fun downloadWithProgress(
        responseBody: ResponseBody,
        targetFile: File,
        progressCallback: ((Double) -> Unit)?
    ) {
        val contentLength = responseBody.contentLength()
        var totalBytesRead = 0L
        
        responseBody.source().use { source ->
            targetFile.sink().buffer().use { sink ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (source.read(buffer).also { bytesRead = it } != -1) {
                    sink.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    if (contentLength > 0 && progressCallback != null) {
                        val progress = totalBytesRead.toDouble() / contentLength.toDouble()
                        progressCallback(progress)
                    }
                }
            }
        }
    }
    
    /**
     * Get the effective PDF file (cached if available and up-to-date, otherwise download)
     */
    suspend fun getEffectivePDFFile(for region: Region): Result<File> {
        // Check if we have a valid cached version
        val isCacheValid = isCacheUpToDate(for region)
        
        if (isCacheValid) {
            getCachedFile(for region)?.let { cachedFile ->
                return Result.success(cachedFile)
            }
        }
        
        // Cache is outdated or doesn't exist, download
        return downloadAndCache(region)
    }
    
    // MARK: - Cache Management
    
    /**
     * Check if a cached file exists for the region
     */
    suspend fun hasCachedFile(for region: Region): Boolean {
        return cachedPDFDao.isPDFCached(region.name) && getCachedFile(for region) != null
    }
    
    /**
     * Clear all cached PDF files
     */
    suspend fun clearCache() {
        try {
            // Get all file paths before clearing database
            val filePaths = cachedPDFDao.getAllCachedFilePaths()
            
            // Delete physical files
            filePaths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                    DebugConfig.debugPrint("🗑️ PDFCacheService: Removed cached file at $path")
                }
            }
            
            // Clear database entries
            cachedPDFDao.clearAllCachedPDFs()
            
            DebugConfig.debugPrint("🗑️ PDFCacheService: Cleared all cached files and database entries")
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ PDFCacheService: Error clearing cache: ${e.message}")
        }
    }
    
    /**
     * Clear cache for a specific region
     */
    suspend fun clearCache(for region: Region) {
        try {
            // Get cached PDF info
            val cachedPDF = cachedPDFDao.getCachedPDF(region.name)
            
            // Delete physical file
            cachedPDF?.let { cached ->
                val file = File(cached.localFilePath)
                if (file.exists()) {
                    file.delete()
                    DebugConfig.debugPrint("🗑️ PDFCacheService: Removed cached file for ${region.name}")
                }
            }
            
            // Remove from database
            cachedPDFDao.deleteCachedPDFByRegion(region.name)
        } catch (e: Exception) {
            DebugConfig.debugPrint("❌ PDFCacheService: Error clearing cache for ${region.name}: ${e.message}")
        }
    }
    
    /**
     * Force download for a region (bypasses cache check)
     */
    suspend fun forceDownload(for region: Region): Result<File> {
        // Clear existing cache first
        clearCache(for region)
        
        // Download fresh copy
        return downloadAndCache(region)
    }
    
    // MARK: - Automatic Update Checking
    
    /**
     * Check if we should perform automatic PDF update check
     */
    private fun shouldCheckForUpdates(): Boolean {
        val lastCheck = sharedPreferences.getLong(LAST_UPDATE_CHECK_KEY, 0L)
        if (lastCheck == 0L) return true // Never checked before
        
        val lastCheckDate = Date(lastCheck)
        val oneDayAgo = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        return lastCheckDate.before(oneDayAgo)
    }
    
    /**
     * Record that we performed an update check
     */
    private fun recordUpdateCheck() {
        sharedPreferences.edit()
            .putLong(LAST_UPDATE_CHECK_KEY, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Check all regions for PDF updates and download if needed
     */
    suspend fun checkForUpdatesIfNeeded() {
        if (!shouldCheckForUpdates()) {
            DebugConfig.debugPrint("📅 PDFCacheService: Skipping update check - already checked today")
            return
        }
        
        DebugConfig.debugPrint("🔍 PDFCacheService: Checking for PDF updates...")
        recordUpdateCheck()
        
        val allRegions = listOf(
            Region.SEGOVIA_CAPITAL,
            Region.CUELLAR, 
            Region.EL_ESPINAR,
            Region.SEGOVIA_RURAL
        )
        
        for (region in allRegions) {
            checkAndUpdateIfNeeded(region)
        }
        
        DebugConfig.debugPrint("✅ PDFCacheService: Update check completed")
    }
    
    /**
     * Check a specific region and update if needed
     */
    private suspend fun checkAndUpdateIfNeeded(region: Region, debugMode: Boolean = false) {
        val isCacheValid = isCacheUpToDate(for region, debugMode)
        
        if (!isCacheValid) {
            val result = downloadAndCache(region)
            if (result.isSuccess) {
                DebugConfig.debugPrint("📥 PDFCacheService: Updated PDF for ${region.name}")
            } else {
                DebugConfig.debugPrint("❌ PDFCacheService: Failed to update PDF for ${region.name}: ${result.exceptionOrNull()?.message}")
            }
        } else {
            DebugConfig.debugPrint("✅ PDFCacheService: PDF for ${region.name} is up to date")
        }
    }
    
    /**
     * Force check for updates (ignores daily limit)
     */
    suspend fun forceCheckForUpdates() {
        DebugConfig.debugPrint("🔄 PDFCacheService: Force checking for PDF updates...")
        recordUpdateCheck() // Update the timestamp
        
        val allRegions = listOf(
            Region.SEGOVIA_CAPITAL,
            Region.CUELLAR,
            Region.EL_ESPINAR,
            Region.SEGOVIA_RURAL
        )
        
        for (region in allRegions) {
            checkAndUpdateIfNeeded(region, debugMode = true)
        }
        
        DebugConfig.debugPrint("✅ PDFCacheService: Force update check completed")
    }
    
    /**
     * Force check for updates with progress callbacks for UI
     */
    fun forceCheckForUpdatesWithProgress(): Flow<Pair<Region, UpdateProgressState>> = flow {
        DebugConfig.debugPrint("🔄 PDFCacheService: Force checking for PDF updates with progress...")
        recordUpdateCheck()
        
        val allRegions = listOf(
            Region.SEGOVIA_CAPITAL,
            Region.CUELLAR,
            Region.EL_ESPINAR,
            Region.SEGOVIA_RURAL
        )
        
        for (region in allRegions) {
            emit(region to UpdateProgressState.Checking)
            
            val isCacheValid = isCacheUpToDate(for region, debugMode = true)
            
            if (!isCacheValid) {
                emit(region to UpdateProgressState.Downloading)
                
                val result = downloadAndCache(region)
                if (result.isSuccess) {
                    DebugConfig.debugPrint("📥 PDFCacheService: Updated PDF for ${region.name}")
                    emit(region to UpdateProgressState.Downloaded)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    DebugConfig.debugPrint("❌ PDFCacheService: Failed to update PDF for ${region.name}: $error")
                    emit(region to UpdateProgressState.Error(error))
                }
            } else {
                DebugConfig.debugPrint("✅ PDFCacheService: PDF for ${region.name} is up to date")
                emit(region to UpdateProgressState.UpToDate)
            }
        }
        
        DebugConfig.debugPrint("✅ PDFCacheService: Force update check with progress completed")
    }
    
    /**
     * Get structured cache status for all regions
     */
    suspend fun getCacheStatus(): List<RegionCacheStatus> {
        val allRegions = listOf(
            Region.SEGOVIA_CAPITAL,
            Region.CUELLAR,
            Region.EL_ESPINAR,
            Region.SEGOVIA_RURAL
        )
        
        val lastUpdateCheck = sharedPreferences.getLong(LAST_UPDATE_CHECK_KEY, 0L)
            .takeIf { it > 0L }?.let { Date(it) }
        
        return allRegions.map { region ->
            val cachedPDF = cachedPDFDao.getCachedPDF(region.name)
            val cachedFile = getCachedFile(for region)
            val isCached = cachedPDF != null && cachedFile != null && cachedFile.exists()
            val needsUpdate = if (isCached) {
                !isCacheUpToDate(for region)
            } else false
            
            RegionCacheStatus(
                region = region,
                isCached = isCached,
                downloadDate = cachedPDF?.downloadDate,
                fileSize = cachedFile?.length(),
                lastChecked = lastUpdateCheck,
                needsUpdate = needsUpdate
            )
        }
    }
    
    /**
     * Get cache info for debugging
     */
    suspend fun getCacheInfo(): String {
        val allRegions = listOf(
            Region.SEGOVIA_CAPITAL,
            Region.CUELLAR,
            Region.EL_ESPINAR,
            Region.SEGOVIA_RURAL
        )
        
        var info = "PDFCacheService Status:\n"
        info += "Cache Directory: ${cacheDirectory.absolutePath}\n\n"
        
        for (region in allRegions) {
            val cached = if (hasCachedFile(for region)) "✅" else "❌"
            info += "📄 ${region.name}: $cached\n"
            
            val cachedPDF = cachedPDFDao.getCachedPDF(region.name)
            if (cachedPDF != null) {
                val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                info += "   Downloaded: ${formatter.format(cachedPDF.downloadDate)}\n"
                info += "   Size: ${RegionCacheStatus.formatFileSize(cachedPDF.fileSize)}\n"
            } else {
                info += "   No cache info\n"
            }
            info += "\n"
        }
        
        return info
    }
    
    /**
     * Initialize the service (call on app launch)
     */
    suspend fun initialize() {
        DebugConfig.debugPrint("🚀 PDFCacheService: Initialized")
        DebugConfig.debugPrint(getCacheInfo())
    }
    
    // MARK: - Utilities
    
    /**
     * Parse HTTP date string to Date
     */
    private fun parseHttpDate(dateString: String): Date? {
        for (format in HTTP_DATE_FORMATS) {
            try {
                val formatter = SimpleDateFormat(format, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }
                return formatter.parse(dateString)
            } catch (e: Exception) {
                // Try next format
                continue
            }
        }
        return null
    }
}
