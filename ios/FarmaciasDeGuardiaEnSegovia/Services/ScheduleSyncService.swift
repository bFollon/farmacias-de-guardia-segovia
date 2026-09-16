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

import Foundation

/// Talks to the schedule sync server (see server/src/routes/{locations,schedules}.ts).
/// Knows how to fetch the manifest and per-location schedules and how to remember ETags -
/// it never touches disk itself. ScheduleCacheService owns persistence; the caller (eventually
/// ScheduleService) is responsible for calling `syncAll` and persisting whatever it hands back
/// via `onUpdated`.
class ScheduleSyncService {
    static let shared = ScheduleSyncService()

    enum SyncError: Error {
        case unauthorized
        case notFound
        case serverError(Int)
        case network(Error)
        case decodeError(Error)
    }

    struct SyncSummary {
        /// True if the sync was skipped entirely because the device is offline.
        var skippedOffline = false
        /// Set if the manifest fetch itself failed - no per-location sync was attempted.
        var manifestFailure: SyncError?
        var updated: [String] = []
        var unchanged: [String] = []
        var failed: [String: SyncError] = [:]
    }

    private let session: URLSession
    private let baseURL: String
    private let apiKey: String
    private let userDefaults = UserDefaults.standard

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10.0
        config.timeoutIntervalForResource = 10.0
        session = URLSession(configuration: config)
        baseURL = Secrets.scheduleServerBaseURL
        apiKey = Secrets.scheduleApiKey
    }

    // MARK: - Manifest

    func fetchManifest() async throws -> [LocationManifestEntry] {
        guard let url = URL(string: "\(baseURL)/api/locations") else {
            throw SyncError.network(URLError(.badURL))
        }

        var request = URLRequest(url: url)
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")

        let (data, response) = try await performRequest(request)
        try Self.checkStatus(response)

        do {
            return try JSONDecoder().decode([LocationManifestEntry].self, from: data)
        } catch {
            throw SyncError.decodeError(error)
        }
    }

    // MARK: - Per-location fetch

    /// Returns a `nil` schedule when the server responds 304 (unchanged since the stored ETag).
    func fetchLocationSchedule(locationId: String) async throws -> LocationSchedule? {
        guard let url = URL(string: "\(baseURL)/api/schedules/\(locationId)") else {
            throw SyncError.network(URLError(.badURL))
        }

        var request = URLRequest(url: url)
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        if let etag = storedETag(for: locationId) {
            request.setValue(etag, forHTTPHeaderField: "If-None-Match")
        }

        let (data, response) = try await performRequest(request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw SyncError.network(URLError(.badServerResponse))
        }

        if httpResponse.statusCode == 304 {
            return nil
        }

        try Self.checkStatus(response)

        do {
            let schedule = try JSONDecoder().decode(LocationSchedule.self, from: data)
            storeETag(httpResponse.value(forHTTPHeaderField: "ETag"), for: locationId)
            return schedule
        } catch {
            throw SyncError.decodeError(error)
        }
    }

    // MARK: - Orchestration

    /// Syncs all given locations against the server manifest.
    /// - `knownVersions` should reflect what's currently valid on disk (`ScheduleCacheService`),
    ///   so a location whose manifest version hasn't changed is skipped without a network round trip.
    /// - `onUpdated` is called synchronously for each location that got fresh data, so the caller can
    ///   persist it - this service never writes to disk itself.
    /// - A per-location failure never aborts the rest of the batch, and never invokes `onUpdated`,
    ///   so a failing location's existing disk cache is left untouched.
    func syncAll(
        locationIds: [String],
        knownVersions: [String: Int],
        onUpdated: (String, LocationSchedule) -> Void
    ) async -> SyncSummary {
        var summary = SyncSummary()

        guard NetworkMonitor.shared.isOnline else {
            DebugConfig.debugPrint("⚠️ ScheduleSyncService: Offline, skipping sync")
            summary.skippedOffline = true
            return summary
        }

        let manifest: [LocationManifestEntry]
        ScheduleSyncStatus.shared.reportFetchingManifest(true)
        do {
            manifest = try await fetchManifest()
        } catch let error as SyncError {
            ScheduleSyncStatus.shared.reportFetchingManifest(false)
            DebugConfig.debugPrint("❌ ScheduleSyncService: Manifest fetch failed: \(error)")
            ErrorReportingService.shared.captureError(error, context: ["operation": "fetchManifest"])
            summary.manifestFailure = error
            return summary
        } catch {
            ScheduleSyncStatus.shared.reportFetchingManifest(false)
            summary.manifestFailure = .network(error)
            return summary
        }
        ScheduleSyncStatus.shared.reportFetchingManifest(false)

        DebugConfig.debugPrint("✅ ScheduleSyncService: Fetched manifest (\(manifest.count) locations)")
        let manifestVersions = Dictionary(uniqueKeysWithValues: manifest.map { ($0.locationId, $0.version) })

        for locationId in locationIds {
            guard let manifestVersion = manifestVersions[locationId] else {
                // Server doesn't publish this location (yet) - leave its disk cache untouched.
                continue
            }

            if knownVersions[locationId] == manifestVersion {
                summary.unchanged.append(locationId)
                storeLastChecked(for: locationId)
                continue
            }

            do {
                if let schedule = try await fetchLocationSchedule(locationId: locationId) {
                    DebugConfig.debugPrint("✅ ScheduleSyncService: Synced \(locationId) (version \(schedule.version), \(schedule.schedules.count) schedules)")
                    onUpdated(locationId, schedule)
                    summary.updated.append(locationId)
                } else {
                    DebugConfig.debugPrint("➖ ScheduleSyncService: \(locationId) unchanged (304)")
                    summary.unchanged.append(locationId)
                }
                storeLastChecked(for: locationId)
            } catch let error as SyncError {
                DebugConfig.debugPrint("❌ ScheduleSyncService: Sync failed for \(locationId): \(error)")
                ErrorReportingService.shared.captureError(error, context: ["locationId": locationId, "operation": "syncAll"])
                summary.failed[locationId] = error
            } catch {
                summary.failed[locationId] = .network(error)
            }
        }

        return summary
    }

    // MARK: - Networking helpers

    private func performRequest(_ request: URLRequest) async throws -> (Data, URLResponse) {
        do {
            return try await session.data(for: request)
        } catch {
            throw SyncError.network(error)
        }
    }

    private static func checkStatus(_ response: URLResponse) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw SyncError.network(URLError(.badServerResponse))
        }
        switch httpResponse.statusCode {
        case 200:
            return
        case 401:
            throw SyncError.unauthorized
        case 404:
            throw SyncError.notFound
        default:
            throw SyncError.serverError(httpResponse.statusCode)
        }
    }

    // MARK: - Per-location ETag storage

    private func etagKey(for locationId: String) -> String {
        "schedule_etag_\(locationId)"
    }

    private func storedETag(for locationId: String) -> String? {
        userDefaults.string(forKey: etagKey(for: locationId))
    }

    private func storeETag(_ etag: String?, for locationId: String) {
        guard let etag else {
            userDefaults.removeObject(forKey: etagKey(for: locationId))
            return
        }
        userDefaults.set(etag, forKey: etagKey(for: locationId))
    }

    // MARK: - Per-location "last checked" tracking

    /// When a location was last successfully confirmed against the server - whether that
    /// confirmation found new data or just verified the cached version is still current
    /// (a manifest-version match or a 304). Distinct from the cache's own timestamp, which
    /// only moves when the underlying data actually changes and can go stale for weeks at a
    /// time even though the app is checking in successfully every day.
    func lastChecked(for locationId: String) -> TimeInterval? {
        let value = userDefaults.double(forKey: lastCheckedKey(for: locationId))
        return value == 0 ? nil : value
    }

    private func lastCheckedKey(for locationId: String) -> String {
        "schedule_last_checked_\(locationId)"
    }

    private func storeLastChecked(for locationId: String) {
        userDefaults.set(Date().timeIntervalSince1970, forKey: lastCheckedKey(for: locationId))
    }
}
