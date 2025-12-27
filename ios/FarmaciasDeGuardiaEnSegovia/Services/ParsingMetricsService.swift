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
import NewRelic

/// Service responsible for recording pharmacy parsing metrics to New Relic
/// Metrics are only sent when actual PDF parsing occurs (not when loading from cache)
class ParsingMetricsService {
    static let shared = ParsingMetricsService()

    private init() {}

    // MARK: - Public Methods

    /// Record parsing metrics for locations that were just parsed from PDF
    /// - Parameter schedulesByLocation: Dictionary of DutyLocation to schedules from PDF parse
    func recordParsingMetrics(for schedulesByLocation: [DutyLocation: [PharmacySchedule]]) {
        // Check user consent first
        guard shouldSendMetrics() else {
            DebugConfig.debugPrint("⚠️ Skipping parsing metrics - user has not opted in to monitoring")
            return
        }

        DebugConfig.debugPrint("📊 Recording parsing metrics for PDF parse operation...")

        // Get app version info
        let versionInfo = getAppVersionInfo()

        // Track total schedules for summary log
        var totalSchedules = 0

        // Record metric for each location
        for (location, schedules) in schedulesByLocation {
            let scheduleCount = schedules.count
            totalSchedules += scheduleCount

            recordMetric(
                for: location,
                scheduleCount: scheduleCount,
                appVersion: versionInfo.version,
                buildNumber: versionInfo.build
            )
        }

        DebugConfig.debugPrint("✅ Recorded parsing metrics for \(schedulesByLocation.count) locations, \(totalSchedules) total schedules")
    }

    // MARK: - Private Methods

    /// Record a single metric for a location
    private func recordMetric(
        for location: DutyLocation,
        scheduleCount: Int,
        appVersion: String,
        buildNumber: String
    ) {
        let attributes: [String: Any] = [
            "locationId": location.id,
            "locationName": location.name,
            "scheduleCount": scheduleCount,
            "appVersion": appVersion,
            "buildNumber": buildNumber
        ]

        NewRelic.recordCustomEvent(
            "PharmacySchedulesParsed",
            attributes: attributes
        )

        DebugConfig.debugPrint("📊 Metric: \(location.id) = \(scheduleCount) schedules (v\(appVersion))")
    }

    /// Get app version information from Bundle
    private func getAppVersionInfo() -> (version: String, build: String) {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "unknown"
        return (version, build)
    }

    /// Check if metrics should be sent (user has opted in to monitoring)
    private func shouldSendMetrics() -> Bool {
        return MonitoringPreferencesService.shared.hasUserOptedIn()
    }
}
