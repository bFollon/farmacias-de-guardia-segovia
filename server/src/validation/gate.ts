import type { LocationSchedule, PharmacySchedule } from "../types.js";
import { monthToNumber } from "../parsers/monthMap.js";

/**
 * Phase 1, step 3 of the migration (Features/parsing-validation-gate.md). Sits between
 * "parser produced JSON" and "backend-data-service publishes it": a parser can succeed
 * without throwing while still producing garbage (empty schedule, dropped days, a shift
 * with no pharmacies) and nothing downstream would notice before it ships to every user's
 * device. Fail closed — a failing check means the previous published JSON stays live.
 */

export interface RegionValidationConfig {
  /** Non-empty floor: below this, treat the parse as a total failure. */
  minScheduleCount: number;
  /** Shift keys every schedule entry must have, each with >=1 pharmacy. */
  requiredShiftKeys: string[];
  /** Expected day-to-day cadence between consecutive parsed dates, in days (1 = daily). */
  expectedCadenceDays: number;
  /** Some Segovia Rural ZBS genuinely only cover weekdays (or have a real hole near a
   * calendar's end) in the source PDF, so gaps there are expected, not a parsing failure. */
  skipDateContinuityCheck?: boolean;
  /** Max fractional change vs. the previous published version before flagging (0.5 = 50%). */
  maxDeltaFraction: number;
}

export interface ValidationResult {
  passed: boolean;
  failures: string[];
}

function toJsDate(date: PharmacySchedule["date"]): Date | undefined {
  const month = monthToNumber(date.month);
  if (month === undefined) return undefined;
  return new Date(Date.UTC(date.year, month - 1, date.day));
}

function checkNonEmpty(schedules: PharmacySchedule[], config: RegionValidationConfig, failures: string[]): void {
  if (schedules.length < config.minScheduleCount) {
    failures.push(`Non-empty: only ${schedules.length} schedules parsed (floor is ${config.minScheduleCount})`);
  }
}

function checkDateContinuity(schedules: PharmacySchedule[], config: RegionValidationConfig, failures: string[]): void {
  const dated = schedules
    .map((s) => ({ schedule: s, jsDate: toJsDate(s.date) }))
    .filter((s): s is { schedule: PharmacySchedule; jsDate: Date } => s.jsDate !== undefined)
    .sort((a, b) => a.jsDate.getTime() - b.jsDate.getTime());

  const cadenceMs = config.expectedCadenceDays * 24 * 60 * 60 * 1000;
  const gaps: string[] = [];

  for (let i = 1; i < dated.length; i++) {
    const diffMs = dated[i].jsDate.getTime() - dated[i - 1].jsDate.getTime();
    if (diffMs !== cadenceMs) {
      const prev = dated[i - 1].schedule.date;
      const curr = dated[i].schedule.date;
      gaps.push(`${prev.day} de ${prev.month} -> ${curr.day} de ${curr.month} (expected ${config.expectedCadenceDays}d gap)`);
    }
  }

  if (gaps.length > 0) {
    failures.push(`Date continuity: ${gaps.length} unexpected gap(s): ${gaps.slice(0, 5).join("; ")}`);
  }
}

function checkShiftCompleteness(schedules: PharmacySchedule[], config: RegionValidationConfig, failures: string[]): void {
  const incomplete: string[] = [];

  for (const schedule of schedules) {
    for (const key of config.requiredShiftKeys) {
      const pharmacies = schedule.shifts[key];
      if (!pharmacies || pharmacies.length === 0) {
        incomplete.push(`${schedule.date.day} de ${schedule.date.month}: missing/empty shift "${key}"`);
      }
    }
  }

  if (incomplete.length > 0) {
    failures.push(`Shift completeness: ${incomplete.length} issue(s): ${incomplete.slice(0, 5).join("; ")}`);
  }
}

// Real source data groups digits inconsistently across regions — "921 427270" (3+6),
// "921144794" (ungrouped), "921 181 171" (3+3+3) all appear — so validate digit count
// after stripping spaces rather than a single fixed grouping.
const SPANISH_PHONE_DIGITS_REGEX = /^\d{9}$/;

