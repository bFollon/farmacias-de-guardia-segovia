import { parseWeeklyRotation, type PharmacyInfo, type WeeklyRotationOptions } from "./weeklyRotation.js";
import type { PharmacySchedule } from "../types.js";

/**
 * Ported from android's ElEspinarParser.kt — see weeklyRotation.ts for the shared base and
 * why this isn't a line-for-line port of the fold logic.
 *
 * The C/ MARQUES PERALES address below is corrected from the clients' current
 * "Calle del, C. Marqués de Perales, 2, 40400, Segovia" (garbled street fragment, missing
 * town name — found and diagnosed but not yet fixed on iOS/Android; flagged separately).
 * Building a new single source of truth is the point of this migration — porting a known,
 * already-diagnosed geocoding-breaking bug forward into it would defeat that.
 */
const PHARMACY_INFO: Record<string, PharmacyInfo> = {
  "AV. HONTANILLA 18": {
    name: "FARMACIA ANA MARÍA APARICIO HERNAN",
    address: "Av. Hontanilla, 18, 40400 El Espinar, Segovia",
    phone: "921 181 011",
  },
  "C/ MARQUES PERALES": {
    name: "Farmacia Lda M J. Bartolomé Sánchez",
    address: "C. Marqués de Perales, 2, 40400 El Espinar, Segovia",
    phone: "921 181 171",
  },
  "SAN RAFAEL": {
    name: "Farmacia San Rafael",
    address: "Tr.ª Alto del León, 19, 40410 San Rafael, Segovia",
    phone: "921 171 105",
  },
};

function identifyPharmacy(line: string): string | undefined {
  const normalized = line.toLowerCase();
  if (normalized.includes("hontanilla")) return "AV. HONTANILLA 18";
  if (normalized.includes("marques perales")) return "C/ MARQUES PERALES";
  if (normalized.endsWith("san rafael")) return "SAN RAFAEL";
  return undefined;
}

const OPTIONS: WeeklyRotationOptions = {
  regionLabel: "el-espinar",
  identifyPharmacy,
  pharmacyInfo: PHARMACY_INFO,
  dedupeDatesPerWeek: true,
};

export async function parseElEspinar(pages: string[][], pdfUrl?: string): Promise<PharmacySchedule[]> {
  return parseWeeklyRotation(pages, OPTIONS, pdfUrl);
}
