import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { extractPdfPageLines } from "../pdf/extractPageLines.js";
import { parseElEspinar } from "./elEspinar.js";
import type { PharmacySchedule } from "../types.js";

/**
 * Fixture-based regression test per Features/parser-port-typescript.md — a real PDF
 * (fixtures/el-espinar.pdf, captured 2026-08-23 from cofsegovia.com).
 *
 * The week-attribution assertions guard a real bug this port found and avoided: android's
 * ElEspinarParser.kt's fold logic checks `hasPharmacy(line)` before `hasDates(line)`, so
 * on a line combining both (e.g. "05-ene ... 11-ene SAN RAFAEL", which pdfjs-dist's line
 * clustering produces for this region) it extracts only the pharmacy and discards that
 * line's dates — then misattributes the *next* week's dates to the *previous* week's
 * pharmacy once it finally flushes. See weeklyRotation.ts for the corrected algorithm.
 */

async function loadFixtureSchedules(): Promise<PharmacySchedule[]> {
  const bytes = new Uint8Array(await readFile(new URL("../../fixtures/el-espinar.pdf", import.meta.url)));
  const pages = await extractPdfPageLines(bytes);
  return parseElEspinar(pages, "https://cofsegovia.com/wp-content/uploads/2026/01/Guardias-EL-ESPINAR_2026.pdf");
}

function find(schedules: PharmacySchedule[], month: string, day: number, year: number): PharmacySchedule {
  const found = schedules.find((s) => s.date.month === month && s.date.day === day && s.date.year === year);
  assert.ok(found, `expected a schedule for ${day} de ${month} de ${year}`);
  return found;
}

test("parses with no date gaps", async () => {
  const schedules = await loadFixtureSchedules();
  assert.equal(schedules.length, 404);
});

test("regression: each week's dates attribute to the pharmacy printed on/after them, not the previous week's", async () => {
  const schedules = await loadFixtureSchedules();
  const expected: [number, string, number, string][] = [
    [31, "diciembre", 2025, "Farmacia Lda M J. Bartolomé Sánchez"], // C/ MARQUES PERALES
    [4, "enero", 2026, "Farmacia Lda M J. Bartolomé Sánchez"],
    [5, "enero", 2026, "Farmacia San Rafael"], // SAN RAFAEL — the week the original bug drops
    [11, "enero", 2026, "Farmacia San Rafael"],
    [12, "enero", 2026, "FARMACIA ANA MARÍA APARICIO HERNAN"], // AV. HONTANILLA 18
    [18, "enero", 2026, "FARMACIA ANA MARÍA APARICIO HERNAN"],
  ];

  for (const [day, month, year, expectedName] of expected) {
    const schedule = find(schedules, month, day, year);
    assert.equal(schedule.shifts.fullDay[0].name, expectedName, `${day} de ${month}`);
  }
});

test("regression: FARMACIA Lda M J. Bartolomé Sánchez gets a corrected, geocodable address", async () => {
  const schedules = await loadFixtureSchedules();
  const schedule = find(schedules, "enero", 1, 2026);
  assert.equal(schedule.shifts.fullDay[0].address, "C. Marqués de Perales, 2, 40400 El Espinar, Segovia");
});
