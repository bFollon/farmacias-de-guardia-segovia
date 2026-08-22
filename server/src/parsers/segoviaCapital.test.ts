import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { extractPdfPageLines } from "../pdf/extractPageLines.js";
import { parseSegoviaCapital } from "./segoviaCapital.js";
import type { PharmacySchedule } from "../types.js";

/**
 * Fixture-based regression test per Features/parser-port-typescript.md — a real historical
 * PDF (fixtures/segovia-capital.pdf, captured 2026-08-23 from cofsegovia.com) with a
 * hand-verified expected output, so a silent parsing regression fails a test instead of
 * shipping bad data (the exact failure mode Features/migration-plan.md's InterSego
 * precedent cites for abandoning automated parsing).
 *
 * Expected values were cross-checked against the same PDF parsed by the Android client
 * on-device (see commits 5dbda38 and 593e516's investigation) — 256 schedules, 43 days
 * with FARMACIA MARTÍN CANTERO on duty, spanning jueves 21 mayo 2026 to domingo 31 enero 2027.
 */

const FIXTURE_URL =
  "https://cofsegovia.com/wp-content/uploads/2026/06/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-2026.pdf";

async function loadFixtureSchedules(): Promise<PharmacySchedule[]> {
  const bytes = new Uint8Array(await readFile(new URL("../../fixtures/segovia-capital.pdf", import.meta.url)));
  const pages = await extractPdfPageLines(bytes);
  return parseSegoviaCapital(pages, FIXTURE_URL);
}

function find(schedules: PharmacySchedule[], month: string, day: number): PharmacySchedule {
  const found = schedules.find((s) => s.date.month === month && s.date.day === day);
  assert.ok(found, `expected a schedule for ${day} de ${month}`);
  return found;
}

test("parses every schedule in the fixture, none dropped", async () => {
  const schedules = await loadFixtureSchedules();
  assert.equal(schedules.length, 256);
  assert.deepEqual(schedules[0].date, { dayOfWeek: "jueves", day: 21, month: "mayo", year: 2026 });
  assert.deepEqual(schedules[schedules.length - 1].date, {
    dayOfWeek: "domingo",
    day: 31,
    month: "enero",
    year: 2027,
  });
});

test("regression: FARMACIA MARTÍN CANTERO no longer drops its schedule day", async () => {
  // See commit 593e516 — this pharmacy's PDF entry used to break ADDRESS_REGEX and
  // silently drop BOTH shifts for every day it was on duty (~40 days/year).
  const schedules = await loadFixtureSchedules();
  const days = schedules.filter((s) =>
    Object.values(s.shifts).some((pharmacies) => pharmacies.some((p) => p.name.toUpperCase().includes("CANTERO"))),
  );
  assert.equal(days.length, 43);
});

test("regression: FARMACIA MARTÍN CANTERO gets the corrected geocodable address", async () => {
  // See commit 5dbda38 — the raw PDF text ("CENTRO COMERCIAL LUZ DE CASTILLA") has no
  // street, so Apple's/Google's geocoders can't resolve it.
  const schedules = await loadFixtureSchedules();
  const aug22 = find(schedules, "agosto", 22);
  assert.equal(aug22.shifts.capitalDay[0].name.trim(), "FARMACIA MARTÍN CANTERO");
  assert.equal(aug22.shifts.capitalDay[0].address, "Calle Guadarrama, S/N, Segovia");
});

test("today's on-duty pharmacy resolves correctly (Aug 22 night = Herrero Isern)", async () => {
  const schedules = await loadFixtureSchedules();
  const aug22 = find(schedules, "agosto", 22);
  assert.equal(aug22.shifts.capitalNight[0].name, "FARMACIA HERRERO ISERN");
  assert.equal(aug22.shifts.capitalNight[0].address, "CARRETERA . VILLACASTIN, 10");
  assert.equal(aug22.shifts.capitalNight[0].phone, "921 427938");
});

test("a well-formed day/night pair parses with the expected shape", async () => {
  const schedules = await loadFixtureSchedules();
  const may22 = find(schedules, "mayo", 22);
  assert.deepEqual(may22.shifts.capitalDay[0], {
    id: may22.shifts.capitalDay[0].id,
    name: "FARMACIA SANZ DE PABLOS ",
    address: "PASEO CONDE SEPULVEDA, 33",
    phone: "921 435749",
    additionalInfo: null,
  });
  assert.deepEqual(may22.shifts.capitalNight[0], {
    id: may22.shifts.capitalNight[0].id,
    name: "FARMACIA HERRERO ISERN",
    address: "CARRETERA . VILLACASTIN, 10",
    phone: "921 427938",
    additionalInfo: "(Frente Estación Ferrocarril)",
  });
});

test("pharmacy ids are stable across repeated parses of the same PDF", async () => {
  const first = await loadFixtureSchedules();
  const second = await loadFixtureSchedules();
  const idsOf = (schedules: PharmacySchedule[]) =>
    schedules.flatMap((s) => Object.values(s.shifts).flatMap((ps) => ps.map((p) => p.id)));
  assert.deepEqual(idsOf(first), idsOf(second));
});
