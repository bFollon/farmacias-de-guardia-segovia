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

/// High-performance cache service for parsed pharmacy schedules
/// Dramatically reduces app startup time by avoiding PDF re-parsing
/// Equivalent to Android ScheduleCacheService.kt
class ScheduleCacheService {
    static let shared = ScheduleCacheService()

    /// Current cache format version. Increment when cache structure changes.
    /// Version 2: Removed schedule info from Pharmacy.additionalInfo for ZBS regions
    /// Version 4: Switched from PDF-modification-date validity to server-version validity
    /// (client-offline-sync migration) - old caches don't decode into the new Pharmacy/
    /// PharmacySchedule shapes anyway, so the version bump forces them to be discarded.
    private let currentCacheVersion = 4

    private let fileManager = FileManager.default
    private let cacheDirectory: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private init() {
        // Create cache directory in app's documents directory
        let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
        cacheDirectory = documentsURL.appendingPathComponent("ScheduleCache", isDirectory: true)

        // Ensure cache directory exists
        if !fileManager.fileExists(atPath: cacheDirectory.path) {
            try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
            DebugConfig.debugPrint("📂 ScheduleCacheService: Created cache directory: \(cacheDirectory.path)")
        }
    }

    // MARK: - Cache Validation

    /// Check if cached schedules exist and are in the current cache format.
    /// Staleness relative to the server is handled separately, by ScheduleSyncService's
    /// manifest-version pre-check before this cache is ever read - this only guards against
    /// a format mismatch (e.g. a pre-migration cache that won't decode into current models).
    func isCacheValid(for location: DutyLocation) -> Bool {
        let cacheFile = getCacheFile(for: location)
        let metadataFile = getMetadataFile(for: location)

        guard fileManager.fileExists(atPath: cacheFile.path),
              fileManager.fileExists(atPath: metadataFile.path) else {
            return false
        }

        do {
            let metadataData = try Data(contentsOf: metadataFile)
            let metadata = try decoder.decode(CacheMetadata.self, from: metadataData)

            if metadata.cacheVersion != currentCacheVersion {
                DebugConfig.debugPrint("❌ ScheduleCacheService: Cache version mismatch for \(location.name) (expected: \(currentCacheVersion), found: \(metadata.cacheVersion))")
                return false
            }

            return true
        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error checking cache validity for \(location.name): \(error)")
            return false
        }
    }

    /// The server `version` currently on disk for a location, or `nil` if there's no valid
    /// cache. Used by ScheduleSyncService to decide whether a location needs re-fetching,
    /// without it needing to know anything about the on-disk cache format.
    func cachedServerVersion(for location: DutyLocation) -> Int? {
        guard isCacheValid(for: location) else { return nil }
        let metadataFile = getMetadataFile(for: location)
        do {
            let metadataData = try Data(contentsOf: metadataFile)
            let metadata = try decoder.decode(CacheMetadata.self, from: metadataData)
            return metadata.serverVersion
        } catch {
            return nil
        }
    }

    // MARK: - Cache Loading

    /// Load cached schedules for a location (if valid)
    func loadCachedSchedules(for location: DutyLocation) -> [PharmacySchedule]? {
        guard isCacheValid(for: location) else {
            return nil
        }

        let cacheFile = getCacheFile(for: location)

        do {
            let startTime = Date()
            let data = try Data(contentsOf: cacheFile)
            let cachedData = try decoder.decode(CachedSchedules.self, from: data)
            let loadTime = Date().timeIntervalSince(startTime) * 1000 // Convert to ms

            DebugConfig.debugPrint("⚡ ScheduleCacheService: Loaded \(cachedData.schedules.count) cached schedules for \(location.name) in \(Int(loadTime))ms")
            return cachedData.schedules

        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error loading cached schedules for \(location.name): \(error)")
            ErrorReportingService.shared.captureError(error, context: ["location": location.name, "operation": "loadCachedSchedules"])
            // If cache is corrupted, delete it
            deleteCacheFiles(for: location)
            return nil
        }
    }

    /// Get the cache timestamp for a location (when it was last saved)
    func getCacheTimestamp(for location: DutyLocation) -> TimeInterval? {
        let cacheFile = getCacheFile(for: location)

        guard fileManager.fileExists(atPath: cacheFile.path) else {
            return nil
        }

        do {
            let data = try Data(contentsOf: cacheFile)
            let cachedData = try decoder.decode(CachedSchedules.self, from: data)
            return cachedData.cacheTimestamp
        } catch {
            return nil
        }
    }

    // MARK: - Cache Saving

    /// Save synced schedules to cache, tagged with the server `version` they came from.
    func saveSchedulesToCache(for location: DutyLocation, schedules: [PharmacySchedule], version: Int) {
        do {
            let startTime = Date()

            let cachedData = CachedSchedules(
                locationId: location.id,
                locationName: location.name,
                schedules: schedules,
                cacheTimestamp: Date().timeIntervalSince1970
            )

            let cacheFile = getCacheFile(for: location)
            let data = try encoder.encode(cachedData)
            try data.write(to: cacheFile)

            let metadata = CacheMetadata(
                locationId: location.id,
                scheduleCount: schedules.count,
                cacheTimestamp: Date().timeIntervalSince1970,
                serverVersion: version,
                cacheVersion: currentCacheVersion
            )

            let metadataFile = getMetadataFile(for: location)
            let metadataData = try encoder.encode(metadata)
            try metadataData.write(to: metadataFile)

            let saveTime = Date().timeIntervalSince(startTime) * 1000 // Convert to ms
            let cacheSize = data.count / 1024 // KB

            DebugConfig.debugPrint("💾 ScheduleCacheService: Cached \(schedules.count) schedules for \(location.name) (version \(version)) in \(Int(saveTime))ms (\(cacheSize)KB)")

        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error saving schedules to cache for \(location.name): \(error)")
            ErrorReportingService.shared.captureError(error, context: ["location": location.name, "operation": "saveSchedulesToCache"])
        }
    }

