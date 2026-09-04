/** One PDF per region (Segovia Rural's one PDF backs all 8 ZBS). */
export const REGION_IDS = ["segovia-capital", "cuellar", "el-espinar", "segovia-rural"] as const;
export type RegionId = (typeof REGION_IDS)[number];

// Ported from pdf-change-monitor/src/scraper/index.ts (itself ported from
// ios/FarmaciasDeGuardiaEnSegovia/Services/PDFURLScrapingService.swift) - same listing page,
// same regex/keyword approach, mapped to this service's regionIds.
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

let cachedUrls: Map<RegionId, string> | null = null;

/**
 * Resolves the current source PDF URL for a region: tries a fresh scrape first, falls back
 * to the last successful scrape (this process only), and finally to the caller-supplied
 * static URL if scraping has never succeeded or doesn't cover this region. Refresh calls
 * happen rarely (only on a detected change), so re-scraping each time is cheap and keeps
 * this resilient to cofsegovia.com's URLs rotating (they encode year/month in the path).
 */
export async function resolvePdfUrl(regionId: RegionId, staticFallback: string): Promise<string> {
  try {
    cachedUrls = await scrapePDFURLs();
  } catch {
    // Scraping failed this time - fall through to the last successful scrape, if any.
  }

  return cachedUrls?.get(regionId) ?? staticFallback;
}
