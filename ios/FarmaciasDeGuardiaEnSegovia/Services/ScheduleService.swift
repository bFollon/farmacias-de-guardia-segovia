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
    // In-memory cache by location ID (session cache)
    static private var cachedSchedules: [String: [PharmacySchedule]] = [:]
    static private let pdfService = PDFProcessingService()
    static private let cacheService = ScheduleCacheService.shared

    static func loadSchedules(for location: DutyLocation, forceRefresh: Bool = false) async -> [PharmacySchedule] {
        // Return in-memory cached schedules if available and not forcing refresh
        if let cached = cachedSchedules[location.id], !forceRefresh {
            DebugConfig.debugPrint("ScheduleService: Using in-memory cached schedules for location \(location.name)")
            return cached
        }

        // Try to load from persistent cache if not forcing refresh
        if !forceRefresh, let persistedSchedules = cacheService.loadCachedSchedules(for: location) {
            DebugConfig.debugPrint("ScheduleService: Using persisted cached schedules for location \(location.name)")
            cachedSchedules[location.id] = persistedSchedules
            return persistedSchedules
        }

        // Load from PDF - this returns ALL locations for the region
        DebugConfig.debugPrint("ScheduleService: Loading schedules from PDF for region \(location.associatedRegion.name)...")
        let schedulesByLocation = await pdfService.loadPharmacies(for: location.associatedRegion, forceRefresh: forceRefresh)

        // Cache ALL locations from the parse (important for Segovia Rural - parse once, cache all 8 ZBS)
        for (loc, schedules) in schedulesByLocation {
            cachedSchedules[loc.id] = schedules
            cacheService.saveSchedulesToCache(for: loc, schedules: schedules)
            DebugConfig.debugPrint("💾 ScheduleService: Cached \(schedules.count) schedules for \(loc.name)")
        }

        // Extract and return schedules for the requested location
        let schedules = schedulesByLocation[location] ?? []

        DebugConfig.debugPrint("ScheduleService: Successfully loaded \(schedules.count) schedules for \(location.name)")

        // Print a sample schedule for verification
        if let sampleSchedule = schedules.first {
            DebugConfig.debugPrint("\nSample schedule for \(location.name):")
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
        let location = DutyLocation.fromRegion(.segoviaCapital)
        return await loadSchedules(for: location, forceRefresh: forceRefresh)
    }

    static func clearCache() {
        // Clear both in-memory and persistent cache
        cachedSchedules.removeAll()
        cacheService.clearAllCache()
        DebugConfig.debugPrint("🗑️ ScheduleService: Cleared all caches (in-memory + persistent)")
    }

    static func clearCache(for location: DutyLocation) {
        // Clear both in-memory and persistent cache for specific location
        cachedSchedules.removeValue(forKey: location.id)
        cacheService.clearLocationCache(for: location)
        DebugConfig.debugPrint("🗑️ ScheduleService: Cleared cache for \(location.name)")
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

    // MARK: - Next Shift Feature

    /// Find schedule and timespan for a specific timestamp
    /// - Parameters:
    ///   - schedules: Array of pharmacy schedules to search
    ///   - region: Region to determine shift patterns
    ///   - timestamp: Unix timestamp to check (defaults to now)
    /// - Returns: Tuple of (schedule, timespan) or nil if not found
    static func findScheduleForTimestamp(
        in schedules: [PharmacySchedule],
        for region: Region,
        at timestamp: TimeInterval = Date().timeIntervalSince1970
    ) -> (PharmacySchedule, DutyTimeSpan)? {
        let date = Date(timeIntervalSince1970: timestamp)
        let calendar = Calendar.current

        // Determine shift pattern (same logic as existing findCurrentSchedule)
        guard let sampleSchedule = schedules.first else { return nil }
        let has24HourShifts = sampleSchedule.shifts[.fullDay] != nil
        let hasDayNightShifts = sampleSchedule.shifts[.capitalDay] != nil ||
                                 sampleSchedule.shifts[.capitalNight] != nil

        if has24HourShifts {
            // 24-hour regions: find schedule for the timestamp's date
            guard let schedule = schedules.first(where: { schedule in
                guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
                let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
                return calendar.isDate(scheduleDate, inSameDayAs: date)
            }) else { return nil }
            return (schedule, .fullDay)

        } else if hasDayNightShifts {
            // Day/night regions: use DutyDate.dutyTimeInfoForTimestamp logic
            let dutyInfo = DutyDate.dutyTimeInfoForTimestamp(timestamp)

            guard let schedule = schedules.first(where: { schedule in
                return schedule.date.day == dutyInfo.date.day &&
                       schedule.date.year == dutyInfo.date.year &&
                       DutyDate.monthToNumber(schedule.date.month) ==
                       calendar.component(.month, from: date)
            }) else { return nil }

            let timeSpan: DutyTimeSpan = dutyInfo.shiftType == .day ? .capitalDay : .capitalNight
            return (schedule, timeSpan)

        } else {
            // Fallback: treat as 24-hour
            guard let schedule = schedules.first(where: { schedule in
                guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
                let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
                return calendar.isDate(scheduleDate, inSameDayAs: date)
            }) else { return nil }
            return (schedule, .fullDay)
        }
    }

    /// Find the next shift after the current one
    /// - Parameters:
    ///   - schedules: Array of pharmacy schedules
    ///   - region: Region to determine shift patterns
    ///   - currentSchedule: Current schedule (optional)
    ///   - currentTimeSpan: Current shift timespan (optional)
    /// - Returns: Tuple of (schedule, timespan) for next shift, or nil
    static func findNextSchedule(
        in schedules: [PharmacySchedule],
        for region: Region,
        currentSchedule: PharmacySchedule? = nil,
        currentTimeSpan: DutyTimeSpan? = nil
    ) -> (PharmacySchedule, DutyTimeSpan)? {
        // If no current info provided, find it first
        let current: (PharmacySchedule, DutyTimeSpan)?
        if let schedule = currentSchedule, let timeSpan = currentTimeSpan {
            current = (schedule, timeSpan)
        } else {
            current = findCurrentSchedule(in: schedules, for: region)
        }

        guard let (schedule, timeSpan) = current else { return nil }

        // Calculate end time of current shift
        let calendar = Calendar.current
        guard let scheduleTimestamp = schedule.date.toTimestamp() else { return nil }
        let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)

        var shiftEndComponents = calendar.dateComponents([.year, .month, .day], from: scheduleDate)
        let endComponents = calendar.dateComponents([.hour, .minute], from: timeSpan.end)
        shiftEndComponents.hour = endComponents.hour
        shiftEndComponents.minute = endComponents.minute

        // Handle midnight-crossing shifts (night shifts)
        if timeSpan.spansMultipleDays {
            shiftEndComponents.day! += 1
        }

        guard let shiftEndDate = calendar.date(from: shiftEndComponents) else { return nil }

        // Add 1 minute to get into next shift
        guard let nextShiftDate = calendar.date(byAdding: .minute, value: 1, to: shiftEndDate)
            else { return nil }

        let nextTimestamp = nextShiftDate.timeIntervalSince1970

        DebugConfig.debugPrint("📅 Finding next schedule after \(timeSpan.displayName) ending at \(shiftEndDate)")

        // Reuse findScheduleForTimestamp to find next shift
        return findScheduleForTimestamp(in: schedules, for: region, at: nextTimestamp)
    }

    /// Calculate minutes remaining until current shift ends
    /// - Parameter timeSpan: Current duty timespan
    /// - Returns: Minutes until shift end, or nil if calculation fails
    static func calculateMinutesUntilShiftEnd(for timeSpan: DutyTimeSpan) -> Int? {
        let now = Date()
        let calendar = Calendar.current
        let currentComponents = calendar.dateComponents([.hour, .minute], from: now)

        guard let currentHour = currentComponents.hour,
              let currentMinute = currentComponents.minute else { return nil }

        let currentMinutes = currentHour * 60 + currentMinute

        let endComponents = calendar.dateComponents([.hour, .minute], from: timeSpan.end)
        guard let endHour = endComponents.hour, let endMinute = endComponents.minute else {
            return nil
        }
        let endMinutes = endHour * 60 + endMinute

        if timeSpan.spansMultipleDays {
            // Night shift crossing midnight
            let startComponents = calendar.dateComponents([.hour, .minute], from: timeSpan.start)
            let startMinutes = (startComponents.hour ?? 0) * 60 + (startComponents.minute ?? 0)

            if currentMinutes >= startMinutes {
                // Currently in "today" portion (after start time)
                let minutesUntilMidnight = (24 * 60) - currentMinutes
                return minutesUntilMidnight + endMinutes
            } else {
                // Currently in "tomorrow" portion (before end time)
                return endMinutes - currentMinutes
            }
        } else {
            // Same-day shift
            return endMinutes - currentMinutes
        }
    }
}
