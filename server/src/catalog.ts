import { readFile } from "node:fs/promises";
import { config } from "./config.js";
import type { LocationSchedule, Pharmacy } from "./types.js";

/** One hand-maintained row of catalog/pharmacies.json. `locationId` is informational (it
 * tells the editor which schedule file the pharmacy appears in) and isn't part of the match
 * key; coordinates stay null until someone fills them in. */
export interface CatalogEntry {
  name: string;
  address: string;
  locationId: string;
  latitude: number | null;
  longitude: number | null;
  /** Input for `npm run resolve-plus-codes`, which turns it into latitude/longitude and
   * removes the key. The server never reads this field. */
  plusCode?: string | null;
}

export interface Coordinates {
  latitude: number;
  longitude: number;
}

/** Province of Segovia, with margin. Hand-typed coordinates outside this box are almost
 * always a typo (swapped or sign-flipped lat/lng), so they're dropped instead of shipped
 * to the apps as a pin in the wrong country. */
const SEGOVIA_BOUNDS = { minLat: 40.5, maxLat: 41.7, minLng: -4.8, maxLng: -3.1 };

export function isInsideSegoviaBounds(latitude: number, longitude: number): boolean {
  return (
    Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= SEGOVIA_BOUNDS.minLat &&
    latitude <= SEGOVIA_BOUNDS.maxLat &&
    longitude >= SEGOVIA_BOUNDS.minLng &&
    longitude <= SEGOVIA_BOUNDS.maxLng
  );
}

/** The PDFs give names/addresses in upper case with stray whitespace and occasional
 * accent differences, so lookups go through a normalized form rather than raw text. */
function normalize(text: string): string {
  return text
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

export function pharmacyKey(name: string, address: string): string {
  return `${normalize(name)}|${normalize(address)}`;
}

export function buildCoordinateMap(entries: CatalogEntry[]): { coordinates: Map<string, Coordinates>; warnings: string[] } {
  const coordinates = new Map<string, Coordinates>();
  const warnings: string[] = [];

  for (const entry of entries) {
    const label = `${entry.name} (${entry.address})`;
    if (entry.latitude === null || entry.longitude === null) continue;

    const { latitude, longitude } = entry;
    if (!isInsideSegoviaBounds(latitude, longitude)) {
      warnings.push(`Ignoring coordinates outside the province of Segovia: ${label} -> ${latitude}, ${longitude}`);
      continue;
    }

    const key = pharmacyKey(entry.name, entry.address);
    if (coordinates.has(key)) {
      warnings.push(`Duplicate catalog entry, keeping the first: ${label}`);
      continue;
    }
    coordinates.set(key, { latitude, longitude });
  }

  return { coordinates, warnings };
}

/** Re-read on every publish so a catalog edit never needs a server restart. A missing
 * file just means no coordinates yet — schedules still publish. */
export async function loadCoordinateMap(): Promise<{ coordinates: Map<string, Coordinates>; warnings: string[] }> {
  try {
    const raw = await readFile(config.catalogPath, "utf-8");
    return buildCoordinateMap(JSON.parse(raw) as CatalogEntry[]);
  } catch (err) {
    if ((err as NodeJS.ErrnoException).code === "ENOENT") {
      return { coordinates: new Map(), warnings: [`Pharmacy catalog not found at ${config.catalogPath}`] };
    }
    throw err;
  }
}

function withCoordinates(pharmacy: Pharmacy, coordinates: Map<string, Coordinates>): Pharmacy {
  // Strip first so a pharmacy removed from the catalog loses stale coordinates on re-apply.
  const { latitude: _latitude, longitude: _longitude, ...base } = pharmacy;
  const found = coordinates.get(pharmacyKey(pharmacy.name, pharmacy.address));
  return found ? { ...base, latitude: found.latitude, longitude: found.longitude } : base;
}

/** Returns a copy of `location` with catalog coordinates merged into every pharmacy entry,
 * plus the distinct "name | address" of pharmacies the catalog has no coordinates for. */
export function applyCatalog(
  location: LocationSchedule,
  coordinates: Map<string, Coordinates>,
): { location: LocationSchedule; uncataloged: string[] } {
  const uncataloged = new Set<string>();

  const schedules = location.schedules.map((schedule) => ({
    ...schedule,
    shifts: Object.fromEntries(
      Object.entries(schedule.shifts).map(([shift, pharmacies]) => [
        shift,
        pharmacies.map((pharmacy) => {
          const enriched = withCoordinates(pharmacy, coordinates);
          if (enriched.latitude === undefined) uncataloged.add(`${pharmacy.name.trim()} | ${pharmacy.address.trim()}`);
          return enriched;
        }),
      ]),
    ),
  }));

  return { location: { ...location, schedules }, uncataloged: [...uncataloged].sort() };
}
