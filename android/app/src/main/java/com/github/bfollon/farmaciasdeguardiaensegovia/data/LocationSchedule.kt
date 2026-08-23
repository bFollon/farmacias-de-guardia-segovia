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
 * Envelope returned by `GET /api/schedules/:locationId` on the sync server.
 * Mirrors `LocationSchedule` in `server/src/types.ts`.
 */
@Serializable
data class LocationSchedule(
    val locationId: String,
    val regionId: String,
    val sourcePdfUrl: String,
    val sourcePdfSha256: String,
    val sourcePdfLastModified: String? = null,
    val sourcePdfEtag: String? = null,
    val parsedAt: String,
    val version: Int,
    val schedules: List<PharmacySchedule>
)

/** A single entry in the manifest returned by `GET /api/locations`. */
@Serializable
data class LocationManifestEntry(
    val locationId: String,
    val version: Int
)
