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
import StoreKit

/// Manages App Store review prompt tracking and requests
@MainActor
class ReviewPromptService {
    static let shared = ReviewPromptService()

    private init() {}

    // MARK: - UserDefaults Keys

    private enum UserDefaultsKeys {
        static let appLaunchCount = "review_prompt_launch_count"
        static let firstLaunchDate = "review_prompt_first_launch_date"
        static let lastReviewPromptDate = "review_prompt_last_prompt_date"
        static let sessionStartTime = "review_prompt_session_start"
    }

    private static let userDefaults = UserDefaults.standard

    // MARK: - Session Management

    private var sessionTask: Task<Void, Never>?

    // MARK: - Computed Properties

    private var appLaunchCount: Int {
        Self.userDefaults.integer(forKey: UserDefaultsKeys.appLaunchCount)
    }

    private var firstLaunchDate: Date? {
        let timestamp = Self.userDefaults.double(forKey: UserDefaultsKeys.firstLaunchDate)
        return timestamp > 0 ? Date(timeIntervalSince1970: timestamp) : nil
    }

    private var lastReviewPromptDate: Date? {
        let timestamp = Self.userDefaults.double(forKey: UserDefaultsKeys.lastReviewPromptDate)
        return timestamp > 0 ? Date(timeIntervalSince1970: timestamp) : nil
    }

    // MARK: - Public Methods

    /// Records an app launch and updates the launch counter
    func recordAppLaunch() {
        let currentCount = appLaunchCount
        Self.userDefaults.set(currentCount + 1, forKey: UserDefaultsKeys.appLaunchCount)

        // Record first launch timestamp if not already set
        if firstLaunchDate == nil {
            Self.userDefaults.set(Date().timeIntervalSince1970, forKey: UserDefaultsKeys.firstLaunchDate)
            DebugConfig.debugPrint("⭐️ First app launch recorded")
        }

        DebugConfig.debugPrint("⭐️ App launch count: \(currentCount + 1)")
    }

    /// Starts a new session and schedules a review prompt check after the configured delay
    func startSession() {
        let startTime = Date()
        Self.userDefaults.set(startTime.timeIntervalSince1970, forKey: UserDefaultsKeys.sessionStartTime)

        // Cancel any existing session task
        sessionTask?.cancel()

        DebugConfig.debugPrint("⭐️ Review prompt session started")

        // Start new delayed task (non-blocking)
        sessionTask = Task {
            // Wait for the configured session duration
            try? await Task.sleep(nanoseconds: UInt64(AppConfig.ReviewPrompt.minimumSessionDuration * 1_000_000_000))

            // Verify task wasn't cancelled
            guard !Task.isCancelled else {
                DebugConfig.debugPrint("⭐️ Review prompt session task cancelled")
                return
            }

            // Verify session timestamp matches (app didn't background/relaunch)
            let storedTime = Self.userDefaults.double(forKey: UserDefaultsKeys.sessionStartTime)
            guard storedTime == startTime.timeIntervalSince1970 else {
                DebugConfig.debugPrint("⭐️ Review prompt session invalidated (app backgrounded)")
                return
            }

            // Request review (already on main actor since class is @MainActor)
            checkAndRequestReviewIfNeeded(in: getCurrentWindowScene())
        }
    }

    /// Cancels the current session task
    func cancelSession() {
        sessionTask?.cancel()
        sessionTask = nil
        DebugConfig.debugPrint("⭐️ Review prompt session cancelled")
    }

    // MARK: - Private Methods

