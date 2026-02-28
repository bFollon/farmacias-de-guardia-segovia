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

package com.github.bfollon.farmaciasdeguardiaensegovia.services

import android.content.Context
import com.github.bfollon.farmaciasdeguardiaensegovia.data.ConfidenceFactor
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyDate
import com.github.bfollon.farmaciasdeguardiaensegovia.data.DutyLocation
import com.github.bfollon.farmaciasdeguardiaensegovia.data.PharmacySchedule
import com.github.bfollon.farmaciasdeguardiaensegovia.repositories.PDFURLRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

/**
 * Centralised constants for the confidence scoring system.
 * All thresholds and deductions are here so they can be tuned in one place.
 */
object ConfidenceConfig {
    // Display thresholds (0–1 scale)
    /** At or above this level the bar is green. */
    const val GREEN_THRESHOLD: Double = 0.90
    /** At or above this level (but below green) the bar is yellow. */
    const val YELLOW_THRESHOLD: Double = 0.75

    // Criterion 1 – URL scraping failure
    const val SCRAPING_FAILED_DEDUCTION: Double = 0.10

    // Criterion 2 – URL scraping age
    const val SCRAPING_AGE_1_TO_7_DAYS_DEDUCTION: Double  = 0.03
    const val SCRAPING_AGE_7_TO_30_DAYS_DEDUCTION: Double = 0.08
    const val SCRAPING_AGE_OVER_30_DAYS_DEDUCTION: Double = 0.15
    const val SCRAPING_AGE_UNKNOWN_DEDUCTION: Double      = 0.10

    // Criterion 3 – Pending PDF update
    const val PENDING_UPDATE_DEDUCTION: Double = 0.12

    // Criterion 4 – Schedule count vs expected
    const val SCHEDULE_COUNT_MODERATE_DEDUCTION: Double = 0.10   // 70–93 %
    const val SCHEDULE_COUNT_LOW_DEDUCTION: Double      = 0.20   // 55–70 %
    const val SCHEDULE_COUNT_VERY_LOW_DEDUCTION: Double = 0.30   // < 55 %
    const val SCHEDULE_COUNT_MODERATE_RATIO: Double     = 0.93
    const val SCHEDULE_COUNT_LOW_RATIO: Double          = 0.70
    const val SCHEDULE_COUNT_VERY_LOW_RATIO: Double     = 0.55

    // Criterion 5 – No current-year schedules
    const val NO_CURRENT_YEAR_DEDUCTION: Double = 0.40

    // Criterion 6 – Missing days near today (±3 days)
    const val MISSING_DAY_DEDUCTION: Double = 0.04
    const val MISSING_DAY_WINDOW_RADIUS: Int = 3   // days each side of today
}

// ---------------------------------------------------------------------------
// Result
// ---------------------------------------------------------------------------

/** The computed confidence result for a location. */
data class ConfidenceResult(
    /** Score in [0, 1]. */
    val level: Double,
    /** All evaluated factors (including those with zero deduction). */
    val factors: List<ConfidenceFactor>
) {
    /** Score as a 0–100 integer for display. */
    val percentage: Int get() = (level * 100).roundToInt()
}

// ---------------------------------------------------------------------------
// Service
// ---------------------------------------------------------------------------

/**
 * Computes a confidence score that reflects how trustworthy the displayed schedule data is.
 *
 * All inputs are read synchronously from SharedPreferences / the provided schedules list.
 * No network calls are made here; upstream services persist the state flags this service reads.
 */
class ConfidenceService(private val context: Context) {

    private val urlRepository = PDFURLRepository.getInstance(context)
    private val pdfCacheManager = PDFCacheManager.getInstance(context)