    // MARK: - Cache Management

    /// Clear cache for a specific location
    func clearLocationCache(for location: DutyLocation) {
        deleteCacheFiles(for: location)
        DebugConfig.debugPrint("🗑️ ScheduleCacheService: Cleared cache for \(location.name)")
    }

    /// Clear all cached schedules
    func clearAllCache() {
        do {
            let files = try fileManager.contentsOfDirectory(at: cacheDirectory, includingPropertiesForKeys: nil)
            for file in files {
                try fileManager.removeItem(at: file)
            }
            DebugConfig.debugPrint("🗑️ ScheduleCacheService: Cleared all schedule caches")
        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error clearing all caches: \(error)")
            ErrorReportingService.shared.captureError(error, context: ["operation": "clearAllScheduleCache"])
        }
    }

    /// Get cache statistics for debugging
    func getCacheStats() -> [String: Any] {
        var stats: [String: Any] = [:]

        do {
            let files = try fileManager.contentsOfDirectory(at: cacheDirectory, includingPropertiesForKeys: [.fileSizeKey])
            let cacheFiles = files.filter { $0.pathExtension == "json" && !$0.lastPathComponent.contains(".meta") }
            let metadataFiles = files.filter { $0.lastPathComponent.contains(".meta.json") }

            stats["cacheDirectory"] = cacheDirectory.path
            stats["cacheFileCount"] = cacheFiles.count
            stats["metadataFileCount"] = metadataFiles.count
            stats["totalCacheSize"] = files.reduce(Int64(0)) { sum, fileURL in
                let attributes = try? fileManager.attributesOfItem(atPath: fileURL.path)
                let size = attributes?[.size] as? Int64 ?? 0
                return sum + size
            }

            // Per-location stats
            for cacheFile in cacheFiles {
                let locationId = cacheFile.deletingPathExtension().lastPathComponent
                do {
                    let data = try Data(contentsOf: cacheFile)
                    let cachedData = try decoder.decode(CachedSchedules.self, from: data)
                    stats["\(locationId)_scheduleCount"] = cachedData.schedules.count
                    stats["\(locationId)_cacheSize"] = data.count
                    stats["\(locationId)_cacheAge"] = Date().timeIntervalSince1970 - cachedData.cacheTimestamp
                } catch {
                    stats["\(locationId)_error"] = error.localizedDescription
                }
            }

        } catch {
            stats["error"] = error.localizedDescription
        }

        return stats
    }

    // MARK: - Bundled Day-Zero Data

    /// Loads the app-bundled schedule JSON for a location - the pre-first-sync fallback,
    /// shipped in the app bundle and never touched again after install. Disk cache (populated
    /// by a successful sync) always takes precedence over this; it's only consulted when the
    /// disk cache is empty/invalid. See Features/client-offline-sync.md.
    func loadBundledSchedules(for location: DutyLocation) -> LocationSchedule? {
        // Note: the BundledSchedules/ folder is a synchronized Xcode group, which flattens
        // its contents into the app bundle root at build time - there's no subdirectory to
        // look under at runtime, despite the source-tree layout suggesting otherwise.
        guard let url = Bundle.main.url(forResource: location.id, withExtension: "json") else {
            DebugConfig.debugPrint("📦 ScheduleCacheService: No bundled schedule found for \(location.name)")
            return nil
        }

        do {
            let data = try Data(contentsOf: url)
            let locationSchedule = try decoder.decode(LocationSchedule.self, from: data)
            DebugConfig.debugPrint("📦 ScheduleCacheService: Loaded bundled schedule for \(location.name) (\(locationSchedule.schedules.count) schedules)")
            return locationSchedule
        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error decoding bundled schedule for \(location.name): \(error)")
            ErrorReportingService.shared.captureError(error, context: ["location": location.name, "operation": "loadBundledSchedules"])
            return nil
        }
    }

    // MARK: - Private Helper Methods

    private func getCacheFile(for location: DutyLocation) -> URL {
        return cacheDirectory.appendingPathComponent("\(location.id).json")
    }

    private func getMetadataFile(for location: DutyLocation) -> URL {
        return cacheDirectory.appendingPathComponent("\(location.id).meta.json")
    }

    private func deleteCacheFiles(for location: DutyLocation) {
        do {
            try fileManager.removeItem(at: getCacheFile(for: location))
        } catch {
            // Ignore errors if files don't exist
        }
        do {
            try fileManager.removeItem(at: getMetadataFile(for: location))
        } catch {
            // Ignore errors if files don't exist
        }
    }
}

// MARK: - Data Structures

private struct CachedSchedules: Codable {
    let locationId: String
    let locationName: String
    let schedules: [PharmacySchedule]
    let cacheTimestamp: TimeInterval
}

private struct CacheMetadata: Codable {
    let locationId: String
    let scheduleCount: Int
    let cacheTimestamp: TimeInterval
    let serverVersion: Int
    let cacheVersion: Int
}
