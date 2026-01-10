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
    private let currentCacheVersion = 3

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

    /// Check if cached schedules exist and are still valid for a location
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

            // Check cache version first
            if metadata.cacheVersion != currentCacheVersion {
                DebugConfig.debugPrint("❌ ScheduleCacheService: Cache version mismatch for \(location.name) (expected: \(currentCacheVersion), found: \(metadata.cacheVersion))")
                return false
            }

            // Check if PDF file exists (use associated region for PDF cache)
            guard let pdfURL = PDFCacheManager.shared.cachedFileURL(for: location.associatedRegion) else {
                DebugConfig.debugPrint("📂 ScheduleCacheService: PDF file not found for \(location.name), cache invalid")
                return false
            }

            // Get PDF modification date
            let attributes = try fileManager.attributesOfItem(atPath: pdfURL.path)
            guard let pdfModificationDate = attributes[.modificationDate] as? Date else {
                return false
            }

            let pdfLastModified = pdfModificationDate.timeIntervalSince1970
            let cacheIsValid = pdfLastModified <= metadata.pdfLastModified

            if cacheIsValid {
                DebugConfig.debugPrint("✅ ScheduleCacheService: Cache valid for \(location.name) (PDF: \(Int(pdfLastModified)), Cache: \(Int(metadata.pdfLastModified)), Version: \(metadata.cacheVersion))")
            } else {
                DebugConfig.debugPrint("❌ ScheduleCacheService: Cache invalid for \(location.name) - PDF newer than cache")
            }

            return cacheIsValid

        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error checking cache validity for \(location.name): \(error)")
            return false
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

    /// Save parsed schedules to cache
    func saveSchedulesToCache(for location: DutyLocation, schedules: [PharmacySchedule]) {
        do {
            let startTime = Date()

            // Create cache data
            let cachedData = CachedSchedules(
                locationId: location.id,
                locationName: location.name,
                schedules: schedules,
                cacheTimestamp: Date().timeIntervalSince1970
            )

            // Save schedules to cache file
            let cacheFile = getCacheFile(for: location)
            let data = try encoder.encode(cachedData)
            try data.write(to: cacheFile)

            // Save metadata (use associated region for PDF cache)
            guard let pdfURL = PDFCacheManager.shared.cachedFileURL(for: location.associatedRegion) else {
                DebugConfig.debugPrint("⚠️ ScheduleCacheService: No PDF file found for \(location.name), using current timestamp")
                return
            }

            let attributes = try fileManager.attributesOfItem(atPath: pdfURL.path)
            let pdfModificationDate = (attributes[.modificationDate] as? Date) ?? Date()

            let metadata = CacheMetadata(
                locationId: location.id,
                scheduleCount: schedules.count,
                cacheTimestamp: Date().timeIntervalSince1970,
                pdfLastModified: pdfModificationDate.timeIntervalSince1970,
                cacheVersion: currentCacheVersion
            )

            let metadataFile = getMetadataFile(for: location)
            let metadataData = try encoder.encode(metadata)
            try metadataData.write(to: metadataFile)

            let saveTime = Date().timeIntervalSince(startTime) * 1000 // Convert to ms
            let cacheSize = data.count / 1024 // KB

            DebugConfig.debugPrint("💾 ScheduleCacheService: Cached \(schedules.count) schedules for \(location.name) in \(Int(saveTime))ms (\(cacheSize)KB)")

        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error saving schedules to cache for \(location.name): \(error)")
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
    let pdfLastModified: TimeInterval
    let cacheVersion: Int

    // For backward compatibility with old cache files
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        // locationId was added later, use "unknown" as fallback for old caches
        locationId = try container.decodeIfPresent(String.self, forKey: .locationId) ?? "unknown"
        scheduleCount = try container.decode(Int.self, forKey: .scheduleCount)
        cacheTimestamp = try container.decode(TimeInterval.self, forKey: .cacheTimestamp)
        pdfLastModified = try container.decode(TimeInterval.self, forKey: .pdfLastModified)
        // cacheVersion was added in version 2, default to 1 for old caches
        cacheVersion = try container.decodeIfPresent(Int.self, forKey: .cacheVersion) ?? 1
    }

    init(locationId: String, scheduleCount: Int, cacheTimestamp: TimeInterval, pdfLastModified: TimeInterval, cacheVersion: Int) {
        self.locationId = locationId
        self.scheduleCount = scheduleCount
        self.cacheTimestamp = cacheTimestamp
        self.pdfLastModified = pdfLastModified
        self.cacheVersion = cacheVersion
    }
}
