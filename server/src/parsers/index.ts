import { extractPdfPageLines } from "../pdf/extractPageLines.js";
import { parseSegoviaCapital } from "./segoviaCapital.js";
import type { PharmacySchedule } from "../types.js";

/**
 * Strategy Pattern registry, carried over from the clients per
 * Features/parser-port-typescript.md. Only segovia-capital is ported so far
 * (Phase 1, step 2) — the rest (cuellar, el-espinar, segovia-rural's 8 ZBS)
 * land in Phase 2.
 */
export interface RegionParser {
  regionId: string;
  sourcePdfUrl: string;
  parse: (pdfBytes: Uint8Array, pdfUrl?: string) => Promise<PharmacySchedule[]>;
}

export const REGION_PARSERS: Record<string, RegionParser> = {
  "segovia-capital": {
    regionId: "segovia-capital",
    sourcePdfUrl: "https://cofsegovia.com/wp-content/uploads/2026/06/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-2026.pdf",
    parse: async (pdfBytes, pdfUrl) => {
      const pages = await extractPdfPageLines(pdfBytes);
      return parseSegoviaCapital(pages, pdfUrl);
    },
  },
};
