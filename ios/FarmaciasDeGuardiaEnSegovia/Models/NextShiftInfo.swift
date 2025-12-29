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

/// Contains information about the next pharmacy shift
struct NextShiftInfo {
    let schedule: PharmacySchedule
    let timeSpan: DutyTimeSpan
    let minutesUntilChange: Int?
    let gapMinutes: Int?

    /// Whether to show the 30-minute transition warning
    var shouldShowWarning: Bool {
        guard let minutes = minutesUntilChange else { return false }
        return minutes > 0 && minutes <= 30
    }

    /// Whether there's a gap between current shift end and next shift start
    /// Gap is considered present if > 2 minutes (handles midnight transitions gracefully)
    var hasGap: Bool {
        guard let gap = gapMinutes else { return false }
        return gap > 2
    }
}
