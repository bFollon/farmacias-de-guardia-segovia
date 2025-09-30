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

package com.github.bfollon.farmaciasdeguardiaensegovia.data

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cache status information for a specific region
 * Used to display cache state in the UI
 */
data class RegionCacheStatus(
    val region: Region,
    val isCached: Boolean,
    val downloadDate: Long? = null,
    val fileSize: Long? = null,
    val lastChecked: Long? = null,
    val needsUpdate: Boolean = false
) {
    /**
     * Formatted file size string (e.g., "524 KB", "1.5 MB")
     */
    val formattedFileSize: String
        get() {
            if (fileSize == null) return "Desconocido"
            
            return when {
                fileSize < 1024 -> "$fileSize B"
                fileSize < 1024 * 1024 -> {
                    val kb = fileSize / 1024
                    "$kb KB"
                }
                else -> {
                    val mb = fileSize.toFloat() / (1024 * 1024)
                    String.format(Locale.getDefault(), "%.1f MB", mb)
                }
            }
        }
    
    /**
     * Formatted download date string (e.g., "16/8/25, 0:17")
     */
    val formattedDownloadDate: String
        get() {
            if (downloadDate == null) return "Nunca"
            
            val formatter = SimpleDateFormat("d/M/yy, H:mm", Locale.getDefault())
            return formatter.format(Date(downloadDate))
        }
    
    /**
     * Formatted last checked date string
     */
    val formattedLastChecked: String
        get() {
            if (lastChecked == null) return "Nunca"
            
            val formatter = SimpleDateFormat("d/M/yy, H:mm", Locale.getDefault())
            return formatter.format(Date(lastChecked))
        }
    
    /**
     * Status icon name based on cache state
     */
    val statusIcon: String
        get() = when {
            !isCached -> "cancel" // Red X icon
            needsUpdate -> "sync" // Orange sync icon
            else -> "check_circle" // Green check icon
        }
    
    /**
     * Status color based on cache state
     */
    val statusColor: Color
        get() = when {
            !isCached -> Color(0xFFD32F2F) // Red
            needsUpdate -> Color(0xFFFFA726) // Orange
            else -> Color(0xFF66BB6A) // Green
        }
    
    /**
     * Status text based on cache state
     */
    val statusText: String
        get() = when {
            !isCached -> "No Descargado"
            needsUpdate -> "Actualización Disponible"
            else -> "Actualizado"
        }
}
