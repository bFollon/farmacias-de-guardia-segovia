import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { extractPdfPageLines } from "../pdf/extractPageLines.js";
import { parseSegoviaRural } from "./segoviaRural.js";
import type { PharmacySchedule } from "../types.js";

/**
 * Fixture-based regression test per Features/parser-port-typescript.md — a real PDF
 * (fixtures/segovia-rural.pdf, captured 2026-08-23 from cofsegovia.com).
 *
 * The La Granja assertion is cross-checked against LIVE data pulled from a real Android
 * device (2026-08-23, "Segovia Rural" -> "La Granja" screen): "Farmacia Cristina Mínguez
 * Del Pozo" (the C/ Valenciana pharmacy) was on duty. This is the ground truth this port's
 * majority-vote rotation-detection was built and verified against — see segoviaRural.ts's
 * comment on why it doesn't port android's detectFirstLaGranjaPharmacy().
 */

async function loadFixtureSchedules(): Promise<Record<string, PharmacySchedule[]>> {
  const bytes = new Uint8Array(await readFile(new URL("../../fixtures/segovia-rural.pdf", import.meta.url)));
  const pages = await extractPdfPageLines(bytes);
  return parseSegoviaRural(pages, "https://cofsegovia.com/wp-content/uploads/2026/08/SERVICIOS-DE-URGENCIA-RURALES-2026.pdf");
}

function find(schedules: PharmacySchedule[], month: string, day: number, year: number): PharmacySchedule {
  const found = schedules.find((s) => s.date.month === month && s.date.day === day && s.date.year === year);
  assert.ok(found, `expected a schedule for ${day} de ${month} de ${year}`);
  return found;
}

test("all 8 ZBS locations are present with the expected schedule counts", async () => {
  const result = await loadFixtureSchedules();
  assert.deepEqual(
    Object.fromEntries(Object.entries(result).map(([id, s]) => [id, s.length])),
    {
      "riaza-sepulveda": 182, // 196 minus the source PDF's genuine 11-24 ene 2027 gap (see below)
      "la-sierra": 133, // weekday-only ZBS — no weekend coverage in the real PDF
      "fuentidueña": 133,
      carbonero: 196,
      "navas-asuncion": 196,
      villacastin: 144,
      "la-granja": 196, // derived from navas-asuncion's date range
      cantalejo: 196, // derived from navas-asuncion's date range
    },
  );
});

test("regression: 'CEREZO DE ABAJO' no longer drops Riaza/Sepúlveda's schedule", async () => {
  // Both clients key this "CEREZO ABAJO" (missing "DE"), which never matches the real
  // PDF's "CEREZO DE ABAJO" — silently dropping every week this town is on duty.
  const result = await loadFixtureSchedules();
  const schedule = find(result["riaza-sepulveda"], "septiembre", 21, 2026);
  assert.equal(schedule.shifts.ruralExtendedDaytime[0].name, "Farmacia Mario Caballero Serrano");
});

test("riaza-sepulveda has no gaps other than the source PDF's genuine 11-24 ene 2027 hole", async () => {
  const result = await loadFixtureSchedules();
  const schedules = result["riaza-sepulveda"];
  const missingDates = schedules.every((s) => !(s.date.month === "enero" && s.date.year === 2027 && s.date.day >= 11 && s.date.day <= 24));
  assert.ok(missingDates, "expected no riaza-sepulveda entries for 11-24 enero 2027");
  assert.ok(schedules.some((s) => s.date.month === "enero" && s.date.day === 10 && s.date.year === 2027));
});

test("regression: La Granja's rotation matches live device ground truth (2026-08-23 -> Valenciana)", async () => {
  const result = await loadFixtureSchedules();
  const schedule = find(result["la-granja"], "agosto", 23, 2026);
  assert.equal(schedule.shifts.ruralExtendedDaytime[0].name, "Farmacia Cristina Mínguez Del Pozo");
  assert.equal(schedule.shifts.ruralExtendedDaytime[0].address, "C. Valenciana, 3, BAJO, 40100 Real Sitio de San Ildefonso, Segovia");
});

test("La Granja's rotation is a strict weekly alternation with no repeats or skips", async () => {
  const result = await loadFixtureSchedules();
  const monthIndex = (m: string) =>
    ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"].indexOf(m);
  const epochDay = (d: PharmacySchedule["date"]) => Math.floor(Date.UTC(d.year, monthIndex(d.month), d.day) / 86400000);

  const sorted = [...result["la-granja"]].sort((a, b) => epochDay(a.date) - epochDay(b.date));
  // Week boundaries relative to the data's own first date (a Monday), matching how the
  // parser computes them — NOT the Unix epoch (1970-01-01 is a Thursday), which would
  // land week boundaries mid-week from the parser's perspective and make this test flaky.
  const startDay = epochDay(sorted[0].date);

  let previousName: string | undefined;
  let previousWeek = -1;
  for (const s of sorted) {
    const week = Math.floor((epochDay(s.date) - startDay) / 7);
    const name = s.shifts.ruralExtendedDaytime[0].name;
    if (previousName && week !== previousWeek) {
      assert.notEqual(name, previousName, `expected the pharmacy to alternate at week ${week} (${s.date.day} de ${s.date.month})`);
    }
    previousName = name;
    previousWeek = week;
  }
});

test("Cantalejo lists both pharmacies together, every day, mirroring navas-asuncion's dates", async () => {
  const result = await loadFixtureSchedules();
  const schedule = find(result.cantalejo, "julio", 13, 2026);
  const names = schedule.shifts.ruralDaytime.map((p) => p.name).sort();
  assert.deepEqual(names, ["Farmacia Carmen Bautista", "Farmacia en Cantalejo"]);
});

test("a directly-parsed ZBS entry has the expected pharmacy and computed day of week (not 'Unknown')", async () => {
  const result = await loadFixtureSchedules();
  const schedule = find(result["navas-asuncion"], "julio", 13, 2026);
  assert.equal(schedule.date.dayOfWeek, "lunes");
  assert.equal(schedule.shifts.ruralDaytime[0].name, "Farmacia Pilar Tribiño Mendiola");
});
