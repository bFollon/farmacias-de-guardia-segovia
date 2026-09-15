/*
 * Copyright (C) 2026  Bruno Follon (@bFollon)
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Detects whether our schedule sync server is unreachable because of LaLiga's
 * court-authorized IP blocking (used against Cloudflare-hosted piracy sites during football
 * matches, which collaterally blocks unrelated sites sharing the same IPs — including our
 * own Cloudflare-tunneled server, see [Secrets.scheduleServerBaseUrl]).
 *
 * This is deliberately separate from [NetworkMonitor]: the device can have perfectly good
 * internet while our own server is unreachable, and that combination is what this service
 * distinguishes from a generic offline state (already covered by [ScheduleSyncStatus]).
 *
 * [onServerUnreachable]/[onServerReachable] are called by
 * [PharmacyScheduleRepository]'s `updateSyncStatus` whenever a manifest fetch (our cheapest,
 * most frequent call to our own server) succeeds or fails — see
 * [ScheduleSyncService.fetchManifest].
 */
object LaLigaBlockingService {

    private const val TAG = "LaLigaBlockingService"
    private const val STATUS_URL = "https://hayahora.futbol/estado/blocked-any.txt"
    private const val MIN_RECHECK_INTERVAL_MS = 2 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var lastCheckAtMs = 0L

    @Volatile
    var isLikelyBlocked: Boolean = false
        private set

    /** Call when a request to our own server succeeds — clears any stale blocked state. */
    fun onServerReachable() {
        isLikelyBlocked = false
    }

    /**
     * Call when a request to our own server fails. If the device is online, this checks
     * hayahora.futbol's live list of blocked IPs (rate-limited to once per
     * [MIN_RECHECK_INTERVAL_MS]) to see whether a LaLiga blocking wave is the likely cause.
     */
    suspend fun onServerUnreachable(): Boolean {
        if (!NetworkMonitor.isOnline()) {
            isLikelyBlocked = false
            return false
        }
        val now = System.currentTimeMillis()
        if (now - lastCheckAtMs < MIN_RECHECK_INTERVAL_MS) return isLikelyBlocked
        lastCheckAtMs = now
        isLikelyBlocked = checkBlocking()
        return isLikelyBlocked
    }

    /**
     * Checks whether our own server's IP is among those currently blocked, rather than just
     * whether *some* blocking is active — a resolved-and-matched IP is strong evidence,
     * while an empty/unreachable blocklist is strong evidence against.
     *
     * If our own hostname can't be resolved at all, we have no way to confirm a specific
     * match, so we fall back to correlation (blocklist non-empty) as the best available signal.
     */
    private suspend fun checkBlocking(): Boolean = withContext(Dispatchers.IO) {
        val blockedIps = fetchBlockedIps() ?: return@withContext false
        if (blockedIps.isEmpty()) return@withContext false

        val resolvedIps = resolveServerIps()
        if (resolvedIps.isNullOrEmpty()) {
            DebugConfig.debugWarn("$TAG: could not resolve our own server IP, falling back to correlation")
            true
        } else {
            resolvedIps.any { it in blockedIps }
        }
    }

    private fun fetchBlockedIps(): Set<String>? = try {
        val request = Request.Builder().url(STATUS_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
                ?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
        }
    } catch (e: Exception) {
        DebugConfig.debugError("$TAG: hayahora.futbol check failed", e)
        null
    }

    private fun resolveServerIps(): Set<String>? = try {
        val host = URI(Secrets.scheduleServerBaseUrl).host
        host?.let { InetAddress.getAllByName(it).mapNotNull { addr -> addr.hostAddress }.toSet() }
    } catch (e: Exception) {
        DebugConfig.debugError("$TAG: could not resolve our own server host", e)
        null
    }
}
