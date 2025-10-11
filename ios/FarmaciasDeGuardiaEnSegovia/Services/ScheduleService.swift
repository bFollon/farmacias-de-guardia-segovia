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

class ScheduleService {
    // In-memory cache by region ID (session cache)
    static private var cachedSchedules: [String: [PharmacySchedule]] = [:]
    static private let pdfService = PDFProcessingService()
    static private let cacheService = ScheduleCacheService.shared

    static func loadSchedules(for region: Region, forceRefresh: Bool = false) async -> [PharmacySchedule] {
        // Return in-memory cached schedules if available and not forcing refresh
        if let cached = cachedSchedules[region.id], !forceRefresh {
            DebugConfig.debugPrint("ScheduleService: Using in-memory cached schedules for region \(region.name)")

            // Special handling for Segovia Rural: ensure ZBS schedules are loaded
            if region == .segoviaRural && SegoviaRuralParser.getCachedZBSSchedules().isEmpty {
                // ZBS cache is empty, try to load from persistent cache
                if let zbsSchedules = cacheService.loadCachedZBSSchedules(for: region) {
                    SegoviaRuralParser.setCachedZBSSchedules(zbsSchedules)
                    DebugConfig.debugPrint("✅ ScheduleService: Loaded \(zbsSchedules.count) ZBS schedules from persistent cache")
                }
            }

            return cached
        }

        // Try to load from persistent cache if not forcing refresh
        if !forceRefresh, let persistedSchedules = cacheService.loadCachedSchedules(for: region) {
            DebugConfig.debugPrint("ScheduleService: Using persisted cached schedules for region \(region.name)")
            cachedSchedules[region.id] = persistedSchedules

            // Special handling for Segovia Rural: load ZBS schedules from cache
            if region == .segoviaRural {
                if let zbsSchedules = cacheService.loadCachedZBSSchedules(for: region) {
                    SegoviaRuralParser.setCachedZBSSchedules(zbsSchedules)
                    DebugConfig.debugPrint("✅ ScheduleService: Loaded \(zbsSchedules.count) ZBS schedules from cache")
                }
            }

            return persistedSchedules
        }

        // Load from PDF and save to both caches
        DebugConfig.debugPrint("ScheduleService: Loading schedules from PDF for region \(region.name)...")
        let schedules = await pdfService.loadPharmacies(for: region, forceRefresh: forceRefresh)

        // Save to in-memory cache
        cachedSchedules[region.id] = schedules

        // Save to persistent cache
        cacheService.saveSchedulesToCache(for: region, schedules: schedules)

        // Special handling for Segovia Rural: cache ZBS schedules
        if region == .segoviaRural {
            let zbsSchedules = SegoviaRuralParser.getCachedZBSSchedules()
            cacheService.saveZBSSchedulesToCache(for: region, zbsSchedules: zbsSchedules)
            DebugConfig.debugPrint("💾 ScheduleService: Saved \(zbsSchedules.count) ZBS schedules to cache")
        }

        DebugConfig.debugPrint("ScheduleService: Successfully cached \(schedules.count) schedules for \(region.name)")

        // Print a sample schedule for verification
        if let sampleSchedule = schedules.first {
            DebugConfig.debugPrint("\nSample schedule for \(region.name):")
            DebugConfig.debugPrint("Date: \(sampleSchedule.date)")

            DebugConfig.debugPrint("\nDay Shift Pharmacies:")
            for pharmacy in sampleSchedule.dayShiftPharmacies {
                DebugConfig.debugPrint("- \(pharmacy.name)")
                DebugConfig.debugPrint("  Address: \(pharmacy.address)")
                DebugConfig.debugPrint("  Phone: \(pharmacy.formattedPhone)")
                if let info = pharmacy.additionalInfo {
                    DebugConfig.debugPrint("  Additional Info: \(info)")
                }
            }

            DebugConfig.debugPrint("\nNight Shift Pharmacies:")
            for pharmacy in sampleSchedule.nightShiftPharmacies {
                DebugConfig.debugPrint("- \(pharmacy.name)")
                DebugConfig.debugPrint("  Address: \(pharmacy.address)")
                DebugConfig.debugPrint("  Phone: \(pharmacy.formattedPhone)")
                if let info = pharmacy.additionalInfo {
                    DebugConfig.debugPrint("  Additional Info: \(info)")
                }
            }
            DebugConfig.debugPrint("")
        }

        return schedules
    }