    /// Checks if all conditions are met and requests a review if appropriate
    private func checkAndRequestReviewIfNeeded(in windowScene: UIWindowScene?) {
        guard shouldPromptForReview() else { return }

        // Record BEFORE showing (Apple doesn't provide callbacks)
        // This prevents showing multiple times even if user dismisses
        recordReviewPrompt()

        guard let scene = windowScene else {
            DebugConfig.debugPrint("⚠️ Cannot request review: no window scene")
            return
        }

        DebugConfig.debugPrint("⭐️ All conditions met - requesting App Store review")
        if #available(iOS 18.0, *) {
            AppStore.requestReview(in: scene)
        } else {
            SKStoreReviewController.requestReview(in: scene)
        }
    }

    /// Checks if all conditions for showing the review prompt are met
    private func shouldPromptForReview() -> Bool {
        // Condition 1: At least 3 launches
        guard appLaunchCount >= AppConfig.ReviewPrompt.minimumLaunchCount else {
            DebugConfig.debugPrint("⭐️ Review prompt skipped: Launch count \(appLaunchCount) < \(AppConfig.ReviewPrompt.minimumLaunchCount)")
            return false
        }

        // Condition 2: At least N days since first launch
        guard let firstLaunch = firstLaunchDate else {
            DebugConfig.debugPrint("⭐️ Review prompt skipped: No first launch date recorded")
            return false
        }

        let daysSinceFirstLaunch = Date().timeIntervalSince(firstLaunch) / (24 * 60 * 60)

        // Detect clock manipulation: negative time
        if daysSinceFirstLaunch < 0 {
            DebugConfig.debugPrint("⚠️ Review prompt skipped: Clock manipulation detected (negative days)")
            return false
        }

        // Detect clock manipulation: unreasonably far future (>10 years)
        if daysSinceFirstLaunch > 3650 {
            DebugConfig.debugPrint("⚠️ Review prompt skipped: Clock manipulation suspected (\(Int(daysSinceFirstLaunch)) days)")
            return false
        }

        guard daysSinceFirstLaunch >= Double(AppConfig.ReviewPrompt.minimumDaysSinceFirstLaunch) else {
            DebugConfig.debugPrint("⭐️ Review prompt skipped: Only \(Int(daysSinceFirstLaunch)) days since first launch")
            return false
        }

        // Condition 3: N days since last prompt (if ever prompted)
        if let lastPrompt = lastReviewPromptDate {
            let daysSinceLastPrompt = Date().timeIntervalSince(lastPrompt) / (24 * 60 * 60)

            // Detect clock manipulation for last prompt date
            if daysSinceLastPrompt < 0 {
                DebugConfig.debugPrint("⚠️ Review prompt skipped: Clock manipulation detected (negative days since last prompt)")
                return false
            }

            guard daysSinceLastPrompt >= Double(AppConfig.ReviewPrompt.daysBetweenPrompts) else {
                DebugConfig.debugPrint("⭐️ Review prompt skipped: Only \(Int(daysSinceLastPrompt)) days since last prompt")
                return false
            }
        }

        return true
    }

    /// Records that a review prompt was shown
    private func recordReviewPrompt() {
        Self.userDefaults.set(Date().timeIntervalSince1970, forKey: UserDefaultsKeys.lastReviewPromptDate)
        DebugConfig.debugPrint("⭐️ Review prompt recorded")
    }

    /// Gets the current foreground window scene
    private func getCurrentWindowScene() -> UIWindowScene? {
        return UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive })
            as? UIWindowScene
    }

    // MARK: - Debug Helpers

    #if DEBUG
    /// Simulates conditions for testing review prompts
    func simulateConditionsForTesting() {
        Self.userDefaults.set(AppConfig.ReviewPrompt.minimumLaunchCount, forKey: UserDefaultsKeys.appLaunchCount)
        Self.userDefaults.set(
            Date().addingTimeInterval(-Double(AppConfig.ReviewPrompt.minimumDaysSinceFirstLaunch + 1) * 24 * 60 * 60).timeIntervalSince1970,
            forKey: UserDefaultsKeys.firstLaunchDate
        )
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.lastReviewPromptDate)
        DebugConfig.debugPrint("⭐️ TEST MODE: Simulated conditions for review prompt")
        DebugConfig.debugPrint("⭐️ \(getTrackingStatus())")
    }

    /// Gets the current tracking status as a string
    func getTrackingStatus() -> String {
        let count = appLaunchCount
        let firstLaunch = firstLaunchDate?.description ?? "nil"
        let lastPrompt = lastReviewPromptDate?.description ?? "nil"
        return "Launches: \(count), First: \(firstLaunch), Last prompt: \(lastPrompt)"
    }

    /// Resets all review prompt tracking data
    func resetAllTracking() {
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.appLaunchCount)
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.firstLaunchDate)
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.lastReviewPromptDate)
        Self.userDefaults.removeObject(forKey: UserDefaultsKeys.sessionStartTime)
        DebugConfig.debugPrint("⭐️ Reset all review prompt tracking")
    }
    #endif
}
