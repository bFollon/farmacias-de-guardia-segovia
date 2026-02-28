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

// MARK: - Configuration

/// Centralised constants for the confidence scoring system.
/// All thresholds and deductions are here so they can be tuned in one place.
enum ConfidenceConfig {
    // MARK: Display thresholds (0–1 scale)
    /// At or above this level the bar is green.
    static let greenThreshold: Double = 0.90
    /// At or above this level (but below green) the bar is yellow.
    static let yellowThreshold: Double = 0.75

    // MARK: Criterion 1 – URL scraping failure
    static let scrapingFailedDeduction: Double = 0.10

    // MARK: Criterion 2 – URL scraping age
    static let scrapingAge1to7DaysDeduction: Double  = 0.03
    static let scrapingAge7to30DaysDeduction: Double = 0.08
    static let scrapingAgeOver30DaysDeduction: Double = 0.15
    static let scrapingAgeUnknownDeduction: Double   = 0.10

    // MARK: Criterion 3 – Pending PDF update
    static let pendingUpdateDeduction: Double = 0.12

    // MARK: Criterion 4 – Schedule count vs expected
    /// Expected count is derived from first schedule date → days remaining in that year.
    /// 70–93 % of expected.
    static let scheduleCountModerateDeduction: Double = 0.10
    /// 55–70 % of expected.
    static let scheduleCountLowDeduction: Double = 0.20
    /// < 55 % of expected.
    static let scheduleCountVeryLowDeduction: Double = 0.30
    /// Ratio thresholds.
    static let scheduleCountModerateRatio: Double = 0.93
    static let scheduleCountLowRatio: Double      = 0.70
    static let scheduleCountVeryLowRatio: Double  = 0.55

    // MARK: Criterion 5 – No current-year schedules
    static let noCurrentYearDeduction: Double = 0.40

    // MARK: Criterion 6 – Missing days near today (±3 days)
    static let missingDayDeduction: Double = 0.04
    static let missingDayWindowRadius: Int = 3   // days each side of today
}

// MARK: - Data Models

/// Represents a single factor that contributes to (or reduces) confidence.
enum ConfidenceFactor {
    case scrapingFailed(deduction: Double)
    case scrapingAge(days: Int, deduction: Double)
    case pendingPDFUpdate(deduction: Double)
    case lowScheduleCount(actual: Int, expected: Int, deduction: Double)
    case noCurrentYearSchedules(deduction: Double)
    case missingDaysNearToday(missingDays: Int, deduction: Double)

    /// Net deduction this factor applies (positive = bad, reduces score).
    var deduction: Double {
        switch self {
        case .scrapingFailed(let d):          return d
        case .scrapingAge(_, let d):           return d
        case .pendingPDFUpdate(let d):         return d
        case .lowScheduleCount(_, _, let d):   return d
        case .noCurrentYearSchedules(let d):   return d
        case .missingDaysNearToday(_, let d):  return d
        }
    }

    /// Localised Spanish label for the breakdown sheet.
    var localizedTitle: String {
        switch self {
        case .scrapingFailed:
            return "Comprobación de URLs fallida"
        case .scrapingAge(let days, _):
            if days == 0 { return "URLs verificadas recientemente" }
            return "URLs no verificadas hace \(days) días"
        case .pendingPDFUpdate:
            return "Hay una actualización pendiente de los PDFs"
        case .lowScheduleCount(let actual, let expected, _):
            return "Pocos horarios (\(actual) de ~\(expected) esperados)"
        case .noCurrentYearSchedules:
            return "Sin horarios para el año actual"
        case .missingDaysNearToday(let missing, _):
            return "\(missing) día\(missing == 1 ? "" : "s") sin horario cerca de hoy"
        }
    }

    /// Whether this factor is actively lowering the score.
    var isIssue: Bool { deduction > 0 }
}

/// The computed confidence result for a location.
struct ConfidenceResult {
    /// Score in [0, 1].
    let level: Double
    /// All evaluated factors (including those with zero deduction).
    let factors: [ConfidenceFactor]

    /// Score as a 0–100 integer for display.
    var percentage: Int { Int(round(level * 100)) }
}

// MARK: - Service

/// Computes a confidence score that reflects how trustworthy the displayed schedule data is.
///
/// All inputs are read synchronously from UserDefaults / the provided schedules array.
/// No network calls are made here; upstream services are responsible for persisting
/// the state flags that this service reads.
enum ConfidenceService {

    // MARK: Public API