    /**
     * Compute a [ConfidenceResult] for [location] given the currently loaded [schedules].
     */
    fun computeConfidence(
        location: DutyLocation,
        schedules: List<PharmacySchedule>
    ): ConfidenceResult {
        DebugConfig.debugPrint("ConfidenceService: Computing confidence for ${location.associatedRegion.name} with ${schedules.size} schedules")
        val factors = mutableListOf<ConfidenceFactor>()
        var totalDeduction = 0.0

        // 1. URL scraping failure
        val scrapingFactor = evaluateScraping()
        factors += scrapingFactor
        totalDeduction += scrapingFactor.deduction

        // 2. URL scraping age
        val ageFactor = evaluateScrapingAge()
        factors += ageFactor
        totalDeduction += ageFactor.deduction

        // 3. Pending PDF update
        val updateFactor = evaluatePendingUpdate(location)
        factors += updateFactor
        totalDeduction += updateFactor.deduction

        // 4 & 5. Schedule count + current-year check
        val (countFactor, yearFactor) = evaluateScheduleCount(schedules)
        factors += countFactor
        totalDeduction += countFactor.deduction
        factors += yearFactor
        totalDeduction += yearFactor.deduction

        // 6. Missing days near today
        val missingFactor = evaluateMissingDaysNearToday(schedules)
        factors += missingFactor
        totalDeduction += missingFactor.deduction

        val level = max(0.0, min(1.0, 1.0 - totalDeduction))
        DebugConfig.debugPrint("ConfidenceService: Result for ${location.associatedRegion.name}: ${(level * 100).roundToInt()}% (total deduction: ${"%.2f".format(totalDeduction)})")
        return ConfidenceResult(level = level, factors = factors)
    }

    // -----------------------------------------------------------------------
    // Criterion 1 – Scraping success/failure
    // -----------------------------------------------------------------------

    private fun evaluateScraping(): ConfidenceFactor {
        // First run: no scrape has ever been attempted — no penalty.
        // The scraping-age criterion handles the "unknown age" case independently.
        if (!urlRepository.hasLastScrapeSucceededValue()) {
            return ConfidenceFactor.ScrapingFailed(deduction = 0.0)
        }
        return if (!urlRepository.getLastScrapeSucceeded()) {
            ConfidenceFactor.ScrapingFailed(deduction = ConfidenceConfig.SCRAPING_FAILED_DEDUCTION)
        } else {
            ConfidenceFactor.ScrapingFailed(deduction = 0.0)
        }
    }

    // -----------------------------------------------------------------------
    // Criterion 2 – Scraping age
    // -----------------------------------------------------------------------

    private fun evaluateScrapingAge(): ConfidenceFactor {
        val timestamp = urlRepository.getLastScrapeTimestamp()
        if (timestamp == 0L) {
            // Distinguish "never scraped at all" (no penalty) from "scraped but timestamp lost" (unknown penalty).
            val deduction = if (urlRepository.hasLastScrapeSucceededValue())
                ConfidenceConfig.SCRAPING_AGE_UNKNOWN_DEDUCTION
            else
                0.0
            return ConfidenceFactor.ScrapingAge(days = -1, deduction = deduction)
        }
        val ageMs = System.currentTimeMillis() - timestamp
        val ageDays = TimeUnit.MILLISECONDS.toDays(ageMs).toInt()

        return when {
            ageDays == 0      -> ConfidenceFactor.ScrapingAge(days = 0, deduction = 0.0)
            ageDays < 7       -> ConfidenceFactor.ScrapingAge(days = ageDays, deduction = ConfidenceConfig.SCRAPING_AGE_1_TO_7_DAYS_DEDUCTION)
            ageDays < 30      -> ConfidenceFactor.ScrapingAge(days = ageDays, deduction = ConfidenceConfig.SCRAPING_AGE_7_TO_30_DAYS_DEDUCTION)
            else              -> ConfidenceFactor.ScrapingAge(days = ageDays, deduction = ConfidenceConfig.SCRAPING_AGE_OVER_30_DAYS_DEDUCTION)
        }
    }

    // -----------------------------------------------------------------------
    // Criterion 3 – Pending PDF update
    // -----------------------------------------------------------------------

    private fun evaluatePendingUpdate(location: DutyLocation): ConfidenceFactor {
        val hasPending = pdfCacheManager.hasPendingUpdate(location.associatedRegion)
        return if (hasPending) {
            ConfidenceFactor.PendingPDFUpdate(deduction = ConfidenceConfig.PENDING_UPDATE_DEDUCTION)
        } else {
            ConfidenceFactor.PendingPDFUpdate(deduction = 0.0)
        }
    }

    // -----------------------------------------------------------------------
    // Criteria 4 & 5 – Schedule count + current year
    // -----------------------------------------------------------------------

