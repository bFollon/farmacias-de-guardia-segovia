import { createHash } from "node:crypto";
import { detectYear, getCurrentYear } from "./yearDetection.js";
import { SPANISH_DAYS, SPANISH_MONTHS, SPANISH_JANUARY, monthToNumber } from "./monthMap.js";
import type { DutyDate, Pharmacy, PharmacySchedule } from "../types.js";

/**
 * Port of android/.../pdfparsing/strategies/SegoviaCapitalParser.kt, chosen as the primary
 * reference over the iOS coordinate-based parser per Features/parser-port-typescript.md
 * ("text-based extraction is architecturally closer to what's available server-side").
 * String-matching rules are preserved near-verbatim, including the two Android bugfixes
 * (see commits 5dbda38 and 593e516) — a broken parse would otherwise silently drop whole
 * schedule days, which is exactly the failure mode this migration exists to catch centrally.
 */

const PHARMACY_DELIMITER = "FARMACIA";
const DAY_ALTERNATION = SPANISH_DAYS.join("|");
const MONTH_ALTERNATION = SPANISH_MONTHS.join("|");

const PHARMACY_NAME_REGEX = new RegExp(`^(${PHARMACY_DELIMITER}.*)(${PHARMACY_DELIMITER}.*)$`);
const SPANISH_DATE_REGEX = new RegExp(`(${DAY_ALTERNATION}),\\s*(\\d{1,2})\\s*de\\s*(${MONTH_ALTERNATION})`, "i");
const ADDRESS_REGEX = new RegExp(
  `^(?:${DAY_ALTERNATION}),\\s*(?:\\d{1,2})\\s*de\\s*(?:${MONTH_ALTERNATION})\\s+(.+?)(?:,\\s*)?(\\d+|S/N)\\s+(.+?)(?:,\\s*)?(\\d+|S/N)$`,
  "i",
);
const PHONE_REGEX = /Tfno: *\d{3} *\d{6}/;
const PHONE_AND_ADDITIONAL_INFO_REGEX = /(?:(\([^)]+\)) *)?Tfno: *(\d{3} *\d{6})/gi;

/**
 * FARMACIA MARTÍN CANTERO's street/number ("Calle Guadarrama S/N") is printed on the
 * following phone/info line, not the date line, so its date-line text ("CENTRO COMERCIAL
 * LUZ DE CASTILLA") has no trailing number. ADDRESS_REGEX requires one street+number per
 * shift on the date line, so without this it fails to match at all — silently dropping
 * BOTH shifts for that day. Insert a synthetic "S/N" so the regex still matches;
 * overrideAddressIfKnown() then swaps in the real address once the Pharmacy is built.
 * Ported from android's SegoviaCapitalParser.kt (commit 593e516).
 */
const CENTRO_COMERCIAL_MISSING_NUMBER_REGEX = /CENTRO COMERCIAL LUZ DE CASTILLA(?!\s*,?\s*(?:\d+|S\/N))/g;

/**
 * Known address corrections for pharmacies whose PDF entry breaks ADDRESS_REGEX. Keyed by
 * a diacritic/case-insensitive substring of the pharmacy name. Ported from both clients'
 * SegoviaCapitalParser (commit 5dbda38).
 */
const ADDRESS_OVERRIDES: Record<string, string> = {
  "MARTIN CANTERO": "Calle Guadarrama, S/N, Segovia",
};

function normalizeName(name: string): string {
  return name
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toUpperCase();
}

function overrideAddressIfKnown(name: string, address: string): string {
  const normalized = normalizeName(name);
  const match = Object.entries(ADDRESS_OVERRIDES).find(([key]) => normalized.includes(key));
  return match ? match[1] : address;
}

function hasPharmacy(line: string): boolean {
  return line.includes(PHARMACY_DELIMITER);
}

function extractPharmacies(line: string): [string, string] {
  const match = PHARMACY_NAME_REGEX.exec(line);
  return [match?.[1] ?? "", match?.[2] ?? ""];
}

function hasDate(line: string): boolean {
  return SPANISH_DATE_REGEX.test(line);
}

function isNewYears(day: number, month: string): boolean {
  return day === 1 && month.toLowerCase() === SPANISH_JANUARY;
}

function extractDate(line: string, year: number): { date: DutyDate; year: number } | undefined {
  const match = SPANISH_DATE_REGEX.exec(line);
  if (!match) return undefined;

  const [, dayOfWeek, dayString, month] = match;
  const day = Number(dayString);
  const updatedYear = isNewYears(day, month) ? year + 1 : year;

  return { date: { dayOfWeek, day, month, year: updatedYear }, year: updatedYear };
}

function hasPhone(line: string): boolean {
  return PHONE_REGEX.test(line);
}

