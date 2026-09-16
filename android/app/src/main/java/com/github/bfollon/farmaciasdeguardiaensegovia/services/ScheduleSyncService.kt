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
import android.content.SharedPreferences
import com.github.bfollon.farmaciasdeguardiaensegovia.data.LocationManifestEntry
import com.github.bfollon.farmaciasdeguardiaensegovia.data.LocationSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the schedule sync server (see server/src/routes/{locations,schedules}.ts).
 * Knows how to fetch the manifest and per-location schedules and how to remember ETags -
 * it never touches disk itself. ScheduleCacheService owns persistence; the caller (eventually
 * PharmacyScheduleRepository) is responsible for calling [syncAll] and persisting whatever it
 * hands back via `onUpdated`.
 */
class ScheduleSyncService private constructor(context: Context) {

    sealed class SyncError : Exception() {
        object Unauthorized : SyncError()
        object NotFound : SyncError()
        data class ServerError(val code: Int) : SyncError()
        data class Network(val underlying: Exception) : SyncError()
        data class DecodeError(val underlying: Exception) : SyncError()
    }

    data class SyncSummary(
        /** True if the sync was skipped entirely because the device is offline. */
        var skippedOffline: Boolean = false,
        /** Set if the manifest fetch itself failed - no per-location sync was attempted. */
        var manifestFailure: SyncError? = null,
        val updated: MutableList<String> = mutableListOf(),
        val unchanged: MutableList<String> = mutableListOf(),
        val failed: MutableMap<String, SyncError> = mutableMapOf()
    )

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val baseUrl = Secrets.scheduleServerBaseUrl
    private val apiKey = Secrets.scheduleApiKey

    companion object {
        private const val PREFS_NAME = "schedule_sync_service"
        private const val ETAGS_KEY = "etags"
        private const val LAST_CHECKED_PREFIX = "last_checked_"

        @Volatile
        private var INSTANCE: ScheduleSyncService? = null

        fun getInstance(context: Context): ScheduleSyncService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScheduleSyncService(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    // MARK: - Manifest

    suspend fun fetchManifest(): List<LocationManifestEntry> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/api/locations")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val body = execute(request, allow304 = false).second
        try {
            json.decodeFromString(ListSerializer(LocationManifestEntry.serializer()), body)
        } catch (e: Exception) {
            throw SyncError.DecodeError(e)
        }
    }

    // MARK: - Per-location fetch

    /** Returns null when the server responds 304 (unchanged since the stored ETag). */
    suspend fun fetchLocationSchedule(locationId: String): LocationSchedule? = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/schedules/$locationId")
            .addHeader("Authorization", "Bearer $apiKey")

        storedETag(locationId)?.let { requestBuilder.addHeader("If-None-Match", it) }

        val (statusCode, body, etagHeader) = executeRaw(requestBuilder.build())

        if (statusCode == 304) {
            return@withContext null
        }

        checkStatus(statusCode)

        val schedule = try {
            json.decodeFromString(LocationSchedule.serializer(), body)
        } catch (e: Exception) {
            throw SyncError.DecodeError(e)
        }

        storeETag(locationId, etagHeader)
        schedule
    }

    // MARK: - Orchestration

