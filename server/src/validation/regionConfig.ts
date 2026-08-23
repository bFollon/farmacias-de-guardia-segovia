import type { RegionValidationConfig } from "./gate.js";

/** MVP validation thresholds per Features/parsing-validation-gate.md. Not yet tuned against
 * a long run of real historical PDFs — that's a separate, later step per that doc's status table. */
export const REGION_VALIDATION_CONFIG: Record<string, RegionValidationConfig> = {
  "segovia-capital": {
    minScheduleCount: 20,
    requiredShiftKeys: ["capitalDay", "capitalNight"],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
  },
  cuellar: {
    minScheduleCount: 20,
    requiredShiftKeys: ["fullDay"],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
  },
  "el-espinar": {
    minScheduleCount: 20,
    requiredShiftKeys: ["fullDay"],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
  },
  // Segovia Rural's 8 ZBS: which pharmacy (and therefore which shift key) is on duty
  // varies day to day, so requiredShiftKeys is left empty — there's no single key
  // guaranteed present on every date. la-sierra/fuentidueña/villacastin only cover
  // weekdays in the real PDF and riaza-sepulveda has a genuine end-of-calendar hole, so
  // date continuity is skipped for those (see segoviaRural.ts and its fixture test).
  "riaza-sepulveda": {
    minScheduleCount: 20,
    requiredShiftKeys: [],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
    skipDateContinuityCheck: true,
  },
  "la-granja": {
    minScheduleCount: 20,
    requiredShiftKeys: ["ruralExtendedDaytime"],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
  },
  "la-sierra": {
    minScheduleCount: 20,
    requiredShiftKeys: ["ruralDaytime"],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
    skipDateContinuityCheck: true,
  },
  "fuentidueña": {
    minScheduleCount: 20,
    requiredShiftKeys: ["ruralDaytime"],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
    skipDateContinuityCheck: true,
  },
  carbonero: {
    minScheduleCount: 20,
    requiredShiftKeys: ["ruralDaytime"],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
  },
  "navas-asuncion": {
    minScheduleCount: 20,
    requiredShiftKeys: ["ruralDaytime"],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
  },
  villacastin: {
    minScheduleCount: 20,
    requiredShiftKeys: [],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
    skipDateContinuityCheck: true,
  },
  cantalejo: {
    minScheduleCount: 20,
    requiredShiftKeys: ["ruralDaytime"],
    expectedCadenceDays: 1,
    maxDeltaFraction: 0.5,
  },
};
