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

/// Envelope returned by `GET /api/schedules/:locationId` on the sync server.
/// Mirrors `LocationSchedule` in `server/src/types.ts`.
public struct LocationSchedule: Codable {
    public let locationId: String
    public let regionId: String
    public let sourcePdfUrl: String
    public let sourcePdfSha256: String
    public let sourcePdfLastModified: String?
    public let sourcePdfEtag: String?
    public let parsedAt: String
    public let version: Int
    public let schedules: [PharmacySchedule]
}

/// A single entry in the manifest returned by `GET /api/locations`.
public struct LocationManifestEntry: Codable {
    public let locationId: String
    public let version: Int
}
