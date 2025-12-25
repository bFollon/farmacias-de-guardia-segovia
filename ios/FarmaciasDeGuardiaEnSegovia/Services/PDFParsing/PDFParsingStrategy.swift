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
import PDFKit

/// Protocol defining the contract for PDF schedule parsing strategies.
/// Each region/location should implement its own strategy to handle its specific PDF format.
public protocol PDFParsingStrategy {
    /// Parse a PDF document and extract pharmacy schedules organized by duty location
    /// - Parameter pdf: The PDF document to parse
    /// - Returns: A dictionary mapping duty locations to their pharmacy schedules.
    ///           Main regions return 1 entry, Segovia Rural returns 8 entries (one per ZBS).
    func parseSchedules(from pdf: PDFDocument) -> [DutyLocation: [PharmacySchedule]]
}
