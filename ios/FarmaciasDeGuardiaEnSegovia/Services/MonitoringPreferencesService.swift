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

/// Manages user consent preferences for monitoring and error tracking
class MonitoringPreferencesService {
    static let shared = MonitoringPreferencesService()

    private init() {}

    // MARK: - UserDefaults Keys

    private enum UserDefaultsKeys {
        static let monitoringEnabled = "monitoring_enabled"
        static let choiceMade = "monitoring_choice_made"
        static let analyticsEnabled = "analytics_enabled"
        static let analyticsChoiceMade = "analytics_choice_made"
    }

    private static let userDefaults = UserDefaults.standard

    // MARK: - Public Methods

    /// Returns true if user has explicitly opted in to monitoring
    func hasUserOptedIn() -> Bool {
        // If user hasn't made a choice yet, default to false (opt-in approach)
        guard hasUserMadeChoice() else {
            return false
        }
        return Self.userDefaults.bool(forKey: UserDefaultsKeys.monitoringEnabled)
    }

    /// Returns true if user has made any choice (enable or decline)
    func hasUserMadeChoice() -> Bool {
        return Self.userDefaults.bool(forKey: UserDefaultsKeys.choiceMade)
    }

    /// Saves user's monitoring preference
    /// - Parameter enabled: true to enable monitoring, false to disable
    func setMonitoringEnabled(_ enabled: Bool) {
        Self.userDefaults.set(enabled, forKey: UserDefaultsKeys.monitoringEnabled)
        Self.userDefaults.set(true, forKey: UserDefaultsKeys.choiceMade)

        DebugConfig.debugPrint(enabled ?
            "✅ User opted IN to monitoring" :
            "⚠️ User opted OUT of monitoring")
    }

    // MARK: - Analytics Preferences

    /// Returns true if user has explicitly opted in to analytics
    func hasUserOptedInToAnalytics() -> Bool {
        guard hasUserMadeAnalyticsChoice() else {
            return false
        }
        return Self.userDefaults.bool(forKey: UserDefaultsKeys.analyticsEnabled)
    }

    /// Returns true if user has made any analytics choice (enable or decline)
    func hasUserMadeAnalyticsChoice() -> Bool {
        return Self.userDefaults.bool(forKey: UserDefaultsKeys.analyticsChoiceMade)
    }

    /// Saves user's analytics preference
    /// - Parameter enabled: true to enable analytics, false to disable
    func setAnalyticsEnabled(_ enabled: Bool) {
        Self.userDefaults.set(enabled, forKey: UserDefaultsKeys.analyticsEnabled)
        Self.userDefaults.set(true, forKey: UserDefaultsKeys.analyticsChoiceMade)

        DebugConfig.debugPrint(enabled ?
            "✅ User opted IN to analytics" :
            "⚠️ User opted OUT of analytics")
    }

    // MARK: - Debug Helpers

    #if DEBUG
    /// Resets all monitoring preferences (for testing)
    func resetPreferences() {
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.monitoringEnabled)
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.choiceMade)
        DebugConfig.debugPrint("🔄 Monitoring preferences reset")
    }

    /// Returns current preferences status for debugging
    func getPreferencesStatus() -> String {
        """
        Monitoring Preferences Status:
        - Has made choice: \(hasUserMadeChoice())
        - Has opted in: \(hasUserOptedIn())
        - Raw monitoring_enabled: \(Self.userDefaults.bool(forKey: UserDefaultsKeys.monitoringEnabled))
        - Raw choice_made: \(Self.userDefaults.bool(forKey: UserDefaultsKeys.choiceMade))
        """
    }
    #endif
}
