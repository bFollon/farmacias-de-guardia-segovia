import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { extractPdfPageLines } from "../pdf/extractPageLines.js";
import { parseCuellar } from "./cuellar.js";
import type { PharmacySchedule } from "../types.js";

/**
 * Fixture-based regression test per Features/parser-port-typescript.md — a real PDF
 * (fixtures/cuellar.pdf, captured 2026-08-23 from cofsegovia.com).
 *
 * The August/September transition-week assertion guards a real gap this port found and
 * fixed: android's CuellarParser.kt claims (in its docstring) to handle the spelled-out
 * "DOMINGO 30 DE AGOSTO Y LUNES 31 DE AGO ..." format the PDF uses for the one week that
 * straddles a month boundary, but no such regex exists in the actual code — that whole
 * week (30 ago - 6 sep) silently disappears from the parsed schedule today. iOS's
 * CuellarParser.swift does implement it (parseSeptemberTransition), which this ports.
 */

async function loadFixtureSchedules(): Promise<PharmacySchedule[]> {
  const bytes = new Uint8Array(await readFile(new URL("../../fixtures/cuellar.pdf", import.meta.url)));
  const pages = await extractPdfPageLines(bytes);
  return parseCuellar(pages, "https://cofsegovia.com/wp-content/uploads/2026/01/GUARDIAS-CUELLAR_2026.pdf");
}

function find(schedules: PharmacySchedule[], month: string, day: number, year: number): PharmacySchedule {
  const found = schedules.find((s) => s.date.month === month && s.date.day === day && s.date.year === year);
  assert.ok(found, `expected a schedule for ${day} de ${month} de ${year}`);
  return found;
}

test("parses a full year with no date gaps, including the month-boundary week", async () => {
  const schedules = await loadFixtureSchedules();
  assert.equal(schedules.length, 371);
});

test("regression: the August/September transition week is no longer dropped", async () => {
  const schedules = await loadFixtureSchedules();
  const expected: [number, string, number, string][] = [
    [30, "agosto", 2026, "Farmacia Ldo. Fco. Javier Alcaraz García de la Barrera"],
    [31, "agosto", 2026, "Farmacia Ldo. Fco. Javier Alcaraz García de la Barrera"],
    [1, "septiembre", 2026, "Farmacia Ldo. César Cabrerizo Izquierdo"],
    [2, "septiembre", 2026, "Farmacia Ldo. César Cabrerizo Izquierdo"],
    [3, "septiembre", 2026, "Farmacia Fernando Redondo"],
    [4, "septiembre", 2026, "Farmacia Fernando Redondo"],
    [5, "septiembre", 2026, "Farmacia San Andrés"],
    [6, "septiembre", 2026, "Farmacia San Andrés"],
  ];

  for (const [day, month, year, expectedName] of expected) {
    const schedule = find(schedules, month, day, year);
    assert.equal(schedule.shifts.fullDay[0].name, expectedName, `${day} de ${month}`);
  }
});

test("a regular dd-mmm week parses with the expected pharmacy and address", async () => {
  const schedules = await loadFixtureSchedules();
  const schedule = find(schedules, "enero", 5, 2026);
  assert.deepEqual(schedule.shifts.fullDay[0], {
    id: schedule.shifts.fullDay[0].id,
    name: "Farmacia Fernando Redondo",
    address: "Av. Camilo Jose Cela, 46, 40200 Cuéllar, Segovia",
    phone: "No disponible",
    additionalInfo: null,
  });
});
