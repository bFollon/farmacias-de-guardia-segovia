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

/// Service for tracking pharmacy parsing operations
/// Parsing errors are automatically captured by OpenTelemetry when exceptions occur
/// This service only logs debug information, no events sent to Signoz for successful parsing
class ParsingMetricsService {
    static let shared = ParsingMetricsService()

    private init() {}

    // MARK: - Public Methods

    /// Log parsing results for debugging (no events sent to Signoz)
    /// - Parameter schedulesByLocation: Dictionary of DutyLocation to schedules from PDF parse
    func recordParsingMetrics(for schedulesByLocation: [DutyLocation: [PharmacySchedule]]) {
        DebugConfig.debugPrint("📊 PDF parsing completed for \(schedulesByLocation.count) locations")

        // Track total schedules for summary log
        var totalSchedules = 0

        // Log parsing results for each location
        for (location, schedules) in schedulesByLocation {
            let scheduleCount = schedules.count
            totalSchedules += scheduleCount
            DebugConfig.debugPrint("📊 Parsed \(location.id): \(scheduleCount) schedules")
        }

        DebugConfig.debugPrint("✅ Total schedules parsed: \(totalSchedules) (no event sent to Signoz)")
    }

}