    /// Compute a `ConfidenceResult` for `location` given the currently loaded `schedules`.
    static func computeConfidence(
        for location: DutyLocation,
        schedules: [PharmacySchedule]
    ) -> ConfidenceResult {
        DebugConfig.debugPrint("ConfidenceService: Computing confidence for \(location.associatedRegion.name) with \(schedules.count) schedules")
        var factors: [ConfidenceFactor] = []
        var totalDeduction: Double = 0.0

        // 1. URL scraping failure
        let scrapingFactor = evaluateScraping()
        factors.append(scrapingFactor)
        totalDeduction += scrapingFactor.deduction

        // 2. URL scraping age
        let ageFactor = evaluateScrapingAge()
        factors.append(ageFactor)
        totalDeduction += ageFactor.deduction

        // 3. Pending PDF update
        let updateFactor = evaluatePendingUpdate(for: location.associatedRegion)
        factors.append(updateFactor)
        totalDeduction += updateFactor.deduction

        // 4 & 5. Schedule count + current-year check
        let (countFactor, yearFactor) = evaluateScheduleCount(schedules)
        factors.append(countFactor)
        totalDeduction += countFactor.deduction
        factors.append(yearFactor)
        totalDeduction += yearFactor.deduction

        // 6. Missing days near today
        let missingFactor = evaluateMissingDaysNearToday(schedules)
        factors.append(missingFactor)
        totalDeduction += missingFactor.deduction

        let level = max(0.0, min(1.0, 1.0 - totalDeduction))
        DebugConfig.debugPrint("ConfidenceService: Result for \(location.associatedRegion.name): \(Int(round(level * 100)))% (total deduction: \(String(format: "%.2f", totalDeduction)))")
        return ConfidenceResult(level: level, factors: factors)
    }

    // MARK: Criterion 1 – Scraping success/failure

    private static func evaluateScraping() -> ConfidenceFactor {
        let hasValue = UserDefaults.standard.object(
            forKey: PDFURLScrapingService.lastScrapeSucceededKey
        ) != nil
        // First run: no scrape has ever been attempted — no penalty.
        // The scraping-age criterion handles the "unknown age" case independently.
        guard hasValue else { return .scrapingFailed(deduction: 0) }

        let succeeded = UserDefaults.standard.bool(
            forKey: PDFURLScrapingService.lastScrapeSucceededKey
        )
        return succeeded
            ? .scrapingFailed(deduction: 0)
            : .scrapingFailed(deduction: ConfidenceConfig.scrapingFailedDeduction)
    }

    // MARK: Criterion 2 – Scraping age

    private static func evaluateScrapingAge() -> ConfidenceFactor {
        let timestamp = UserDefaults.standard.double(
            forKey: PDFURLScrapingService.lastScrapeTimestampKey
        )
        guard timestamp > 0 else {
            // Distinguish "never scraped at all" (no penalty) from "scraped but timestamp lost" (unknown penalty).
            let hasEverScraped = UserDefaults.standard.object(
                forKey: PDFURLScrapingService.lastScrapeSucceededKey
            ) != nil
            let deduction = hasEverScraped
                ? ConfidenceConfig.scrapingAgeUnknownDeduction
                : 0.0
            return .scrapingAge(days: -1, deduction: deduction)
        }

        let ageSeconds = Date().timeIntervalSince1970 - timestamp
        let ageDays = Int(ageSeconds / 86_400)

        switch ageDays {
        case 0:
            return .scrapingAge(days: 0, deduction: 0)
        case 1..<7:
            return .scrapingAge(days: ageDays, deduction: ConfidenceConfig.scrapingAge1to7DaysDeduction)
        case 7..<30:
            return .scrapingAge(days: ageDays, deduction: ConfidenceConfig.scrapingAge7to30DaysDeduction)
        default:
            return .scrapingAge(days: ageDays, deduction: ConfidenceConfig.scrapingAgeOver30DaysDeduction)
        }
    }

    // MARK: Criterion 3 – Pending PDF update

    private static func evaluatePendingUpdate(for region: Region) -> ConfidenceFactor {
        let key = PDFCacheManager.pendingUpdateKey(for: region)
        let hasPending = UserDefaults.standard.bool(forKey: key)
        if hasPending {
            return .pendingPDFUpdate(deduction: ConfidenceConfig.pendingUpdateDeduction)
        }
        return .pendingPDFUpdate(deduction: 0)
    }

    // MARK: Criteria 4 & 5 – Schedule count + current year