    private fun evaluateScheduleCount(
        schedules: List<PharmacySchedule>
    ): Pair<ConfidenceFactor, ConfidenceFactor> {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val schedulesForCurrentYear = schedules.filter {
            (it.date.year ?: currentYear) == currentYear
        }

        // Criterion 5: no schedules for current year at all
        if (schedulesForCurrentYear.isEmpty()) {
            val yearFactor = ConfidenceFactor.NoCurrentYearSchedules(
                deduction = ConfidenceConfig.NO_CURRENT_YEAR_DEDUCTION
            )
            // Avoid double-counting – count factor has no penalty when year factor fires
            val countFactor = ConfidenceFactor.LowScheduleCount(
                actual = schedules.size, expected = 0, deduction = 0.0
            )
            return Pair(countFactor, yearFactor)
        }

        // Criterion 4
        val countFactor = evaluateCount(schedules)
        val yearFactor  = ConfidenceFactor.NoCurrentYearSchedules(deduction = 0.0)
        return Pair(countFactor, yearFactor)
    }

    private fun evaluateCount(schedules: List<PharmacySchedule>): ConfidenceFactor {
        if (schedules.isEmpty()) {
            return ConfidenceFactor.LowScheduleCount(
                actual = 0, expected = 0,
                deduction = ConfidenceConfig.SCHEDULE_COUNT_VERY_LOW_DEDUCTION
            )
        }

        // Derive expected count from the first schedule date
        val sorted = schedules.sortedBy { it.date.toTimestamp() ?: 0L }
        val firstDate = sorted.first().date
        val firstTimestamp = firstDate.toTimestamp() ?: return ConfidenceFactor.LowScheduleCount(
            actual = schedules.size, expected = 0, deduction = 0.0
        )

        val firstCal = Calendar.getInstance().apply { timeInMillis = firstTimestamp }
        val year = firstCal.get(Calendar.YEAR)

        val dec31 = Calendar.getInstance().apply {
            set(year, Calendar.DECEMBER, 31)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMs = dec31.timeInMillis - firstTimestamp
        val expectedCount = TimeUnit.MILLISECONDS.toDays(diffMs).toInt() + 1  // inclusive

        val actual = schedules.size
        val ratio = if (expectedCount > 0) actual.toDouble() / expectedCount.toDouble() else 1.0

        return when {
            ratio >= ConfidenceConfig.SCHEDULE_COUNT_MODERATE_RATIO ->
                ConfidenceFactor.LowScheduleCount(actual, expectedCount, 0.0)
            ratio >= ConfidenceConfig.SCHEDULE_COUNT_LOW_RATIO ->
                ConfidenceFactor.LowScheduleCount(actual, expectedCount, ConfidenceConfig.SCHEDULE_COUNT_MODERATE_DEDUCTION)
            ratio >= ConfidenceConfig.SCHEDULE_COUNT_VERY_LOW_RATIO ->
                ConfidenceFactor.LowScheduleCount(actual, expectedCount, ConfidenceConfig.SCHEDULE_COUNT_LOW_DEDUCTION)
            else ->
                ConfidenceFactor.LowScheduleCount(actual, expectedCount, ConfidenceConfig.SCHEDULE_COUNT_VERY_LOW_DEDUCTION)
        }
    }

    // -----------------------------------------------------------------------
    // Criterion 6 – Missing days near today
    // -----------------------------------------------------------------------

    private fun evaluateMissingDaysNearToday(schedules: List<PharmacySchedule>): ConfidenceFactor {
        val today = Calendar.getInstance()
        val radius = ConfidenceConfig.MISSING_DAY_WINDOW_RADIUS
        var missingDays = 0

        for (offset in -radius..radius) {
            val target = Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val targetDay   = target.get(Calendar.DAY_OF_MONTH)
            val targetMonth = target.get(Calendar.MONTH) + 1   // Calendar months are 0-based
            val targetYear  = target.get(Calendar.YEAR)

            val found = schedules.any { schedule ->
                val monthNumber = DutyDate.monthToNumber(schedule.date.month) ?: return@any false
                val scheduleYear = schedule.date.year ?: Calendar.getInstance().get(Calendar.YEAR)
                schedule.date.day == targetDay &&
                    monthNumber == targetMonth &&
                    scheduleYear == targetYear
            }

            if (!found) missingDays++
        }

        val deduction = missingDays * ConfidenceConfig.MISSING_DAY_DEDUCTION
        return ConfidenceFactor.MissingDaysNearToday(missingDays = missingDays, deduction = deduction)
    }

}
