import { test } from "node:test";
import assert from "node:assert/strict";
import { validateSchedules, type RegionValidationConfig } from "./gate.js";
import type { LocationSchedule, Pharmacy, PharmacySchedule } from "../types.js";

const CONFIG: RegionValidationConfig = {
  minScheduleCount: 3,
  requiredShiftKeys: ["capitalDay", "capitalNight"],
  expectedCadenceDays: 1,
  maxDeltaFraction: 0.5,
};

function pharmacy(overrides: Partial<Pharmacy> = {}): Pharmacy {
  return {
    id: "id",
    name: "FARMACIA EXAMPLE",
    address: "CALLE EXAMPLE, 1",
    phone: "921 123456",
    additionalInfo: null,
    ...overrides,
  };
}

function schedule(day: number, month = "mayo", year = 2026, shifts: PharmacySchedule["shifts"] = {}): PharmacySchedule {
  return {
    date: { dayOfWeek: "lunes", day, month, year },
    shifts: {
      capitalDay: [pharmacy()],
      capitalNight: [pharmacy()],
      ...shifts,
    },
  };
}

function daily(count: number): PharmacySchedule[] {
  return Array.from({ length: count }, (_, i) => schedule(i + 1));
}

test("valid daily schedules pass every check", () => {
  const result = validateSchedules(daily(5), CONFIG, undefined);
  assert.deepEqual(result, { passed: true, failures: [] });
});

test("fails closed when too few schedules were parsed", () => {
  const result = validateSchedules(daily(2), CONFIG, undefined);
  assert.equal(result.passed, false);
  assert.match(result.failures[0], /Non-empty/);
});

test("fails closed on a missing shift", () => {
  const schedules = daily(4);
  schedules[2] = schedule(3, "mayo", 2026, { capitalDay: [pharmacy()], capitalNight: [] });
  const result = validateSchedules(schedules, CONFIG, undefined);
  assert.equal(result.passed, false);
  assert.ok(result.failures.some((f) => f.includes("Shift completeness")));
});

test("fails closed on a malformed phone number", () => {
  const schedules = daily(4);
  schedules[1] = schedule(2, "mayo", 2026, { capitalDay: [pharmacy({ phone: "not-a-phone" })] });
  const result = validateSchedules(schedules, CONFIG, undefined);
  assert.equal(result.passed, false);
  assert.ok(result.failures.some((f) => f.includes("Pharmacy shape sanity")));
});

test("fails closed on an empty name", () => {
  const schedules = daily(4);
  schedules[0] = schedule(1, "mayo", 2026, { capitalNight: [pharmacy({ name: "" })] });
  const result = validateSchedules(schedules, CONFIG, undefined);
  assert.equal(result.passed, false);
  assert.ok(result.failures.some((f) => f.includes("Pharmacy shape sanity")));
});

test("fails closed on a date gap (regression guard for the Martín Cantero bug)", () => {
  // Days 1, 2, 4, 5 — day 3 silently missing, exactly the shape of the bug fixed in 593e516.
  const schedules = [schedule(1), schedule(2), schedule(4), schedule(5)];
  const result = validateSchedules(schedules, CONFIG, undefined);
  assert.equal(result.passed, false);
  assert.ok(result.failures.some((f) => f.includes("Date continuity")));
});

test("passes when there is no previous version to delta against (first publish)", () => {
  const result = validateSchedules(daily(5), CONFIG, undefined);
  assert.equal(result.passed, true);
});

test("fails closed when schedule count swings more than the delta bound vs. the previous version", () => {
  const previous: LocationSchedule = {
    locationId: "segovia-capital",
    regionId: "segovia-capital",
    sourcePdfUrl: "https://example.com/prev.pdf",
    sourcePdfSha256: "prev",
    parsedAt: new Date().toISOString(),
    version: 1,
    schedules: daily(20),
  };
  const result = validateSchedules(daily(5), CONFIG, previous);
  assert.equal(result.passed, false);
  assert.ok(result.failures.some((f) => f.includes("Delta bound")));
});

test("passes when the change vs. the previous version is within the delta bound", () => {
  const previous: LocationSchedule = {
    locationId: "segovia-capital",
    regionId: "segovia-capital",
    sourcePdfUrl: "https://example.com/prev.pdf",
    sourcePdfSha256: "prev",
    parsedAt: new Date().toISOString(),
    version: 1,
    schedules: daily(10),
  };
  const result = validateSchedules(daily(12), CONFIG, previous);
  assert.equal(result.passed, true);
});
