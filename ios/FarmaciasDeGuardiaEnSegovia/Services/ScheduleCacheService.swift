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

    /// Check if cached schedules exist and are still valid for a region
    func isCacheValid(for region: Region) -> Bool {
        let cacheFile = getCacheFile(for: region)
        let metadataFile = getMetadataFile(for: region)

        guard fileManager.fileExists(atPath: cacheFile.path),
              fileManager.fileExists(atPath: metadataFile.path) else {
            return false
        }

        do {
            let metadataData = try Data(contentsOf: metadataFile)
            let metadata = try decoder.decode(CacheMetadata.self, from: metadataData)

            // Check if PDF file exists
            guard let pdfURL = PDFCacheManager.shared.cachedFileURL(for: region) else {
                DebugConfig.debugPrint("📂 ScheduleCacheService: PDF file not found for \(region.name), cache invalid")
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
                DebugConfig.debugPrint("✅ ScheduleCacheService: Cache valid for \(region.name) (PDF: \(Int(pdfLastModified)), Cache: \(Int(metadata.pdfLastModified)))")
            } else {
                DebugConfig.debugPrint("❌ ScheduleCacheService: Cache invalid for \(region.name) - PDF newer than cache")
            }

            return cacheIsValid

        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error checking cache validity for \(region.name): \(error)")
            return false
        }
    }

    // MARK: - Cache Loading

    /// Load cached schedules for a region (if valid)
    func loadCachedSchedules(for region: Region) -> [PharmacySchedule]? {
        guard isCacheValid(for: region) else {
            return nil
        }

        let cacheFile = getCacheFile(for: region)

        do {
            let startTime = Date()
            let data = try Data(contentsOf: cacheFile)
            let cachedData = try decoder.decode(CachedSchedules.self, from: data)
            let loadTime = Date().timeIntervalSince(startTime) * 1000 // Convert to ms

            DebugConfig.debugPrint("⚡ ScheduleCacheService: Loaded \(cachedData.schedules.count) cached schedules for \(region.name) in \(Int(loadTime))ms")
            return cachedData.schedules

        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error loading cached schedules for \(region.name): \(error)")
            // If cache is corrupted, delete it
            deleteCacheFiles(for: region)
            return nil
        }
    }

    // MARK: - Cache Saving

    /// Save parsed schedules to cache
    func saveSchedulesToCache(for region: Region, schedules: [PharmacySchedule]) {
        do {
            let startTime = Date()

            // Create cache data
            let cachedData = CachedSchedules(
                regionId: region.id,
                regionName: region.name,
                schedules: schedules,
                cacheTimestamp: Date().timeIntervalSince1970
            )

            // Save schedules to cache file
            let cacheFile = getCacheFile(for: region)
            let data = try encoder.encode(cachedData)
            try data.write(to: cacheFile)

            // Save metadata
            guard let pdfURL = PDFCacheManager.shared.cachedFileURL(for: region) else {
                DebugConfig.debugPrint("⚠️ ScheduleCacheService: No PDF file found for \(region.name), using current timestamp")
                return
            }

            let attributes = try fileManager.attributesOfItem(atPath: pdfURL.path)
            let pdfModificationDate = (attributes[.modificationDate] as? Date) ?? Date()

            let metadata = CacheMetadata(
                regionId: region.id,
                scheduleCount: schedules.count,
                cacheTimestamp: Date().timeIntervalSince1970,
                pdfLastModified: pdfModificationDate.timeIntervalSince1970
            )

            let metadataFile = getMetadataFile(for: region)
            let metadataData = try encoder.encode(metadata)
            try metadataData.write(to: metadataFile)

            let saveTime = Date().timeIntervalSince(startTime) * 1000 // Convert to ms
            let cacheSize = data.count / 1024 // KB

            DebugConfig.debugPrint("💾 ScheduleCacheService: Cached \(schedules.count) schedules for \(region.name) in \(Int(saveTime))ms (\(cacheSize)KB)")

        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error saving schedules to cache for \(region.name): \(error)")
        }
    }

    // MARK: - Cache Management

    /// Clear cache for a specific region
    func clearRegionCache(for region: Region) {
        deleteCacheFiles(for: region)
        DebugConfig.debugPrint("🗑️ ScheduleCacheService: Cleared cache for \(region.name)")
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

            // Per-region stats
            for cacheFile in cacheFiles {
                let regionId = cacheFile.deletingPathExtension().lastPathComponent
                do {
                    let data = try Data(contentsOf: cacheFile)
                    let cachedData = try decoder.decode(CachedSchedules.self, from: data)
                    stats["\(regionId)_scheduleCount"] = cachedData.schedules.count
                    stats["\(regionId)_cacheSize"] = data.count
                    stats["\(regionId)_cacheAge"] = Date().timeIntervalSince1970 - cachedData.cacheTimestamp
                } catch {
                    stats["\(regionId)_error"] = error.localizedDescription
                }
            }

        } catch {
            stats["error"] = error.localizedDescription
        }

        return stats
    }

    // MARK: - Private Helper Methods

    private func getCacheFile(for region: Region) -> URL {
        return cacheDirectory.appendingPathComponent("\(region.id).json")
    }

    private func getMetadataFile(for region: Region) -> URL {
        return cacheDirectory.appendingPathComponent("\(region.id).meta.json")
    }

    private func getZBSCacheFile(for region: Region) -> URL {
        return cacheDirectory.appendingPathComponent("\(region.id).zbs.json")
    }

    private func deleteCacheFiles(for region: Region) {
        do {
            try fileManager.removeItem(at: getCacheFile(for: region))
        } catch {
            // Ignore errors if files don't exist
        }
        do {
            try fileManager.removeItem(at: getMetadataFile(for: region))
        } catch {
            // Ignore errors if files don't exist
        }
        do {
            try fileManager.removeItem(at: getZBSCacheFile(for: region))
        } catch {
            // Ignore errors if files don't exist
        }
    }

    // MARK: - ZBS Schedule Caching (for Segovia Rural)

    /// Save ZBS schedules to cache (for Segovia Rural)
    func saveZBSSchedulesToCache(for region: Region, zbsSchedules: [ZBSSchedule]) {
        guard region == .segoviaRural else {
            return
        }

        do {
            let zbsFile = getZBSCacheFile(for: region)
            let data = try encoder.encode(zbsSchedules)
            try data.write(to: zbsFile)

            DebugConfig.debugPrint("💾 ScheduleCacheService: Cached \(zbsSchedules.count) ZBS schedules for \(region.name)")
        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error saving ZBS schedules to cache: \(error)")
        }
    }

    /// Load ZBS schedules from cache (for Segovia Rural)
    func loadCachedZBSSchedules(for region: Region) -> [ZBSSchedule]? {
        guard region == .segoviaRural else {
            return nil
        }

        let zbsFile = getZBSCacheFile(for: region)

        guard fileManager.fileExists(atPath: zbsFile.path) else {
            return nil
        }

        do {
            let data = try Data(contentsOf: zbsFile)
            let zbsSchedules = try decoder.decode([ZBSSchedule].self, from: data)

            DebugConfig.debugPrint("⚡ ScheduleCacheService: Loaded \(zbsSchedules.count) ZBS schedules from cache")
            return zbsSchedules
        } catch {
            DebugConfig.debugPrint("❌ ScheduleCacheService: Error loading ZBS schedules from cache: \(error)")
            return nil
        }
    }
}

// MARK: - Data Structures

private struct CachedSchedules: Codable {
    let regionId: String
    let regionName: String
    let schedules: [PharmacySchedule]
    let cacheTimestamp: TimeInterval
}

private struct CacheMetadata: Codable {
    let regionId: String
    let scheduleCount: Int
    let cacheTimestamp: TimeInterval
    let pdfLastModified: TimeInterval
}