function extractPhoneAndAdditionalInfo(
  line: string,
): [{ phone: string; extraInfo: string }, { phone: string; extraInfo: string }] | undefined {
  const matches = [...line.matchAll(PHONE_AND_ADDITIONAL_INFO_REGEX)];
  if (matches.length < 2) return undefined;

  const toPair = (m: RegExpMatchArray) => ({ phone: m[2] ?? "", extraInfo: m[1] ?? "" });
  return [toPair(matches[0]), toPair(matches[1])];
}

function extractAddresses(line: string): [string, string] | undefined {
  const normalizedLine = line.replace(CENTRO_COMERCIAL_MISSING_NUMBER_REGEX, (m) => `${m} S/N`);
  const match = ADDRESS_REGEX.exec(normalizedLine);
  if (!match) return undefined;

  const [, dayStreet, dayNumber, nightStreet, nightNumber] = match;
  const formatAddress = (street: string, number: string) =>
    number === "S/N" ? `${street} ${number}` : `${street}, ${Number(number)}`;

  return [formatAddress(dayStreet, dayNumber), formatAddress(nightStreet, nightNumber)];
}

/** Stable id derived from content, not a fresh UUID per parse, per backend-data-service.md's JSON schema notes. */
function stablePharmacyId(date: DutyDate, shiftKey: string, name: string, address: string): string {
  const key = `${date.year}-${date.month}-${date.day}|${shiftKey}|${name}|${address}`;
  return createHash("sha256").update(key).digest("hex").slice(0, 32);
}

interface Builder {
  date: DutyDate | null;
  dayName: string | null;
  dayAddress: string | null;
  dayPhone: string | null;
  dayExtraInfo: string;
  nightName: string | null;
  nightAddress: string | null;
  nightPhone: string | null;
  nightExtraInfo: string;
}

function emptyBuilder(): Builder {
  return {
    date: null,
    dayName: null,
    dayAddress: null,
    dayPhone: null,
    dayExtraInfo: "",
    nightName: null,
    nightAddress: null,
    nightPhone: null,
    nightExtraInfo: "",
  };
}

function tryBuildSchedule(builder: Builder): PharmacySchedule | undefined {
  const { date, dayName, dayAddress, dayPhone, nightName, nightAddress, nightPhone } = builder;
  if (!date || !dayName || !dayAddress || !dayPhone || !nightName || !nightAddress || !nightPhone) {
    return undefined;
  }

  const dayPharmacy: Pharmacy = {
    id: stablePharmacyId(date, "capitalDay", dayName, dayAddress),
    name: dayName,
    address: overrideAddressIfKnown(dayName, dayAddress),
    phone: dayPhone,
    additionalInfo: builder.dayExtraInfo || null,
  };
  const nightPharmacy: Pharmacy = {
    id: stablePharmacyId(date, "capitalNight", nightName, nightAddress),
    name: nightName,
    address: overrideAddressIfKnown(nightName, nightAddress),
    phone: nightPhone,
    additionalInfo: builder.nightExtraInfo || null,
  };

  return {
    date,
    shifts: {
      capitalDay: [dayPharmacy],
      capitalNight: [nightPharmacy],
    },
  };
}

function parsePage(lines: string[], startYear: number): { schedules: PharmacySchedule[]; year: number } {
  const schedules: PharmacySchedule[] = [];
  let builder = emptyBuilder();
  let year = startYear;

  for (const line of lines) {
    if (hasPharmacy(line)) {
      const [dayName, nightName] = extractPharmacies(line);
      builder = { ...builder, dayName, nightName };
    } else if (hasDate(line)) {
      const dateResult = extractDate(line, year);
      if (dateResult) {
        builder.date = dateResult.date;
        year = dateResult.year;
      }
      const addresses = extractAddresses(line);
      if (addresses) {
        builder.dayAddress = addresses[0];
        builder.nightAddress = addresses[1];
      }
    } else if (hasPhone(line)) {
      const info = extractPhoneAndAdditionalInfo(line);
      if (info) {
        builder.dayPhone = info[0].phone;
        builder.dayExtraInfo = info[0].extraInfo;
        builder.nightPhone = info[1].phone;
        builder.nightExtraInfo = info[1].extraInfo;
      }
    }

    const schedule = tryBuildSchedule(builder);
    if (schedule) {
      schedules.push(schedule);
      builder = emptyBuilder();
    }
  }

  return { schedules, year };
}

export async function parseSegoviaCapital(pages: string[][], pdfUrl?: string): Promise<PharmacySchedule[]> {
  const firstPageText = pages[0]?.join("\n") ?? "";
  const yearResult = detectYear(firstPageText, pdfUrl);
  let year = yearResult.year ?? getCurrentYear();

  const allSchedules: PharmacySchedule[] = [];
  for (const pageLines of pages) {
    const { schedules, year: nextYear } = parsePage(pageLines, year);
    allSchedules.push(...schedules);
    year = nextYear;
  }

  return allSchedules;
}