    /**
     * Syncs all given locations against the server manifest.
     * - [knownVersions] should reflect what's currently valid on disk ([ScheduleCacheService]),
     *   so a location whose manifest version hasn't changed is skipped without a network round trip.
     * - [onUpdated] is called synchronously for each location that got fresh data, so the caller
     *   can persist it - this service never writes to disk itself.
     * - A per-location failure never aborts the rest of the batch, and never invokes [onUpdated],
     *   so a failing location's existing disk cache is left untouched.
     */
    suspend fun syncAll(
        locationIds: List<String>,
        knownVersions: Map<String, Int>,
        onUpdated: (String, LocationSchedule) -> Unit
    ): SyncSummary {
        val summary = SyncSummary()

        if (!NetworkMonitor.isOnline()) {
            DebugConfig.debugPrint("⚠️ ScheduleSyncService: Offline, skipping sync")
            summary.skippedOffline = true
            return summary
        }

        ScheduleSyncStatus.reportFetchingManifest(true)
        val manifest = try {
            fetchManifest()
        } catch (e: SyncError) {
            ScheduleSyncStatus.reportFetchingManifest(false)
            DebugConfig.debugError("ScheduleSyncService: Manifest fetch failed", e)
            ErrorReportingService.captureError(e, mapOf("operation" to "fetchManifest"))
            summary.manifestFailure = e
            return summary
        } catch (e: Exception) {
            ScheduleSyncStatus.reportFetchingManifest(false)
            summary.manifestFailure = SyncError.Network(e)
            return summary
        }
        ScheduleSyncStatus.reportFetchingManifest(false)

        DebugConfig.debugPrint("✅ ScheduleSyncService: Fetched manifest (${manifest.size} locations)")
        val manifestVersions = manifest.associate { it.locationId to it.version }

        for (locationId in locationIds) {
            val manifestVersion = manifestVersions[locationId]
            if (manifestVersion == null) {
                // Server doesn't publish this location (yet) - leave its disk cache untouched.
                continue
            }

            if (knownVersions[locationId] == manifestVersion) {
                summary.unchanged.add(locationId)
                storeLastChecked(locationId)
                continue
            }

            try {
                val schedule = fetchLocationSchedule(locationId)
                if (schedule != null) {
                    DebugConfig.debugPrint("✅ ScheduleSyncService: Synced $locationId (version ${schedule.version}, ${schedule.schedules.size} schedules)")
                    onUpdated(locationId, schedule)
                    summary.updated.add(locationId)
                } else {
                    DebugConfig.debugPrint("➖ ScheduleSyncService: $locationId unchanged (304)")
                    summary.unchanged.add(locationId)
                }
                storeLastChecked(locationId)
            } catch (e: SyncError) {
                DebugConfig.debugError("ScheduleSyncService: Sync failed for $locationId", e)
                ErrorReportingService.captureError(e, mapOf("locationId" to locationId, "operation" to "syncAll"))
                summary.failed[locationId] = e
            } catch (e: Exception) {
                summary.failed[locationId] = SyncError.Network(e)
            }
        }

        return summary
    }

    // MARK: - Networking helpers

    /** Returns (statusCode, body) for a request expected to be 200 (or optionally 304). */
    private fun execute(request: Request, allow304: Boolean): Pair<Int, String> {
        val (statusCode, body, _) = executeRaw(request)
        if (!allow304 || statusCode != 304) checkStatus(statusCode)
        return statusCode to body
    }

    /** Returns (statusCode, body, etagHeader) without status validation. */
    private fun executeRaw(request: Request): Triple<Int, String, String?> {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                return Triple(response.code, body, response.header("ETag"))
            }
        } catch (e: IOException) {
            throw SyncError.Network(e)
        }
    }

    private fun checkStatus(statusCode: Int) {
        when (statusCode) {
            200 -> return
            401 -> throw SyncError.Unauthorized
            404 -> throw SyncError.NotFound
            else -> throw SyncError.ServerError(statusCode)
        }
    }

    // MARK: - Per-location ETag storage

    private fun loadETags(): Map<String, String> {
        val stored = sharedPreferences.getString(ETAGS_KEY, null) ?: return emptyMap()
        return try {
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), stored)
        } catch (e: Exception) {
            DebugConfig.debugError("ScheduleSyncService: Failed to load stored ETags", e)
            emptyMap()
        }
    }

    private fun storedETag(locationId: String): String? = loadETags()[locationId]

    private fun storeETag(locationId: String, etag: String?) {
        val current = loadETags().toMutableMap()
        if (etag == null) {
            current.remove(locationId)
        } else {
            current[locationId] = etag
        }
        val encoded = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), current)
        sharedPreferences.edit().putString(ETAGS_KEY, encoded).apply()
    }

    // MARK: - Per-location "last checked" tracking

    /**
     * When a location was last successfully confirmed against the server - whether that
     * confirmation found new data or just verified the cached version is still current
     * (a manifest-version match or a 304). Distinct from the cache's own timestamp, which
     * only moves when the underlying data actually changes and can go stale for weeks at a
     * time even though the app is checking in successfully every day.
     */
    fun lastChecked(locationId: String): Long? {
        val value = sharedPreferences.getLong(LAST_CHECKED_PREFIX + locationId, 0L)
        return if (value == 0L) null else value
    }

    private fun storeLastChecked(locationId: String) {
        sharedPreferences.edit().putLong(LAST_CHECKED_PREFIX + locationId, System.currentTimeMillis()).apply()
    }
}
