package com.farmaciasdeGuardia.database.dao

import androidx.room.*
import com.farmaciasdeGuardia.database.entities.CachedPDF
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for PDF cache operations
 * Equivalent to iOS PDFCacheManager's UserDefaults operations
 */
@Dao
interface CachedPDFDao {
    
    /**
     * Get cached PDF info for a specific region
     */
    @Query("SELECT * FROM cached_pdfs WHERE regionName = :regionName")
    suspend fun getCachedPDF(regionName: String): CachedPDF?
    
    /**
     * Get all cached PDFs
     */
    @Query("SELECT * FROM cached_pdfs")
    fun getAllCachedPDFs(): Flow<List<CachedPDF>>
    
    /**
     * Get all cached PDFs as a list (for one-time queries)
     */
    @Query("SELECT * FROM cached_pdfs")
    suspend fun getAllCachedPDFsList(): List<CachedPDF>
    
    /**
     * Insert or update a cached PDF entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCachedPDF(cachedPDF: CachedPDF)
    
    /**
     * Delete a specific cached PDF entry
     */
    @Delete
    suspend fun deleteCachedPDF(cachedPDF: CachedPDF)
    
    /**
     * Delete cached PDF by region name
     */
    @Query("DELETE FROM cached_pdfs WHERE regionName = :regionName")
    suspend fun deleteCachedPDFByRegion(regionName: String)
    
    /**
     * Clear all cached PDF entries
     */
    @Query("DELETE FROM cached_pdfs")
    suspend fun clearAllCachedPDFs()
    
    /**
     * Get cache statistics - count and total file size
     */
    @Query("SELECT COUNT(*) as count, SUM(fileSize) as totalSize FROM cached_pdfs")
    suspend fun getCacheStats(): CacheStats?
    
    /**
     * Check if a PDF is cached for a region
     */
    @Query("SELECT COUNT(*) > 0 FROM cached_pdfs WHERE regionName = :regionName")
    suspend fun isPDFCached(regionName: String): Boolean
    
    /**
     * Get file paths of all cached PDFs (for cleanup operations)
     */
    @Query("SELECT localFilePath FROM cached_pdfs")
    suspend fun getAllCachedFilePaths(): List<String>
}

/**
 * Data class for cache statistics
 */
data class CacheStats(
    val count: Int,
    val totalSize: Long
) {
    val totalSizeKB: Double
        get() = totalSize / 1024.0
    
    val formattedSize: String
        get() = when {
            totalSize < 1024 -> "${totalSize} B"
            totalSizeKB < 1024 -> String.format("%.1f KB", totalSizeKB)
            else -> String.format("%.1f MB", totalSizeKB / 1024.0)
        }
}
