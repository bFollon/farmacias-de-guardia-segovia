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

import type { RegionId } from "../types.js";

// Ported from ios/FarmaciasDeGuardiaEnSegovia/Services/PDFURLScrapingService.swift -
// same page, same regex approach, just mapped to server regionIds instead of display names.
const LISTING_PAGE_URL = "https://cofsegovia.com/farmacias-de-guardia/";

function determineRegionId(pdfUrl: string, context: string): RegionId | null {
  const urlLower = pdfUrl.toLowerCase();

  if (urlLower.includes("segovia") && urlLower.includes("capital")) return "segovia-capital";
  if (urlLower.includes("cuellar") || urlLower.includes("cuéllar")) return "cuellar";
  if (urlLower.includes("espinar")) return "el-espinar";
  if (urlLower.includes("rural")) return "segovia-rural";

  const contextLower = context.toLowerCase();
  const keywords: [string, RegionId][] = [
    ["segovia", "segovia-capital"],
    ["capital", "segovia-capital"],
    ["cuellar", "cuellar"],
    ["cuéllar", "cuellar"],
    ["espinar", "el-espinar"],
    ["san rafael", "el-espinar"],
    ["rural", "segovia-rural"],
  ];
  for (const [keyword, regionId] of keywords) {
    if (contextLower.includes(keyword)) return regionId;
  }

  return null;
}

/** Scrapes cofsegovia.com's stable listing page for the current PDF URL per region. */
export async function scrapePDFURLs(): Promise<Map<RegionId, string>> {
  const response = await fetch(LISTING_PAGE_URL);
  if (!response.ok) {
    throw new Error(`cofsegovia.com listing page returned HTTP ${response.status}`);
  }

  const html = await response.text();
  const urls = new Map<RegionId, string>();

  for (const match of html.matchAll(/href="([^"]*\.pdf)"/gi)) {
    const href = match[1];
    const absoluteUrl = href.startsWith("http") ? href : `https://cofsegovia.com${href}`;

    const index = html.indexOf(href);
    const context = index >= 0 ? html.slice(Math.max(0, index - 200), index + href.length + 200) : "";

    const regionId = determineRegionId(absoluteUrl, context);
    if (regionId && !urls.has(regionId)) {
      urls.set(regionId, absoluteUrl);
    }
  }

  return urls;
}
