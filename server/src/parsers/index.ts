import { extractPdfPageLines } from "../pdf/extractPageLines.js";
import { parseSegoviaCapital } from "./segoviaCapital.js";
import { parseCuellar } from "./cuellar.js";
import { parseElEspinar } from "./elEspinar.js";
import type { PharmacySchedule } from "../types.js";

/**
 * Strategy Pattern registry, carried over from the clients per
 * Features/parser-port-typescript.md. segovia-rural's 8 ZBS land in a later Phase 2 step
 * (most complex — 8-ZBS subdivision, shared/separate schedules per project CLAUDE.md).
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
  cuellar: {
    regionId: "cuellar",
    sourcePdfUrl: "https://cofsegovia.com/wp-content/uploads/2026/01/GUARDIAS-CUELLAR_2026.pdf",
    parse: async (pdfBytes, pdfUrl) => {
      const pages = await extractPdfPageLines(pdfBytes);
      return parseCuellar(pages, pdfUrl);
    },
  },
  "el-espinar": {
    regionId: "el-espinar",
    sourcePdfUrl: "https://cofsegovia.com/wp-content/uploads/2026/01/Guardias-EL-ESPINAR_2026.pdf",
    parse: async (pdfBytes, pdfUrl) => {
      const pages = await extractPdfPageLines(pdfBytes);
      return parseElEspinar(pages, pdfUrl);
    },
  },
};
