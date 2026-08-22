/**
 * Port of android/.../services/YearDetectionService.kt (chosen as the primary reference
 * per Features/parser-port-typescript.md's "text-based extraction, not coordinate-based"
 * decision). Multi-layer year detection: PDF URL -> standard text pattern -> flexible
 * text pattern -> current-year fallback, then a December-adjustment pass on top.
 */

export type YearSource =
  | { kind: "extractedFromURL"; original: string }
  | { kind: "extractedFromPDF"; original: string }
  | { kind: "extractedFlexible"; original: string }
  | { kind: "fallbackDecember" }
  | { kind: "fallbackCurrent" }
  | { kind: "invalid"; reason: string };

export interface YearDetectionResult {
  year: number;
  source: YearSource;
  isValid: boolean;
  warning?: string;
}

export function getCurrentYear(): number {
  return new Date().getFullYear();
}

function isYearInValidRange(yearString: string): boolean {
  const year = Number(yearString);
  if (!Number.isFinite(year)) return false;
  const currentYear = getCurrentYear();
  return year >= currentYear - 20 && year <= currentYear + 20;
}

function validateYear(year: number, currentYear: number): { valid: boolean; warning?: string } {
  const difference = Math.abs(year - currentYear);
  if (difference <= 2) {
    return difference === 2
      ? { valid: true, warning: `Year ${year} is at the edge of valid range (±2 years from ${currentYear})` }
      : { valid: true };
  }
  return {
    valid: false,
    warning: `Year ${year} is outside valid range (±2 years from ${currentYear}). PDF may be outdated or incorrect.`,
  };
}

/** Rightmost 4-digit year in the URL wins (filename year has priority over path year). */
function extractYearFromURL(url: string): string | undefined {
  const allYears = [...url.matchAll(/(\d{4})/g)].map((m) => m[1]);
  for (const year of [...allYears].reverse()) {
    if (isYearInValidRange(year)) return year;
  }
  return undefined;
}

/** Matches a single year (2025) or a span (2024-2025), returning the first year. */
function extractYearFromText(text: string): string | undefined {
  const match = text.match(/\b(20[2-3]\d)(?:\s*-\s*20[2-3]\d)?\b/);
  return match?.[1];
}

/** Matches malformed years like "2.025", "2-0-2-5", "2 0 2 5". */
function extractYearFlexible(text: string): string | undefined {
  const match = text.match(/2\D?0\D?([2-3])\D?(\d)/);
  if (!match) return undefined;
  const reconstructed = `20${match[1]}${match[2]}`;
  return isYearInValidRange(reconstructed) ? reconstructed : undefined;
}

/** Heuristic: December dates in the first 500 chars mean the PDF starts mid-prior-year. */
function isFirstDateDecember(text: string): boolean {
  return /\b\d{1,2}[‐-]dic\b/i.test(text.slice(0, 500));
}

interface DetectedYearInfo {
  year: number;
  originalString: string;
  source: YearSource;
}

function detectYearFromSources(text: string, pdfUrl: string | undefined, currentYear: number): DetectedYearInfo {
  if (pdfUrl) {
    const yearString = extractYearFromURL(pdfUrl);
    if (yearString) {
      const year = Number(yearString);
      if (validateYear(year, currentYear).valid) {
        return { year, originalString: yearString, source: { kind: "extractedFromURL", original: yearString } };
      }
    }
  }

  const textYear = extractYearFromText(text);
  if (textYear) {
    const year = Number(textYear);
    if (validateYear(year, currentYear).valid) {
      return { year, originalString: textYear, source: { kind: "extractedFromPDF", original: textYear } };
    }
  }

  const flexibleYear = extractYearFlexible(text);
  if (flexibleYear) {
    const year = Number(flexibleYear);
    if (validateYear(year, currentYear).valid) {
      return { year, originalString: flexibleYear, source: { kind: "extractedFlexible", original: flexibleYear } };
    }
  }

  return { year: currentYear, originalString: String(currentYear), source: { kind: "fallbackCurrent" } };
}

function applyDecemberAdjustment(year: number, text: string, source: YearSource): YearDetectionResult {
  if (isFirstDateDecember(text)) {
    const adjustedYear = year - 1;
    const adjustedSource: YearSource = source.kind === "fallbackCurrent" ? { kind: "fallbackDecember" } : source;
    return {
      year: adjustedYear,
      source: adjustedSource,
      isValid: true,
      warning: `Found year ${year}, but adjusted to ${adjustedYear} due to December dates at start of schedule.`,
    };
  }
  return { year, source, isValid: true };
}

export function detectYear(text: string, pdfUrl?: string): YearDetectionResult {
  const currentYear = getCurrentYear();
  const detected = detectYearFromSources(text, pdfUrl, currentYear);
  return applyDecemberAdjustment(detected.year, text, detected.source);
}
