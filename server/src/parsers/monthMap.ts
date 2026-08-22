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