    private static func evaluateScheduleCount(
        _ schedules: [PharmacySchedule]
    ) -> (ConfidenceFactor, ConfidenceFactor) {
        let currentYear = Calendar.current.component(.year, from: Date())
        let schedulesForCurrentYear = schedules.filter {
            ($0.date.year ?? currentYear) == currentYear
        }

        // Criterion 5: no schedules for current year at all
        if schedulesForCurrentYear.isEmpty {
            let yearFactor = ConfidenceFactor.noCurrentYearSchedules(
                deduction: ConfidenceConfig.noCurrentYearDeduction
            )
            // Count factor is implicitly 0 when there are no current-year schedules
            // (yearFactor already applies a severe penalty; avoid double-counting)
            let countFactor = ConfidenceFactor.lowScheduleCount(actual: schedules.count, expected: 0, deduction: 0)
            return (countFactor, yearFactor)
        }

        // Criterion 4: compare actual count to expected count
        let countFactor = evaluateCount(schedules)
        let yearFactor = ConfidenceFactor.noCurrentYearSchedules(deduction: 0)
        return (countFactor, yearFactor)
    }

    private static func evaluateCount(_ schedules: [PharmacySchedule]) -> ConfidenceFactor {
        guard !schedules.isEmpty else {
            return .lowScheduleCount(
                actual: 0,
                expected: 0,
                deduction: ConfidenceConfig.scheduleCountVeryLowDeduction
            )
        }

        // Derive expected count from the first schedule date
        let sorted = schedules.sorted {
            ($0.date.toTimestamp() ?? 0) < ($1.date.toTimestamp() ?? 0)
        }
        guard let firstDate = sorted.first?.date,
              let firstTimestamp = firstDate.toTimestamp() else {
            return .lowScheduleCount(actual: schedules.count, expected: 0, deduction: 0)
        }

        let calendar = Calendar.current
        let firstDay = Date(timeIntervalSince1970: firstTimestamp)
        let year = calendar.component(.year, from: firstDay)

        // Days from firstDay to Dec 31 of the same year (inclusive)
        var dec31Components = DateComponents()
        dec31Components.year = year
        dec31Components.month = 12
        dec31Components.day = 31
        guard let dec31 = calendar.date(from: dec31Components),
              let daysBetween = calendar.dateComponents([.day], from: firstDay, to: dec31).day else {
            return .lowScheduleCount(actual: schedules.count, expected: 0, deduction: 0)
        }
        let expectedCount = daysBetween + 1  // inclusive

        let actual = schedules.count
        let ratio = expectedCount > 0 ? Double(actual) / Double(expectedCount) : 1.0

        switch ratio {
        case ConfidenceConfig.scheduleCountModerateRatio...:
            return .lowScheduleCount(actual: actual, expected: expectedCount, deduction: 0)
        case ConfidenceConfig.scheduleCountLowRatio..<ConfidenceConfig.scheduleCountModerateRatio:
            return .lowScheduleCount(
                actual: actual, expected: expectedCount,
                deduction: ConfidenceConfig.scheduleCountModerateDeduction
            )
        case ConfidenceConfig.scheduleCountVeryLowRatio..<ConfidenceConfig.scheduleCountLowRatio:
            return .lowScheduleCount(
                actual: actual, expected: expectedCount,
                deduction: ConfidenceConfig.scheduleCountLowDeduction
            )
        default:
            return .lowScheduleCount(
                actual: actual, expected: expectedCount,
                deduction: ConfidenceConfig.scheduleCountVeryLowDeduction
            )
        }
    }

    // MARK: Criterion 6 – Missing days near today

    private static func evaluateMissingDaysNearToday(
        _ schedules: [PharmacySchedule]
    ) -> ConfidenceFactor {
        let calendar = Calendar.current
        let today = Date()
        let radius = ConfidenceConfig.missingDayWindowRadius
        var missingDays = 0

        for offset in -radius...radius {
            guard let targetDate = calendar.date(byAdding: .day, value: offset, to: today) else {
                continue
            }
            let components = calendar.dateComponents([.year, .month, .day], from: targetDate)
            let targetDay   = components.day ?? 0
            let targetMonth = components.month ?? 0
            let targetYear  = components.year ?? 0

            let found = schedules.contains { schedule in
                guard let monthNumber = DutyDate.monthToNumber(schedule.date.month) else { return false }
                let scheduleYear = schedule.date.year ?? DutyDate.getCurrentYear()
                return schedule.date.day == targetDay
                    && monthNumber == targetMonth
                    && scheduleYear == targetYear
            }

            if !found { missingDays += 1 }
        }

        let deduction = Double(missingDays) * ConfidenceConfig.missingDayDeduction
        return .missingDaysNearToday(missingDays: missingDays, deduction: deduction)
    }
}
