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

import type { RefreshOutcome, RegionId } from "./types.js";

// Deliberate deviation from InterSego's monitor (see Features/pdf-change-monitor.md
// "Trigger, don't just alert"): the data service can auto-parse, so a detected change
// should trigger POST /api/refresh/:locationId immediately rather than only emailing
// "go do something manually."
//
// Segovia Rural has no locationId of its own (one PDF backs its 8 ZBS) - refreshing any
// one of its ZBS ids re-parses and republishes all 8 together, per server/src/routes/admin.ts.
const REFRESH_LOCATION_ID: Record<RegionId, string> = {
  "segovia-capital": "segovia-capital",
  cuellar: "cuellar",
  "el-espinar": "el-espinar",
  "segovia-rural": "riaza-sepulveda",
};

export async function triggerRefresh(regionId: RegionId): Promise<RefreshOutcome> {
  const locationId = REFRESH_LOCATION_ID[regionId];
  const baseUrl = process.env.DATA_SERVICE_URL ?? "http://localhost:3765";

  const response = await fetch(`${baseUrl}/api/refresh/${locationId}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${process.env.DATA_SERVICE_RELOAD_KEY}` },
  });

  const body = await response.json().catch(() => null);
  return { locationId, status: response.status, body };
}
