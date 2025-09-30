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

import kotlinx.serialization.Serializable

/**
 * PDF version information for tracking updates
 * Stores metadata from HTTP headers to detect when PDFs change on the server
 */
@Serializable
data class PDFVersion(
    val url: String,
    val lastModified: Long? = null,  // HTTP Last-Modified header as timestamp
    val contentLength: Long? = null, // HTTP Content-Length header
    val etag: String? = null,        // HTTP ETag header
    val downloadDate: Long = System.currentTimeMillis()
)