function checkPharmacyShapeSanity(schedules: PharmacySchedule[], failures: string[]): void {
  const bad: string[] = [];

  for (const schedule of schedules) {
    for (const pharmacies of Object.values(schedule.shifts)) {
      for (const pharmacy of pharmacies) {
        const label = `${schedule.date.day} de ${schedule.date.month} (${pharmacy.name || "<empty>"})`;
        // Not "must contain FARMACIA": Segovia Rural's hardcoded table legitimately has
        // at least one entry ("Dr. Jesús Santos del Cura", Fuentidueña/Olombrada) that
        // isn't named "Farmacia ...". Names in every region come from a trusted source
        // (a hardcoded table, or — for Segovia Capital — a regex requiring "FARMACIA" to
        // even recognize the line as a pharmacy in the first place), so a bare non-empty
        // check is sufficient; this isn't guarding against unvalidated free text.
        if (!pharmacy.name.trim()) {
          bad.push(`${label}: empty name`);
        } else if (!pharmacy.address.trim()) {
          bad.push(`${label}: empty address`);
        } else if (
          pharmacy.phone.trim() !== "No disponible" &&
          !SPANISH_PHONE_DIGITS_REGEX.test(pharmacy.phone.replace(/\s/g, ""))
        ) {
          bad.push(`${label}: phone "${pharmacy.phone}" doesn't match expected format`);
        }
      }
    }
  }

  if (bad.length > 0) {
    failures.push(`Pharmacy shape sanity: ${bad.length} issue(s): ${bad.slice(0, 5).join("; ")}`);
  }
}

function countDistinctPharmacies(schedules: PharmacySchedule[]): number {
  const names = new Set<string>();
  for (const schedule of schedules) {
    for (const pharmacies of Object.values(schedule.shifts)) {
      for (const pharmacy of pharmacies) names.add(pharmacy.name.trim().toUpperCase());
    }
  }
  return names.size;
}

/** Previous schedules clipped to [newMin, newMax] — the new parse's own date window. A
 * source PDF that legitimately narrows its date range (e.g. a full year shrinking to a
 * rolling few-month window) should be compared against the same narrower slice of the old
 * data, not the old data's full length, or a shrinking window looks identical to dropped
 * days. Falls back to the full previous set when there's no date overlap at all (e.g. the
 * new parse covers a different year entirely), so that case still fails closed. */
function previousInNewRange(schedules: PharmacySchedule[], previous: LocationSchedule): PharmacySchedule[] {
  const newDates = schedules.map((s) => toJsDate(s.date)).filter((d): d is Date => d !== undefined);
  if (newDates.length === 0) return previous.schedules;

  const newMin = Math.min(...newDates.map((d) => d.getTime()));
  const newMax = Math.max(...newDates.map((d) => d.getTime()));

  const clipped = previous.schedules.filter((s) => {
    const d = toJsDate(s.date);
    return d !== undefined && d.getTime() >= newMin && d.getTime() <= newMax;
  });

  return clipped.length > 0 ? clipped : previous.schedules;
}

function checkDeltaBound(
  schedules: PharmacySchedule[],
  config: RegionValidationConfig,
  previous: LocationSchedule | undefined,
  failures: string[],
): void {
  if (!previous || previous.schedules.length === 0) return; // first publish — nothing to compare against

  const baseline = previousInNewRange(schedules, previous);

  const scheduleDelta = Math.abs(schedules.length - baseline.length) / baseline.length;
  if (scheduleDelta > config.maxDeltaFraction) {
    failures.push(
      `Delta bound: schedule count changed by ${(scheduleDelta * 100).toFixed(0)}% ` +
        `(${baseline.length} -> ${schedules.length}, max is ${config.maxDeltaFraction * 100}%)`,
    );
  }

  const previousPharmacyCount = countDistinctPharmacies(baseline);
  const newPharmacyCount = countDistinctPharmacies(schedules);
  if (previousPharmacyCount > 0) {
    const pharmacyDelta = Math.abs(newPharmacyCount - previousPharmacyCount) / previousPharmacyCount;
    if (pharmacyDelta > config.maxDeltaFraction) {
      failures.push(
        `Delta bound: distinct pharmacy count changed by ${(pharmacyDelta * 100).toFixed(0)}% ` +
          `(${previousPharmacyCount} -> ${newPharmacyCount}, max is ${config.maxDeltaFraction * 100}%)`,
      );
    }
  }
}

export interface ValidateOptions {
  /** Skips only the delta-bound check — for an operator-confirmed refresh where the swing
   * is known to be legitimate (e.g. the source PDF's date range grew), not a parsing bug.
   * Every other check still runs, so a genuinely broken parse still fails closed. */
  skipDeltaCheck?: boolean;
}

export function validateSchedules(
  schedules: PharmacySchedule[],
  config: RegionValidationConfig,
  previous: LocationSchedule | undefined,
  options?: ValidateOptions,
): ValidationResult {
  const failures: string[] = [];

  checkNonEmpty(schedules, config, failures);
  if (!config.skipDateContinuityCheck) checkDateContinuity(schedules, config, failures);
  checkShiftCompleteness(schedules, config, failures);
  checkPharmacyShapeSanity(schedules, failures);
  if (!options?.skipDeltaCheck) checkDeltaBound(schedules, config, previous, failures);

  return { passed: failures.length === 0, failures };
}
