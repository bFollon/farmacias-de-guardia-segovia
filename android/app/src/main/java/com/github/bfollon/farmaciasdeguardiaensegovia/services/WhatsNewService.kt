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

package com.github.bfollon.farmaciasdeguardiaensegovia.services

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.edit
import com.github.bfollon.farmaciasdeguardiaensegovia.BuildConfig

data class WhatsNewEntry(
    val icon: ImageVector,
    val title: String,
    val body: String,
    /** The version this entry shipped in, e.g. "1.9.0" — see `versionName` in app/build.gradle.kts. */
    val version: String,
)

/**
 * Version-*range*-gated "what's new" notice, shown once per app update.
 *
 * This keys off "has this app version been seen before", so it surfaces on first launch after
 * an update, before the user has to go looking for whatever changed.
 *
 * `entries` is append-only: each release adds new version-tagged entries, older ones are never
 * removed. A user who skips versions sees everything they missed ([entriesToShow]), not just
 * whatever shipped in the version they happen to update to — capped at [MAX_ENTRIES_TO_SHOW] so
 * someone who hasn't updated in a very long time doesn't get a wall of old announcements.
 */
object WhatsNewService {
    private const val PREFS_NAME = "whats_new_prefs"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

    /** Upper bound on how many past entries to show at once, oldest-missed dropped first. */
    private const val MAX_ENTRIES_TO_SHOW = 5

    /**
     * All announcements ever shipped, oldest first. Append new ones here on every release that
     * warrants an announcement — never remove or overwrite past entries.
     */
    val entries: List<WhatsNewEntry> = listOf(
        WhatsNewEntry(
            icon = Icons.Filled.Sync,
            title = "Información más fiable",
            body = "Los horarios de guardia ahora se procesan una sola vez en nuestro servidor y se sincronizan con todos los dispositivos, en lugar de que cada móvil interprete el PDF oficial por su cuenta. Así todo el mundo ve siempre los mismos datos, sin errores de lectura puntuales. Como siempre, el servicio sigue y seguirá siendo gratuito y sin anuncios. Gracias por usarlo.",
            version = "2.0.0",
        ),
    )

    /**
     * Entries the user hasn't seen yet: `version > lastSeenVersion` and `version <= currentVersion`,
     * sorted ascending and capped to the most recent [MAX_ENTRIES_TO_SHOW].
     */
    fun entriesToShow(context: Context): List<WhatsNewEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSeen = prefs.getString(KEY_LAST_SEEN_VERSION, null) ?: "0.0.0"
        val currentVersion = BuildConfig.VERSION_NAME
        val inRange = entries.filter {
            compareSemVer(it.version, lastSeen) > 0 && compareSemVer(it.version, currentVersion) <= 0
        }
        return inRange.sortedWith { a, b -> compareSemVer(a.version, b.version) }
            .takeLast(MAX_ENTRIES_TO_SHOW)
    }

    /**
     * True if there's at least one unseen entry to announce on this device.
     *
     * No last-seen version recorded means one of two things: a genuinely fresh install (in
     * which case there's nothing to announce — the user never saw the old behavior), or an
     * existing install upgrading into the very first version that ships this mechanism (in
     * which case they *should* see whatever they missed). [MonitoringPreferencesService.hasUserMadeAnalyticsChoice]
     * distinguishes the two: that choice is only ever made once, on a screen every install has
     * gone through since long before this feature existed, so its presence means the app has
     * run on this device before.
     */
    fun shouldShow(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_SEEN_VERSION, null) == null) {
            val isExistingInstall = MonitoringPreferencesService.hasUserMadeAnalyticsChoice()
            if (!isExistingInstall) {
                markAsSeen(context)
                return false
            }
        }
        return entriesToShow(context).isNotEmpty()
    }

    fun markAsSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST_SEEN_VERSION, BuildConfig.VERSION_NAME)
        }
    }
}

/**
 * Compares two "MAJOR.MINOR.PATCH" SemVer strings numerically, not lexicographically
 * (`"1.10.0" > "1.9.0"`, which plain string comparison would get wrong). Missing or
 * non-numeric components are treated as 0.
 */
private fun compareSemVer(lhs: String, rhs: String): Int {
    val (lMajor, lMinor, lPatch) = semVerComponents(lhs)
    val (rMajor, rMinor, rPatch) = semVerComponents(rhs)
    if (lMajor != rMajor) return lMajor.compareTo(rMajor)
    if (lMinor != rMinor) return lMinor.compareTo(rMinor)
    return lPatch.compareTo(rPatch)
}

private fun semVerComponents(version: String): Triple<Int, Int, Int> {
    val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
    return Triple(parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 })
}
