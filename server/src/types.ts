/**
 * Wire shapes shared with the iOS/Android clients.
 * Mirrors PharmacySchedule/DutyDate/Pharmacy in ios/FarmaciasDeGuardiaEnSegovia/Models/
 * and the JSON schema in ../../Features/backend-data-service.md.
 */

export interface DutyDate {
  dayOfWeek: string;
  day: number;
  month: string;
  year: number;
}

export interface Pharmacy {
  id: string;
  name: string;
  address: string;
  phone: string;
  additionalInfo: string | null;
}

/** Keyed by DutyTimeSpan name, e.g. "capitalDay", "capitalNight", "fullDay". */
export type Shifts = Record<string, Pharmacy[]>;

export interface PharmacySchedule {
  date: DutyDate;
  shifts: Shifts;
}

export interface LocationSchedule {
  locationId: string;
  regionId: string;
  sourcePdfUrl: string;
  sourcePdfSha256: string;
  parsedAt: string;
  version: number;
  schedules: PharmacySchedule[];
}

/** The 11 location ids served by this API. `segovia-rural` itself has no direct schedule file. */
export const LOCATION_IDS = [
  "segovia-capital",
  "cuellar",
  "el-espinar",
  "riaza-sepulveda",
  "la-granja",
  "la-sierra",
  "fuentidueña",
  "carbonero",
  "navas-asuncion",
  "villacastin",
  "cantalejo",
] as const;

export type LocationId = (typeof LOCATION_IDS)[number];

export function isLocationId(value: string): value is LocationId {
  return (LOCATION_IDS as readonly string[]).includes(value);
}
