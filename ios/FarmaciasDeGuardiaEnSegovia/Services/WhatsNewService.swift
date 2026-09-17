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

struct WhatsNewEntry {
    let icon: String
    let title: String
    let body: String
    /// The version this entry shipped in, e.g. "1.9.0" — see `MARKETING_VERSION` in project.pbxproj.
    let version: String
}

/// Version-*range*-gated "what's new" notice, shown once per app update.
///
/// This keys off "has this app version been seen before", so it surfaces on first launch after
/// an update, before the user has to go looking for whatever changed.
///
/// `entries` is append-only: each release adds new version-tagged entries, older ones are never
/// removed. A user who skips versions sees everything they missed (`entriesToShow()`), not just
/// whatever shipped in the version they happen to update to — capped at `maxEntriesToShow` so
/// someone who hasn't updated in a very long time doesn't get a wall of old announcements.
enum WhatsNewService {
    private static let lastSeenVersionKey = "whats_new_last_seen_version"

    /// Upper bound on how many past entries to show at once, oldest-missed dropped first.
    private static let maxEntriesToShow = 5

    static var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }

    /// All announcements ever shipped, oldest first. Append new ones here on every release that
    /// warrants an announcement — never remove or overwrite past entries.
    static let entries: [WhatsNewEntry] = [
        WhatsNewEntry(
            icon: "arrow.triangle.2.circlepath",
            title: "Información más fiable",
            body: "Los horarios de guardia ahora se procesan una sola vez en nuestro servidor y se sincronizan con todos los dispositivos, en lugar de que cada móvil interprete el PDF oficial por su cuenta. Así todo el mundo ve siempre los mismos datos, sin errores de lectura puntuales. Como siempre, el servicio sigue y seguirá siendo gratuito y sin anuncios. Gracias por usarlo.",
            version: "2.0.0"
        ),
        WhatsNewEntry(
            icon: "soccerball",
            title: "Aviso de bloqueo por LaLiga",
            body: "Si nuestro servidor no responde durante un partido de fútbol, ahora te lo decimos: puede deberse al bloqueo de IPs ordenado judicialmente por LaLiga contra la piratería, que a veces afecta a servicios que nada tienen que ver con el fútbol pirata. Toca el aviso para más información.",
            version: "2.1.0"
        ),
    ]

    /// Entries the user hasn't seen yet: `version > lastSeenVersion` and `version <= currentVersion`,
    /// sorted ascending and capped to the most recent `maxEntriesToShow`.
    static func entriesToShow() -> [WhatsNewEntry] {
        let lastSeen = UserDefaults.standard.string(forKey: lastSeenVersionKey) ?? "0.0.0"
        let inRange = entries.filter {
            compareSemVer($0.version, lastSeen) == .orderedDescending
                && compareSemVer($0.version, currentVersion) != .orderedDescending
        }
        let sorted = inRange.sorted { compareSemVer($0.version, $1.version) == .orderedAscending }
        return Array(sorted.suffix(maxEntriesToShow))
    }

    /// True if there's at least one unseen entry to announce on this device.
    ///
    /// No last-seen version recorded means one of two things: a genuinely fresh install (in
    /// which case there's nothing to announce — the user never saw the old behavior), or an
    /// existing install upgrading into the very first version that ships this mechanism (in
    /// which case they *should* see whatever they missed). `MonitoringPreferencesService.hasUserMadeAnalyticsChoice`
    /// distinguishes the two: that choice is only ever made once, on a screen every install has
    /// gone through since long before this feature existed, so its presence means the app has
    /// run on this device before.
    static func shouldShow() -> Bool {
        guard UserDefaults.standard.string(forKey: lastSeenVersionKey) != nil else {
            let isExistingInstall = MonitoringPreferencesService.shared.hasUserMadeAnalyticsChoice()
            if !isExistingInstall {
                markAsSeen()
                return false
            }
            return !entriesToShow().isEmpty
        }
        return !entriesToShow().isEmpty
    }

    static func markAsSeen() {
        UserDefaults.standard.set(currentVersion, forKey: lastSeenVersionKey)
    }
}

/// Compares two "MAJOR.MINOR.PATCH" SemVer strings numerically, not lexicographically
/// (`"1.10.0" > "1.9.0"`, which plain string comparison would get wrong). Missing or
/// non-numeric components are treated as 0.
private func compareSemVer(_ lhs: String, _ rhs: String) -> ComparisonResult {
    let l = semVerComponents(lhs)
    let r = semVerComponents(rhs)
    if l != r {
        return (l.0, l.1, l.2) < (r.0, r.1, r.2) ? .orderedAscending : .orderedDescending
    }
    return .orderedSame
}

private func semVerComponents(_ version: String) -> (Int, Int, Int) {
    let parts = version.split(separator: ".").map { Int($0) ?? 0 }
    return (parts.count > 0 ? parts[0] : 0, parts.count > 1 ? parts[1] : 0, parts.count > 2 ? parts[2] : 0)
}
