import { createHash } from "node:crypto";
import { detectYear, getCurrentYear } from "./yearDetection.js";
import { monthAbbreviationToNumber, monthNumberToName, monthToNumber, spanishDayOfWeek } from "./monthMap.js";
import type { DutyDate, Pharmacy, PharmacySchedule } from "../types.js";

/**
 * Shared base for Cuéllar and El Espinar — Features/parser-port-typescript.md describes
 * them as "near-identical 2-column weekly-schedule structure to each other": a calendar
 * grid of dd-mmm dates where each week-row names one pharmacy (via a hardcoded lookup
 * table, not parsed from the PDF) that's on duty the whole week (DutyTimeSpan.FullDay).
 *
 * NOT a line-for-line port of android's CuellarParser.kt / ElEspinarParser.kt. Tracing
 * ElEspinarParser.kt's fold logic against this region's real PDF (pdfjs-dist's line
 * clustering puts a week's dates and its pharmacy label on the same physical line for
 * some rows, e.g. "05-ene ... 11-ene SAN RAFAEL") shows it silently MISATTRIBUTES entire
 * weeks: `when { hasPharmacy(line) -> ...; hasDates(line) -> ... }` checks pharmacy before
 * dates, so a combined line only extracts the pharmacy and discards that line's own dates,
 * then the *next* week's dates get attributed to the *previous* week's pharmacy label once
 * it finally flushes — a real, currently-shipping bug (not something to "preserve near-
 * verbatim" the way string-matching micro-rules are meant to be) that likely already
 * affects real El Espinar/San Rafael users. Flagged for the client codebases separately.
 *
 * This implementation instead accumulates dates across lines and flushes them against a
 * pharmacy label the moment one appears on the same or a later line — correct regardless
 * of which lines a table row happens to wrap across.
 */

export interface PharmacyInfo {
  name: string;
  address: string;
  phone: string;
}

export interface WeeklyRotationOptions {
  regionLabel: string;
  identifyPharmacy: (line: string) => string | undefined;
  pharmacyInfo: Record<string, PharmacyInfo>;
  /** Cuéllar's PDF has no duplicate-date quirk; El Espinar's does (New Year's Day rendered twice). */
  dedupeDatesPerWeek: boolean;
  /** Extra dd-mmm-shaped date tokens found in a line beyond DATE_TOKEN_REGEX's plain
   * matches — see extractTransitionDateTokens() for Cuéllar's month-boundary format. */
  extraDateTokens?: (line: string) => string[];
}

const DATE_TOKEN_REGEX = /(\d{1,2})[‐-](\w{3})/g;
const FULL_DATE_TOKEN_REGEX = /^(\d{1,2})[‐-](\w{3})$/;
const NEW_YEARS_DAY_REGEX = /^01[‐-]ene$/i;

function stablePharmacyId(regionLabel: string, date: DutyDate, name: string): string {
  const key = `${regionLabel}|${date.year}-${date.month}-${date.day}|fullDay|${name}`;
  return createHash("sha256").update(key).digest("hex").slice(0, 32);
}

function parseDutyDate(dateToken: string, year: number): DutyDate | undefined {
  const match = FULL_DATE_TOKEN_REGEX.exec(dateToken);
  if (!match) return undefined;

  const day = Number(match[1]);
  const month = monthAbbreviationToNumber(match[2]);
  if (month === undefined) return undefined;

  return { dayOfWeek: spanishDayOfWeek(day, month, year), day, month: monthNumberToName(month)!, year };
}

function flushWeek(
  dates: string[],
  pharmacyKey: string,
  year: number,
  options: WeeklyRotationOptions,
): { schedules: PharmacySchedule[]; year: number } {
  const uniqueDates = options.dedupeDatesPerWeek ? [...new Set(dates)] : dates;
  const schedules: PharmacySchedule[] = [];
  let currentYear = year;

  for (const dateToken of uniqueDates) {
    if (NEW_YEARS_DAY_REGEX.test(dateToken)) currentYear += 1;

    const date = parseDutyDate(dateToken, currentYear);
    if (!date) continue;

    const info = options.pharmacyInfo[pharmacyKey] ?? {
      name: `Farmacia ${pharmacyKey}`,
      address: "Dirección no disponible",
      phone: "No disponible",
    };

    const pharmacy: Pharmacy = {
      id: stablePharmacyId(options.regionLabel, date, info.name),
      name: info.name,
      address: info.address,
      phone: info.phone,
      additionalInfo: null,
    };

    schedules.push({ date, shifts: { fullDay: [pharmacy] } });
  }

  return { schedules, year: currentYear };
}

function parsePage(
  lines: string[],
  startYear: number,
  options: WeeklyRotationOptions,
): { schedules: PharmacySchedule[]; year: number } {
  const schedules: PharmacySchedule[] = [];
  let dates: string[] = [];
  let year = startYear;

  for (const line of lines) {
    const lineDates = [...line.matchAll(DATE_TOKEN_REGEX)].map((m) => m[0]);
    if (options.extraDateTokens) lineDates.push(...options.extraDateTokens(line));
    if (lineDates.length > 0) dates.push(...lineDates);

    const pharmacyKey = options.identifyPharmacy(line);
    if (pharmacyKey && dates.length > 0) {
      const result = flushWeek(dates, pharmacyKey, year, options);
      schedules.push(...result.schedules);
      year = result.year;
      dates = [];
    }
  }

  return { schedules, year };
}

export async function parseWeeklyRotation(pages: string[][], options: WeeklyRotationOptions, pdfUrl?: string): Promise<PharmacySchedule[]> {
  const firstPageText = pages[0]?.join("\n") ?? "";
  const yearResult = detectYear(firstPageText, pdfUrl);
  let year = yearResult.year ?? getCurrentYear();

  const allSchedules: PharmacySchedule[] = [];
  for (const pageLines of pages) {
    const { schedules, year: nextYear } = parsePage(pageLines, year, options);
    allSchedules.push(...schedules);
    year = nextYear;
  }

  allSchedules.sort((a, b) => {
    if (a.date.year !== b.date.year) return a.date.year - b.date.year;
    const aMonth = monthToNumber(a.date.month) ?? 0;
    const bMonth = monthToNumber(b.date.month) ?? 0;
    if (aMonth !== bMonth) return aMonth - bMonth;
    return a.date.day - b.date.day;
  });

  return allSchedules;
}
