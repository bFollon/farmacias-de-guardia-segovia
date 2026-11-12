import { writeFile } from "node:fs/promises";
import path from "node:path";
import { config } from "./config.js";
import { applyCatalog, loadCoordinateMap } from "./catalog.js";
import { scheduleStore } from "./store.js";
import type { LocationSchedule } from "./types.js";

/** Writes a location's schedule JSON to disk and reloads the in-memory store, per
 * backend-data-service.md's "reload without restart" design. Pharmacy coordinates from the
 * catalog are merged in on the way out, so the files on disk are exactly what clients get.
 * Returns the pharmacies the catalog has no coordinates for, so callers can surface them. */
export async function publishLocationSchedule(location: LocationSchedule): Promise<{ uncataloged: string[] }> {
  const { coordinates, warnings } = await loadCoordinateMap();
  for (const warning of warnings) console.warn(`[catalog] ${warning}`);

  const enriched = applyCatalog(location, coordinates);
  if (enriched.uncataloged.length > 0) {
    console.warn(`[catalog] ${location.locationId}: no coordinates for ${enriched.uncataloged.join("; ")}`);
  }

  const filePath = path.join(config.dataDir, `${location.locationId}.json`);
  await writeFile(filePath, JSON.stringify(enriched.location, null, 2), "utf-8");
  await scheduleStore.reload();
  return { uncataloged: enriched.uncataloged };
}

/** Re-applies the current catalog to every already-published location, republishing (with a
 * version bump, so clients refetch) only those whose output actually changes. This is how a
 * coordinate edit reaches the apps when the source PDFs haven't changed. */
export async function applyCatalogToPublished(): Promise<{
  updated: Record<string, number>;
  uncataloged: Record<string, string[]>;
}> {
  const { coordinates, warnings } = await loadCoordinateMap();
  for (const warning of warnings) console.warn(`[catalog] ${warning}`);

  const updated: Record<string, number> = {};
  const uncataloged: Record<string, string[]> = {};

  for (const { locationId } of scheduleStore.getManifest()) {
    const current = scheduleStore.get(locationId)!;
    const next = applyCatalog(current, coordinates);
    if (next.uncataloged.length > 0) uncataloged[locationId] = next.uncataloged;
    if (JSON.stringify(next.location) === JSON.stringify(current)) continue;

    const version = current.version + 1;
    await publishLocationSchedule({ ...current, version });
    updated[locationId] = version;
  }

  return { updated, uncataloged };
}
