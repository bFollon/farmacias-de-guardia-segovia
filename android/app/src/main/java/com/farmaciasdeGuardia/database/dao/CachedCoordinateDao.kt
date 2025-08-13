package com.farmaciasdeGuardia.database.dao

import androidx.room.*
import com.farmaciasdeGuardia.database.entities.CachedCoordinate
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for coordinate cache operations
 * Equivalent to iOS CoordinateCache's UserDefaults operations
 */
@Dao
interface CachedCoordinateDao {
    
    /**
     * Get cached coordinates for a specific address
     */
    @Query("SELECT * FROM cached_coordinates WHERE address = :address")
    suspend fun getCachedCoordinate(address: String): CachedCoordinate?
    
    /**
     * Get all cached coordinates
     */
    @Query("SELECT * FROM cached_coordinates")
    fun getAllCachedCoordinates(): Flow<List<CachedCoordinate>>
    
    /**
     * Get all cached coordinates as a list (for cleanup operations)
     */
    @Query("SELECT * FROM cached_coordinates")
    suspend fun getAllCachedCoordinatesList(): List<CachedCoordinate>
    
    /**
     * Insert or update a cached coordinate
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCachedCoordinate(cachedCoordinate: CachedCoordinate)
    
    /**
     * Delete a specific cached coordinate
     */
    @Delete
    suspend fun deleteCachedCoordinate(cachedCoordinate: CachedCoordinate)
    
    /**
     * Clear all cached coordinates
     */
    @Query("DELETE FROM cached_coordinates")
    suspend fun clearAllCachedCoordinates()
    
    /**
     * Delete expired coordinates (older than 30 days)
     * Note: We'll handle the expiry logic in the service layer since Room doesn't support
     * date calculations directly in SQLite
     */
    @Query("DELETE FROM cached_coordinates WHERE timestamp < :cutoffDate")
    suspend fun deleteCoordinatesOlderThan(cutoffDate: Long)
    
    /**
     * Get cache statistics
     */
    @Query("SELECT COUNT(*) FROM cached_coordinates")
    suspend fun getCacheCount(): Int
    
    /**
     * Check if coordinates are cached for an address
     */
    @Query("SELECT COUNT(*) > 0 FROM cached_coordinates WHERE address = :address")
    suspend fun areCoordinatesCached(address: String): Boolean
    
    /**
     * Get coordinates cached before a certain date (for cleanup)
     */
    @Query("SELECT * FROM cached_coordinates WHERE timestamp < :cutoffDate")
    suspend fun getCoordinatesOlderThan(cutoffDate: Long): List<CachedCoordinate>
    
    /**
     * Update cache version for all entries (for migration purposes)
     */
    @Query("UPDATE cached_coordinates SET cacheVersion = :newVersion")
    suspend fun updateAllCacheVersions(newVersion: Int)
    
    /**
     * Delete entries with old cache version (for migration cleanup)
     */
    @Query("DELETE FROM cached_coordinates WHERE cacheVersion < :currentVersion")
    suspend fun deleteOldCacheVersions(currentVersion: Int)
}
