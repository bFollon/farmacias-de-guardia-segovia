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
    /// Returns the current schedule even during dead hours (for displaying with "Fuera del horario" warning)
    static func findCurrentSchedule(in schedules: [PharmacySchedule], for region: Region) -> (PharmacySchedule, DutyTimeSpan)? {
        let now = Date()
        let nowTimestamp = now.timeIntervalSince1970
        let calendar = Calendar.current

        guard !schedules.isEmpty else { return nil }

        // STEP 1: Try to find an ACTIVE shift first
        // Use findScheduleForTimestamp which properly handles all shift types
        if let activeShift = findScheduleForTimestamp(in: schedules, for: region, at: nowTimestamp) {
            // Check if this shift is actually active right now
            let schedule = activeShift.0
            let timeSpan = activeShift.1

            guard let scheduleTimestamp = schedule.date.toTimestamp() else {
                // If we can't get timestamp, return what we found
                return activeShift
            }

            let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)

            // Build shift start and end times
            var shiftStartComponents = calendar.dateComponents([.year, .month, .day], from: scheduleDate)
            let startTimeComponents = calendar.dateComponents([.hour, .minute], from: timeSpan.start)
            shiftStartComponents.hour = startTimeComponents.hour
            shiftStartComponents.minute = startTimeComponents.minute

            var shiftEndComponents = calendar.dateComponents([.year, .month, .day], from: scheduleDate)
            let endTimeComponents = calendar.dateComponents([.hour, .minute], from: timeSpan.end)
            shiftEndComponents.hour = endTimeComponents.hour
            shiftEndComponents.minute = endTimeComponents.minute

            if timeSpan.spansMultipleDays {
                shiftEndComponents.day! += 1
            }

            if let shiftStart = calendar.date(from: shiftStartComponents),
               let shiftEnd = calendar.date(from: shiftEndComponents),
               now >= shiftStart && now <= shiftEnd {
                // Shift is currently active
                return activeShift
            }
        }

        // STEP 2: No active shift - return today's schedule (for dead hours display)
        // This allows showing "Fuera del horario de guardia" warning
        if let todaySchedule = schedules.first(where: { schedule in
            guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
            let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
            return calendar.isDate(scheduleDate, inSameDayAs: now)
        }), let firstShift = todaySchedule.shifts.first(where: { !$0.value.isEmpty }) {
            DebugConfig.debugPrint("⏰ Dead hours: Returning today's schedule for display with out-of-hours warning")
            return (todaySchedule, firstShift.key)
        }

        // STEP 3: Fallback - return most recent past schedule
        // This handles edge cases like early morning before any schedule exists for today
        let sortedPastSchedules = schedules
            .compactMap { schedule -> (PharmacySchedule, TimeInterval, DutyTimeSpan)? in
                guard let scheduleTimestamp = schedule.date.toTimestamp() else { return nil }
                guard let firstShift = schedule.shifts.first(where: { !$0.value.isEmpty }) else { return nil }
                return (schedule, scheduleTimestamp, firstShift.key)
            }
            .filter { $0.1 <= nowTimestamp }  // Only past schedules
            .sorted { $0.1 > $1.1 }  // Sort by date descending (most recent first)

        if let mostRecent = sortedPastSchedules.first {
            DebugConfig.debugPrint("📅 Returning most recent past schedule")
            return (mostRecent.0, mostRecent.2)
        }

        return nil
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

        guard !schedules.isEmpty else { return nil }

        // STEP 1: Try to find a shift where the timestamp falls within active hours
        // This properly handles all shift types and dead hours
        for schedule in schedules {
            guard let scheduleTimestamp = schedule.date.toTimestamp() else { continue }
            let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)

            // Check each shift in this schedule
            for (timeSpan, pharmacies) in schedule.shifts {
                // Skip empty shifts
                guard !pharmacies.isEmpty else { continue }

                // Build the shift's actual start and end times
                var shiftStartComponents = calendar.dateComponents([.year, .month, .day], from: scheduleDate)
                let startTimeComponents = calendar.dateComponents([.hour, .minute], from: timeSpan.start)
                shiftStartComponents.hour = startTimeComponents.hour
                shiftStartComponents.minute = startTimeComponents.minute

                var shiftEndComponents = calendar.dateComponents([.year, .month, .day], from: scheduleDate)
                let endTimeComponents = calendar.dateComponents([.hour, .minute], from: timeSpan.end)
                shiftEndComponents.hour = endTimeComponents.hour
                shiftEndComponents.minute = endTimeComponents.minute

                // Handle midnight-crossing shifts (e.g., night shift 22:00-10:15)
                if timeSpan.spansMultipleDays {
                    shiftEndComponents.day! += 1
                }

                guard let shiftStart = calendar.date(from: shiftStartComponents),
                      let shiftEnd = calendar.date(from: shiftEndComponents) else { continue }

                // Check if timestamp falls within this shift's active time range
                if date >= shiftStart && date <= shiftEnd {
                    DebugConfig.debugPrint("✅ Found active shift for timestamp \(timestamp): \(timeSpan.displayName)")
                    return (schedule, timeSpan)
                }
            }
        }

        // STEP 2: No active shift found - find the next future schedule
        // This handles dead hours by finding the next shift that will be active
        let sortedFutureSchedules = schedules
            .compactMap { schedule -> (PharmacySchedule, TimeInterval, DutyTimeSpan)? in
                guard let scheduleTimestamp = schedule.date.toTimestamp() else { return nil }
                // Only consider schedules on or after the timestamp's date
                guard scheduleTimestamp >= timestamp - (24 * 3600) else { return nil }  // Look within 24 hours back

                // Get first non-empty shift
                if let firstShift = schedule.shifts.first(where: { !$0.value.isEmpty }) {
                    return (schedule, scheduleTimestamp, firstShift.key)
                }
                return nil
            }
            .sorted { $0.1 < $1.1 }  // Sort by schedule date ascending

        // Find the first schedule that has a shift starting after the timestamp
        for (schedule, scheduleTimestamp, timeSpan) in sortedFutureSchedules {
            let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)

            // Build shift start time
            var shiftStartComponents = calendar.dateComponents([.year, .month, .day], from: scheduleDate)
            let startTimeComponents = calendar.dateComponents([.hour, .minute], from: timeSpan.start)
            shiftStartComponents.hour = startTimeComponents.hour
            shiftStartComponents.minute = startTimeComponents.minute

            if let shiftStart = calendar.date(from: shiftStartComponents),
               shiftStart.timeIntervalSince1970 > timestamp {
                DebugConfig.debugPrint("⏭️ Found next future schedule for timestamp \(timestamp): \(timeSpan.displayName) starting at \(shiftStart)")
                return (schedule, timeSpan)
            }
        }

        // STEP 3: Last resort - same-day matching for edge cases
        if let schedule = schedules.first(where: { schedule in
            guard let scheduleTimestamp = schedule.date.toTimestamp() else { return false }
            let scheduleDate = Date(timeIntervalSince1970: scheduleTimestamp)
            return calendar.isDate(scheduleDate, inSameDayAs: date)
        }), let firstShift = schedule.shifts.first(where: { !$0.value.isEmpty }) {
            DebugConfig.debugPrint("⚠️ Using same-day fallback for timestamp \(timestamp)")
            return (schedule, firstShift.key)
        }

        DebugConfig.debugPrint("❌ No schedule found for timestamp \(timestamp)")
        return nil
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

    /// Calculate gap in minutes between current shift end and next shift start
    /// - Parameters:
    ///   - currentSchedule: Current pharmacy schedule
    ///   - currentTimeSpan: Current shift timespan
    ///   - nextSchedule: Next pharmacy schedule
    ///   - nextTimeSpan: Next shift timespan
    /// - Returns: Gap in minutes, or nil if calculation fails
    static func calculateGapBetweenShifts(
        currentSchedule: PharmacySchedule,
        currentTimeSpan: DutyTimeSpan,
        nextSchedule: PharmacySchedule,
        nextTimeSpan: DutyTimeSpan
    ) -> Int? {
        let calendar = Calendar.current

        // Get current shift end date
        guard let currentScheduleTimestamp = currentSchedule.date.toTimestamp() else { return nil }
        let currentScheduleDate = Date(timeIntervalSince1970: currentScheduleTimestamp)

        var currentEndComponents = calendar.dateComponents([.year, .month, .day], from: currentScheduleDate)
        let endTimeComponents = calendar.dateComponents([.hour, .minute], from: currentTimeSpan.end)
        currentEndComponents.hour = endTimeComponents.hour
        currentEndComponents.minute = endTimeComponents.minute

        // Handle midnight-crossing shifts
        if currentTimeSpan.spansMultipleDays {
            currentEndComponents.day! += 1
        }

        guard let currentShiftEndDate = calendar.date(from: currentEndComponents) else { return nil }

        // Get next shift start date
        guard let nextScheduleTimestamp = nextSchedule.date.toTimestamp() else { return nil }
        let nextScheduleDate = Date(timeIntervalSince1970: nextScheduleTimestamp)

        var nextStartComponents = calendar.dateComponents([.year, .month, .day], from: nextScheduleDate)
        let startTimeComponents = calendar.dateComponents([.hour, .minute], from: nextTimeSpan.start)
        nextStartComponents.hour = startTimeComponents.hour
        nextStartComponents.minute = startTimeComponents.minute

        guard let nextShiftStartDate = calendar.date(from: nextStartComponents) else { return nil }

        // Calculate gap
        let components = calendar.dateComponents([.minute], from: currentShiftEndDate, to: nextShiftStartDate)
        let gapMinutes = components.minute ?? 0

        DebugConfig.debugPrint("⏰ Gap between shifts: \(gapMinutes) minutes (current ends: \(currentShiftEndDate), next starts: \(nextShiftStartDate))")

        return gapMinutes
    }
}
