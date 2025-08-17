package com.farmaciasdeGuardia.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.Date

/**
 * Room entity for caching downloaded PDF files
 * Equivalent to iOS PDFCacheManager's UserDefaults storage
 */
@Entity(tableName = "cached_pdfs")
@Serializable
data class CachedPDF(
    @PrimaryKey
    val regionName: String,
    val localFilePath: String,        // Path to the PDF file in internal storage
    val remoteUrl: String,
    val lastModified: Date?,          // Server's last modified date
    val contentLength: Long?,         // Content length from server
    val etag: String?,                // ETag from server for cache validation
    val downloadDate: Date,           // When we downloaded it
    val fileSize: Long               // Actual local file size
) {
    companion object {
        fun create(
            regionName: String,
            localFilePath: String,
            remoteUrl: String,
            lastModified: Date? = null,
            contentLength: Long? = null,
            etag: String? = null,
            fileSize: Long = 0L
        ): CachedPDF {
            return CachedPDF(
                regionName = regionName,
                localFilePath = localFilePath,
                remoteUrl = remoteUrl,
                lastModified = lastModified,
                contentLength = contentLength,
                etag = etag,
                downloadDate = Date(),
                fileSize = fileSize
            )
        }
    }
}
