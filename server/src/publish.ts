import { writeFile } from "node:fs/promises";
import path from "node:path";
import { config } from "./config.js";
import { scheduleStore } from "./store.js";
import type { LocationSchedule } from "./types.js";

/** Writes a location's schedule JSON to disk and reloads the in-memory store, per
 * backend-data-service.md's "reload without restart" design. */
export async function publishLocationSchedule(location: LocationSchedule): Promise<void> {
  const filePath = path.join(config.dataDir, `${location.locationId}.json`);
  await writeFile(filePath, JSON.stringify(location, null, 2), "utf-8");
  await scheduleStore.reload();
}
