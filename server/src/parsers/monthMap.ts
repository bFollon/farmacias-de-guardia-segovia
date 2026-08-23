// Port of DutyDate.kt's Spanish weekday/month vocabulary, shared by every region parser.

export const SPANISH_DAYS = ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"] as const;

export const SPANISH_MONTHS = [
  "enero",
  "febrero",
  "marzo",
  "abril",
  "mayo",
  "junio",
  "julio",
  "agosto",
  "septiembre",
  "octubre",
  "noviembre",
  "diciembre",
] as const;

export const SPANISH_JANUARY = "enero";

const MONTH_NUMBERS: Record<string, number> = Object.fromEntries(SPANISH_MONTHS.map((m, i) => [m, i + 1]));

export function monthToNumber(month: string): number | undefined {
  return MONTH_NUMBERS[month.toLowerCase()];
}

const MONTH_ABBREVIATIONS: Record<string, string> = {
  ene: "enero",
  feb: "febrero",
  mar: "marzo",
  abr: "abril",
  may: "mayo",
  jun: "junio",
  jul: "julio",
  ago: "agosto",
  sep: "septiembre",
  oct: "octubre",
  nov: "noviembre",
  dic: "diciembre",
};

export function monthAbbreviationToNumber(abbreviation: string): number | undefined {
  const fullName = MONTH_ABBREVIATIONS[abbreviation.toLowerCase()];
  return fullName ? monthToNumber(fullName) : undefined;
}

export function monthNumberToName(month: number): string | undefined {
  return SPANISH_MONTHS[month - 1];
}

export function spanishDayOfWeek(day: number, month: number, year: number): string {
  // JS Date's getDay(): 0=Sunday..6=Saturday, matching SPANISH_DAYS' domingo-first order
  // once rotated — SPANISH_DAYS itself starts at lunes, so index accordingly.
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  const sundayFirst = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
  return sundayFirst[weekday];
}
