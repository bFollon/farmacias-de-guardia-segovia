// PDF Change Monitor
// Copyright (C) 2026 Bruno Follon (@bFollon)
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.

import { createHash } from "crypto";
import type { FastifyBaseLogger } from "fastify";
import { getRegionStates, upsertRegionStates } from "../db/index.js";
import { scrapePDFURLs } from "../scraper/index.js";
import { sendChangeNotification } from "../notifier/index.js";
import { triggerRefresh } from "../refreshTrigger.js";
import { REGION_IDS, type ChangeDetail, type RegionCheckState, type RegionId } from "../types.js";

// Static fallbacks, mirroring server/src/parsers/{index,rural}.ts - used only if scraping
// fails to find a URL for a region (e.g. page structure change), so a check still runs.
const FALLBACK_URLS: Record<RegionId, string> = {
  "segovia-capital": "https://cofsegovia.com/wp-content/uploads/2026/06/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-2026.pdf",
  cuellar: "https://cofsegovia.com/wp-content/uploads/2026/01/GUARDIAS-CUELLAR_2026.pdf",
  "el-espinar": "https://cofsegovia.com/wp-content/uploads/2026/01/Guardias-EL-ESPINAR_2026.pdf",
  "segovia-rural": "https://cofsegovia.com/wp-content/uploads/2026/08/SERVICIOS-DE-URGENCIA-RURALES-2026.pdf",
};

let checkInProgress = false;

async function downloadAndHash(url: string): Promise<string> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const buffer = await response.arrayBuffer();
  return createHash("sha256").update(Buffer.from(buffer)).digest("hex");
}

export async function runCheck(log: FastifyBaseLogger): Promise<void> {
  if (checkInProgress) {
    log.info("PDF check already in progress, skipping");
    return;
  }
  checkInProgress = true;

  try {
    log.info("Starting PDF check");

    let scrapedUrls: Map<RegionId, string>;
    try {
      scrapedUrls = await scrapePDFURLs();
      log.info(`Scraped ${scrapedUrls.size} PDF URLs`);
    } catch (err) {
      log.error(`Scraping failed, falling back to static URLs: ${err}`);
      scrapedUrls = new Map();
    }

    const existingStates = await getRegionStates();
    const stateMap = new Map(existingStates.map((s) => [s.regionId, s]));

    const updatedStates: RegionCheckState[] = [];
    const changes: ChangeDetail[] = [];
    const now = new Date().toISOString();

    for (const regionId of REGION_IDS) {
      const currentUrl = scrapedUrls.get(regionId) ?? FALLBACK_URLS[regionId];
      const existing = stateMap.get(regionId) ?? null;

      let sha256: string;
      try {
        sha256 = await downloadAndHash(currentUrl);
      } catch (err) {
        log.error(`${regionId}: download failed: ${err}`);
        continue;
      }

      const isNew = existing === null;
      const urlChanged = !isNew && existing.url !== currentUrl;
      const hashChanged = !isNew && existing.sha256 !== sha256;
      const changed = urlChanged || hashChanged;

      updatedStates.push({
        regionId,
        url: currentUrl,
        sha256,
        lastChecked: now,
        lastChanged: changed ? now : (existing?.lastChanged ?? null),
      });

      if (isNew) {
        log.info(`${regionId}: baseline established`);
        continue;
      }

      if (!changed) {
        log.info(`${regionId}: unchanged`);
        continue;
      }

      log.info(`${regionId}: CHANGED (url=${urlChanged}, hash=${hashChanged}) - triggering refresh`);

      let refresh;
      try {
        refresh = await triggerRefresh(regionId);
        log.info(`${regionId}: refresh returned HTTP ${refresh.status}`);
      } catch (err) {
        log.error(`${regionId}: refresh trigger failed: ${err}`);
        refresh = { locationId: regionId, status: 0, body: { error: String(err) } };
      }

      changes.push({
        regionId,
        previousUrl: existing.url,
        currentUrl,
        urlChanged,
        previousSha256: existing.sha256,
        currentSha256: sha256,
        refresh,
      });
    }

    if (updatedStates.length > 0) {
      await upsertRegionStates(updatedStates);
      log.info(`Persisted state for ${updatedStates.length} regions`);
    }

    if (changes.length > 0) {
      try {
        await sendChangeNotification(changes);
        log.info(`Change notification sent for: ${changes.map((c) => c.regionId).join(", ")}`);
      } catch (err) {
        log.error(`Failed to send change notification: ${err}`);
      }
    } else {
      log.info("No changes detected");
    }
  } finally {
    checkInProgress = false;
  }
}
