import { test } from "node:test";
import assert from "node:assert/strict";
import { applyCatalog, buildCoordinateMap, pharmacyKey, type CatalogEntry } from "./catalog.js";
import type { LocationSchedule, Pharmacy } from "./types.js";

function pharmacy(overrides: Partial<Pharmacy> = {}): Pharmacy {
  return {
    id: "id",
    name: "FARMACIA MATEOS RODRÍGUEZ ",
    address: "SAN FRANCISCO, 9",
    phone: "921 428220",
    additionalInfo: null,
    ...overrides,
  };
}

function location(pharmacies: Pharmacy[]): LocationSchedule {
  return {
    locationId: "segovia-capital",
    regionId: "segovia-capital",
    sourcePdfUrl: "https://example.test/a.pdf",
    sourcePdfSha256: "abc",
    parsedAt: "2026-01-01T00:00:00.000Z",
    version: 3,
    schedules: [
      {
        date: { dayOfWeek: "lunes", day: 1, month: "enero", year: 2026 },
        shifts: { capitalDay: pharmacies, capitalNight: [] },
      },
    ],
  };
}

function entry(overrides: Partial<CatalogEntry> = {}): CatalogEntry {
  return {
    name: "Farmacia Mateos Rodriguez",
    address: "San Francisco,  9",
    locationId: "segovia-capital",
    latitude: 40.9481,
    longitude: -4.1197,
    ...overrides,
  };
}

test("pharmacyKey ignores case, accents and stray whitespace", () => {
  assert.equal(
    pharmacyKey("FARMACIA MATEOS RODRÍGUEZ ", "SAN  FRANCISCO, 9"),
    pharmacyKey("farmacia mateos rodriguez", " San Francisco, 9"),
  );
});

test("applyCatalog merges coordinates into every matching entry", () => {
  const { coordinates } = buildCoordinateMap([entry()]);
  const result = applyCatalog(location([pharmacy(), pharmacy({ id: "other-id" })]), coordinates);

  const merged = result.location.schedules[0]!.shifts.capitalDay!;
  assert.equal(merged.length, 2);
  for (const p of merged) {
    assert.equal(p.latitude, 40.9481);
    assert.equal(p.longitude, -4.1197);
  }
  assert.deepEqual(result.uncataloged, []);
});

test("applyCatalog does not mutate its input", () => {
  const { coordinates } = buildCoordinateMap([entry()]);
  const input = location([pharmacy()]);
  applyCatalog(input, coordinates);
  assert.equal(input.schedules[0]!.shifts.capitalDay![0]!.latitude, undefined);
});

test("pharmacies missing from the catalog are reported once and left without coordinates", () => {
  const { coordinates } = buildCoordinateMap([entry()]);
  const unknown = pharmacy({ name: "FARMACIA NUEVA", address: "CALLE NUEVA, 1" });
  const result = applyCatalog(location([unknown, unknown]), coordinates);

  assert.deepEqual(result.uncataloged, ["FARMACIA NUEVA | CALLE NUEVA, 1"]);
  const [first] = result.location.schedules[0]!.shifts.capitalDay!;
  assert.ok(!("latitude" in first!));
  assert.ok(!("longitude" in first!));
});

test("entries with null coordinates count as uncataloged", () => {
  const { coordinates } = buildCoordinateMap([entry({ latitude: null, longitude: null })]);
  const result = applyCatalog(location([pharmacy()]), coordinates);
  assert.equal(result.uncataloged.length, 1);
});

test("re-applying strips coordinates a pharmacy no longer has in the catalog", () => {
  const withCoords = applyCatalog(location([pharmacy()]), buildCoordinateMap([entry()]).coordinates).location;
  const after = applyCatalog(withCoords, buildCoordinateMap([]).coordinates);
  assert.ok(!("latitude" in after.location.schedules[0]!.shifts.capitalDay![0]!));
});

test("applying the same catalog twice is a no-op", () => {
  const { coordinates } = buildCoordinateMap([entry()]);
  const once = applyCatalog(location([pharmacy()]), coordinates).location;
  const twice = applyCatalog(once, coordinates).location;
  assert.equal(JSON.stringify(twice), JSON.stringify(once));
});

test("coordinates outside the province of Segovia are rejected with a warning", () => {
  // Classic typo: longitude without its minus sign puts the pin in Turkey.
  const { coordinates, warnings } = buildCoordinateMap([entry({ longitude: 4.1197 })]);
  assert.equal(coordinates.size, 0);
  assert.equal(warnings.length, 1);
});

test("duplicate catalog entries keep the first and warn", () => {
  const { coordinates, warnings } = buildCoordinateMap([entry(), entry({ latitude: 40.95 })]);
  assert.equal(coordinates.size, 1);
  assert.equal([...coordinates.values()][0]!.latitude, 40.9481);
  assert.equal(warnings.length, 1);
});
