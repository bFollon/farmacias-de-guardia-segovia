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

import android.net.Uri
import androidx.core.net.toUri

/**
 * A `geo:` URI that drops a labelled pin exactly on the pharmacy, or null when the server's
 * catalog has no coordinates for it - callers then fall back to a text search for name +
 * address and let the maps app resolve it.
 */
fun Pharmacy.coordinatesGeoUri(): Uri? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return "geo:0,0?q=$lat,$lng(${Uri.encode(name.trim())})".toUri()
}
