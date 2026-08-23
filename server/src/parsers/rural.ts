import { extractPdfPageLines } from "../pdf/extractPageLines.js";
import { parseSegoviaRural } from "./segoviaRural.js";
import type { PharmacySchedule } from "../types.js";

/**
 * Segovia Rural is one PDF backing 8 separate location JSON files (the 8 ZBS) — a
 * dedicated shape from the single-region REGION_PARSERS registry, per
 * backend-data-service.md's MVP note ("segovia-rural itself has no direct schedule — it
 * only owns the source PDF that all 8 ZBS files are derived from") and
 * pdf-change-monitor.md's edge case ("a single PDF change triggers 8 republishes").
 */
export const RURAL_ZBS_IDS = [
  "riaza-sepulveda",
  "la-granja",
  "la-sierra",
  "fuentidueña",
  "carbonero",
  "navas-asuncion",
  "villacastin",
  "cantalejo",
] as const;

export const RURAL_SOURCE_PDF_URL =
  "https://cofsegovia.com/wp-content/uploads/2026/08/SERVICIOS-DE-URGENCIA-RURALES-2026.pdf";

export function isRuralZbsId(locationId: string): locationId is (typeof RURAL_ZBS_IDS)[number] {
  return (RURAL_ZBS_IDS as readonly string[]).includes(locationId);
}

export async function parseAllRuralZbs(pdfBytes: Uint8Array, pdfUrl?: string): Promise<Record<string, PharmacySchedule[]>> {
  const pages = await extractPdfPageLines(pdfBytes);
  return parseSegoviaRural(pages, pdfUrl);
}
