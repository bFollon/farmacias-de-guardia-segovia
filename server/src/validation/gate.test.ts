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

test("fails closed when schedule count swings more than the delta bound within the same date window", () => {
  // Same overall span (day 1-20) as the previous version, so this isn't explained by the
  // source PDF's date range narrowing — it's a genuine hole in an otherwise-unchanged window.
  const previous: LocationSchedule = {
    locationId: "segovia-capital",
    regionId: "segovia-capital",
    sourcePdfUrl: "https://example.com/prev.pdf",
    sourcePdfSha256: "prev",
    parsedAt: new Date().toISOString(),
    version: 1,
    schedules: daily(20),
  };
  const sparse = [1, 5, 10, 15, 20].map((day) => schedule(day));
  const result = validateSchedules(sparse, CONFIG, previous);
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

test("passes when the source PDF's date window legitimately narrows (rolling window vs. full year)", () => {
  // Previous covers a full year (Jan-Dec); new parse only covers the last 20 days of it.
  // Raw counts would swing ~94% (365 -> 20), well past the 50% bound, but every day the
  // new parse claims to cover is actually present, so it must not be flagged.
  const previous: LocationSchedule = {
    locationId: "segovia-rural",
    regionId: "segovia-rural",
    sourcePdfUrl: "https://example.com/prev.pdf",
    sourcePdfSha256: "prev",
    parsedAt: new Date().toISOString(),
    version: 1,
    schedules: Array.from({ length: 365 }, (_, i) => {
      const date = new Date(Date.UTC(2026, 0, 1 + i));
      const months = [
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
      ];
      return schedule(date.getUTCDate(), months[date.getUTCMonth()], date.getUTCFullYear());
    }),
  };
  const newSchedules = Array.from({ length: 20 }, (_, i) => schedule(i + 1, "diciembre", 2026));
  const result = validateSchedules(newSchedules, CONFIG, previous);
  assert.equal(result.passed, true);
});

test("fails closed when days go missing inside the overlapping window, even if the source's date range also narrowed", () => {
  // New parse's own window is Dec 1-20, but only 10 of those days actually appear —
  // a genuine parsing regression that a naive full-length delta could mask by
  // coincidence, and that the range-clipped baseline must still catch.
  const previous: LocationSchedule = {
    locationId: "segovia-rural",
    regionId: "segovia-rural",
    sourcePdfUrl: "https://example.com/prev.pdf",
    sourcePdfSha256: "prev",
    parsedAt: new Date().toISOString(),
    version: 1,
    schedules: Array.from({ length: 20 }, (_, i) => schedule(i + 1, "diciembre", 2026)),
  };
  const newSchedules = [
    ...Array.from({ length: 2 }, (_, i) => schedule(i + 1, "diciembre", 2026)),
    ...Array.from({ length: 2 }, (_, i) => schedule(i + 19, "diciembre", 2026)),
  ];
  const result = validateSchedules(newSchedules, CONFIG, previous);
  assert.equal(result.passed, false);
  assert.ok(result.failures.some((f) => f.includes("Delta bound")));
});
