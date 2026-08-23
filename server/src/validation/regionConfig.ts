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
};
