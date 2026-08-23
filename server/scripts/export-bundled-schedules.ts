/**
 * Exports the current server-published schedule for every location into both clients'
 * app-bundle directories, for day-zero offline use (see Features/client-offline-sync.md's
 * "Bundled day-zero JSON" section). This is a manual, pre-release step - not wired into CI,
 * matching this project's scope. Run it, review the diff, commit the results, then ship.
 *
 * Usage:
 *   API_KEY=<schedule API key> tsx scripts/export-bundled-schedules.ts [baseUrl]
 *
 * baseUrl defaults to http://homeserver.local:3765 (only reachable on the home LAN).
 */
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { LOCATION_IDS } from "../src/types.js";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "../.."); // server/scripts -> server -> repo root

const apiKey = process.env.API_KEY;
if (!apiKey) {
  console.error("API_KEY environment variable is required (the client-facing schedule API key, not RELOAD_KEY).");
  process.exit(1);
}

const baseUrl = process.argv[2] ?? "http://homeserver.local:3765";

const iosDir = path.join(repoRoot, "ios/FarmaciasDeGuardiaEnSegovia/BundledSchedules");
const androidDir = path.join(repoRoot, "android/app/src/main/assets/bundled_schedules");

async function main(): Promise<void> {
  await mkdir(iosDir, { recursive: true });
  await mkdir(androidDir, { recursive: true });

  let failures = 0;

  for (const locationId of LOCATION_IDS) {
    const url = `${baseUrl}/api/schedules/${locationId}`;
    const response = await fetch(url, { headers: { Authorization: `Bearer ${apiKey}` } });

    if (!response.ok) {
      console.error(`✗ ${locationId}: ${response.status} ${response.statusText}`);
      failures++;
      continue;
    }

    // Re-serialize (rather than piping the raw body through) so the committed files have
    // stable, readable formatting and a trailing newline.
    const body = await response.json();
    const formatted = `${JSON.stringify(body, null, 2)}\n`;

    await writeFile(path.join(iosDir, `${locationId}.json`), formatted, "utf-8");
    await writeFile(path.join(androidDir, `${locationId}.json`), formatted, "utf-8");

    console.log(`✓ ${locationId} (version ${body.version}, ${body.schedules?.length ?? 0} schedules)`);
  }

  if (failures > 0) {
    console.error(`\n${failures} location(s) failed to export - see above. Review before committing.`);
    process.exit(1);
  }

  console.log(`\nExported ${LOCATION_IDS.length} locations to:\n  ${iosDir}\n  ${androidDir}`);
}

main().catch((error) => {
  console.error("export-bundled-schedules failed:", error);
  process.exit(1);
});
