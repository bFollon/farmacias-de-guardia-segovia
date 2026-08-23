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

/** One PDF per region (Segovia Rural's one PDF backs all 8 ZBS, hashed once here). */
export const REGION_IDS = ["segovia-capital", "cuellar", "el-espinar", "segovia-rural"] as const;
export type RegionId = (typeof REGION_IDS)[number];

export interface RegionCheckState {
  regionId: RegionId;
  url: string;
  sha256: string;
  lastChecked: string;
  lastChanged: string | null;
}

export interface RefreshOutcome {
  /** locationId(s) refresh was attempted against - one for town regions, one representative ZBS id for segovia-rural (refreshing any of its 8 ZBS ids refreshes all 8, per admin.ts). */
  locationId: string;
  status: number;
  body: unknown;
}

export interface ChangeDetail {
  regionId: RegionId;
  previousUrl: string | null;
  currentUrl: string;
  urlChanged: boolean;
  previousSha256: string | null;
  currentSha256: string;
  refresh: RefreshOutcome;
}

export interface DbSchema {
  regions: RegionCheckState[];
}
