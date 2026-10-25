/**
 * Turns Plus Codes typed into catalog/pharmacies.json into latitude/longitude, in place.
 *
 * Fill in an entry's `"plusCode"` (copied from Google Maps), run this, and the entry gets
 * its `latitude`/`longitude` and the `plusCode` key is removed. Entries that fail to
 * resolve are left untouched so you can fix them and re-run.
 *
 * Usage:
 *   npm run resolve-plus-codes [path/to/pharmacies.json]
 *
 * Accepts full codes ("8CGQVQ5M+8F") and short codes ("VQ5M+8F", with or without the town
 * Google Maps appends). A short code omits its first four digits, which fit any 1-degree
 * cell (~100 km), so they're recovered against the rough centre of the entry's locationId.
 * Those centres only need to be within ~0.5 degrees of every pharmacy in the location.
 */
import { readFile, writeFile } from "node:fs/promises";
import { OpenLocationCode } from "open-location-code";
import { isInsideSegoviaBounds, type CatalogEntry } from "../src/catalog.js";
import { config } from "../src/config.js";

const REFERENCE_POINTS: Record<string, { latitude: number; longitude: number }> = {
  "segovia-capital": { latitude: 40.943, longitude: -4.109 },
  cuellar: { latitude: 41.4, longitude: -4.31 },
  "el-espinar": { latitude: 40.72, longitude: -4.24 },
  "riaza-sepulveda": { latitude: 41.4, longitude: -3.5 },
  "la-granja": { latitude: 40.9, longitude: -4.01 },
  "la-sierra": { latitude: 41.1, longitude: -3.8 },
  fuentiduena: { latitude: 41.4, longitude: -4.0 },
  carbonero: { latitude: 41.15, longitude: -4.2 },
  "navas-asuncion": { latitude: 41.1, longitude: -4.45 },
  villacastin: { latitude: 40.82, longitude: -4.4 },
  cantalejo: { latitude: 41.25, longitude: -3.92 },
};

const olc = new OpenLocationCode();

function resolve(entry: CatalogEntry, input: string): { latitude: number; longitude: number } {
  // Google Maps copies "VQ5M+8F Segovia, Spain"; only the first token is the code.
  let code = input.trim().split(/\s+/)[0]!.toUpperCase();

  if (!olc.isValid(code)) throw new Error(`"${code}" is not a valid Plus Code`);

  if (olc.isShort(code)) {
    const reference = REFERENCE_POINTS[entry.locationId];
    if (!reference) throw new Error(`short code but no reference point for locationId "${entry.locationId}"`);
    code = olc.recoverNearest(code, reference.latitude, reference.longitude);
  }

  const area = olc.decode(code);
  const latitude = Number(area.latitudeCenter.toFixed(6));
  const longitude = Number(area.longitudeCenter.toFixed(6));

  if (!isInsideSegoviaBounds(latitude, longitude)) {
    throw new Error(`${latitude}, ${longitude} is outside the province of Segovia`);
  }
  return { latitude, longitude };
}

async function main(): Promise<void> {
  const catalogPath = process.argv[2] ?? config.catalogPath;
  const entries = JSON.parse(await readFile(catalogPath, "utf-8")) as CatalogEntry[];

  let resolved = 0;
  let failed = 0;

  for (const entry of entries) {
    const input = entry.plusCode?.trim();
    if (!input) continue;

    try {
      const { latitude, longitude } = resolve(entry, input);
      entry.latitude = latitude;
      entry.longitude = longitude;
      delete entry.plusCode;
      resolved++;
      console.log(`✓ ${entry.name}: ${latitude}, ${longitude}  https://www.google.com/maps?q=${latitude},${longitude}`);
    } catch (err) {
      failed++;
      console.error(`✗ ${entry.name} (${input}): ${(err as Error).message}`);
    }
  }

  // Also drop the empty placeholder from entries that already have coordinates (e.g. typed
  // in by hand), so a finished catalog has no plusCode keys left at all.
  let cleaned = 0;
  for (const entry of entries) {
    if (entry.latitude !== null && entry.longitude !== null && "plusCode" in entry && !entry.plusCode?.trim()) {
      delete entry.plusCode;
      cleaned++;
    }
  }

  if (resolved > 0 || cleaned > 0) await writeFile(catalogPath, `${JSON.stringify(entries, null, 2)}\n`, "utf-8");

  const missing = entries.filter((entry) => entry.latitude === null || entry.longitude === null).length;
  console.log(`\n${resolved} resolved, ${failed} failed, ${missing} of ${entries.length} entries still without coordinates.`);
  if (failed > 0) process.exit(1);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
