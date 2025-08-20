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
