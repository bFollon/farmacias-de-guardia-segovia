import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { config } from "./config.js";
import { isLocationId, type LocationSchedule } from "./types.js";

/**
 * In-memory cache of data/schedules/*.json, matching backend-data-service.md's
 * "flat JSON files, reload without restart" design. No database — data volume is tiny.
 */
class ScheduleStore {
  private byLocationId = new Map<string, LocationSchedule>();

  /** Re-reads every *.json file in the data dir, replacing the in-memory cache atomically. */
  async reload(): Promise<{ loaded: number; skipped: string[] }> {
    const next = new Map<string, LocationSchedule>();
    const skipped: string[] = [];

    let entries: string[] = [];
    try {
      entries = await readdir(config.dataDir);
    } catch (err) {
      if ((err as NodeJS.ErrnoException).code === "ENOENT") {
        this.byLocationId = next;
        return { loaded: 0, skipped };
      }
      throw err;
    }

    for (const entry of entries) {
      if (!entry.endsWith(".json")) continue;

      const filePath = path.join(config.dataDir, entry);
      try {
        const raw = await readFile(filePath, "utf-8");
        const parsed = JSON.parse(raw) as LocationSchedule;

        if (!isLocationId(parsed.locationId)) {
          skipped.push(entry);
          continue;
        }

        next.set(parsed.locationId, parsed);
      } catch {
        skipped.push(entry);
      }
    }

    this.byLocationId = next;
    return { loaded: next.size, skipped };
  }

  getManifest(): { locationId: string; version: number }[] {
    return [...this.byLocationId.values()]
      .map(({ locationId, version }) => ({ locationId, version }))
      .sort((a, b) => a.locationId.localeCompare(b.locationId));
  }

  get(locationId: string): LocationSchedule | undefined {
    return this.byLocationId.get(locationId);
  }

  /** ETag for conditional GETs — content hash, not just version, so a manual data edit still invalidates client caches. */
  etagFor(locationId: string): string | undefined {
    const location = this.byLocationId.get(locationId);
    if (!location) return undefined;
    return `"${createHash("sha256").update(JSON.stringify(location)).digest("hex").slice(0, 16)}"`;
  }
}

export const scheduleStore = new ScheduleStore();