    // Keep backward compatibility for direct URL loading
    static func loadSchedules(from url: URL, forceRefresh: Bool = false) async -> [PharmacySchedule] {
        // For direct URL loading, treat as Segovia Capital
        return await loadSchedules(for: .segoviaCapital, forceRefresh: forceRefresh)
    }

    static func clearCache() {
        // Clear both in-memory and persistent cache
        cachedSchedules.removeAll()
        cacheService.clearAllCache()
        DebugConfig.debugPrint("🗑️ ScheduleService: Cleared all caches (in-memory + persistent)")
    }

    static func clearCache(for region: Region) {
        // Clear both in-memory and persistent cache for specific region
        cachedSchedules.removeValue(forKey: region.id)
        cacheService.clearRegionCache(for: region)

        // Clear ZBS cache if clearing Segovia Rural
        if region == .segoviaRural {
            SegoviaRuralParser.clearZBSCache()
        }

        DebugConfig.debugPrint("🗑️ ScheduleService: Cleared cache for \(region.name)")
    }

    static func findCurrentSchedule(in schedules: [PharmacySchedule]) -> (PharmacySchedule, DutyDate.ShiftType)? {
        let now = Date()
        let currentTimestamp = now.timeIntervalSince1970

        // Get the duty time info for current timestamp
        let dutyInfo = DutyDate.dutyTimeInfoForTimestamp(currentTimestamp)

        // Find the schedule for the required date (using dutyInfo.date)
        guard let schedule = schedules.first(where: { schedule in
            // Both dates should have the same day and month
            return schedule.date.day == dutyInfo.date.day &&
                   schedule.date.year == dutyInfo.date.year &&
                   DutyDate.monthToNumber(schedule.date.month) == Calendar.current.component(.month, from: now)
        }) else {
            return nil
        }

        return (schedule, dutyInfo.shiftType)
    }

    /// Region-aware version that detects shift pattern from schedule data
    static func findCurrentSchedule(in schedules: [PharmacySchedule], for region: Region) -> (PharmacySchedule, DutyTimeSpan)? {
        let now = Date()
        let calendar = Calendar.current

        // First, find a sample schedule to determine the shift pattern for this region
        guard let sampleSchedule = schedules.first else { return nil }

        // Detect shift pattern based on what shifts are available in the schedule
        let has24HourShifts = sampleSchedule.shifts[.fullDay] != nil
        let hasDayNightShifts = sampleSchedule.shifts[.capitalDay] != nil || sampleSchedule.shifts[.capitalNight] != nil

        if has24HourShifts {
            // For 24-hour regions, always use current day and full-day shift
            guard let schedule = schedules.first(where: { schedule in
                guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
                let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
                return calendar.isDate(scheduleDate, inSameDayAs: now)
            }) else {
                return nil
            }
            return (schedule, .fullDay)
        } else if hasDayNightShifts {
            // For day/night regions, use the existing logic
            if let legacyResult = findCurrentSchedule(in: schedules) {
                let timeSpan: DutyTimeSpan = legacyResult.1 == .day ? .capitalDay : .capitalNight
                return (legacyResult.0, timeSpan)
            }
            return nil
        } else {
            // Fallback: treat as 24-hour if we can't determine
            guard let schedule = schedules.first(where: { schedule in
                guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
                let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
                return calendar.isDate(scheduleDate, inSameDayAs: now)
            }) else {
                return nil
            }
            return (schedule, .fullDay)
        }
    }

    static func getCurrentDateTime() -> String {
        let today = Date()
        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "es_ES")
        dateFormatter.setLocalizedDateFormatFromTemplate("EEEE d MMMM")
        return "\(dateFormatter.string(from: today)) · Ahora"
    }

    static func findSchedule(for date: Date, in schedules: [PharmacySchedule]) -> PharmacySchedule? {
        let calendar = Calendar.current
        return schedules.first { schedule in
            guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
            let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
            return calendar.isDate(scheduleDate, inSameDayAs: date)
        }
    }
}
