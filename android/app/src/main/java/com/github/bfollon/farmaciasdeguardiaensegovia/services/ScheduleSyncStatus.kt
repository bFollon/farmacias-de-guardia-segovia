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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tracks whether the most recent sync attempt actually reached the schedule server, so the UI
 * can show a "showing cached data" warning distinct from full device-offline (NetworkMonitor
 * already covers that case). This covers "device is online, but the sync server itself was
 * unreachable" - e.g. off the home LAN, since the server isn't exposed via Cloudflare Tunnel
 * yet (see Features/client-offline-sync.md / migration-plan.md).
 */
object ScheduleSyncStatus {
    var isServerUnreachable by mutableStateOf(false)
        private set

    fun reportSuccess() {
        isServerUnreachable = false
    }

    fun reportFailure() {
        isServerUnreachable = true
    }
}
