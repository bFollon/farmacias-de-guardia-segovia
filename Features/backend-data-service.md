# Feature: Backend Data Service

## Problem

Both iOS and Android re-parse the same official PDFs on-device, independently, using hand-tuned extraction (Strategy Pattern: `SegoviaCapitalParser`, `CuellarParser`, `ElEspinarParser`, `SegoviaRuralParser`). Parsing runs per-device on cache invalidation, so:

- iOS and Android can drift (different bugs, different parser generations, different cached PDF versions) — no guarantee two users see the same data at the same moment.
- Parsing logic is duplicated across two languages (Swift/PDFKit coordinate-based, Kotlin/iText7 text-based) and must be fixed twice for the same PDF quirk.
- No way to detect a parsing regression without a user reporting an empty/garbled schedule.

## Target solution

A single server-side service owns PDF parsing and is the one source of truth for structured schedule JSON. It:

- Fetches each region's PDF from the official cofsegovia.com source (reusing/porting `PDFURLScrapingService` logic — see [[parser-port-typescript]]).
- Parses it into the JSON schema below, using ported versions of the four existing region parsers.
- Validates parser output before publishing (see [[parsing-validation-gate]]).
- Serves the current JSON per location over a small HTTP API, with ETag-based conditional GETs so clients don't re-download unchanged data.
- Re-parses only when [[pdf-change-monitor]] detects the source PDF changed — not on every request.

Apps stop parsing PDFs locally entirely. They ship a bundled JSON snapshot per location and sync against this service (see [[client-offline-sync]]).

## MVP

- One JSON file per `DutyLocation.id` (`segovia-capital`, `cuellar`, `el-espinar`, and the 8 ZBS ids under `segovia-rural` — `riaza-sepulveda`, `la-granja`, `la-sierra`, `fuentidueña`, `carbonero`, `navas-asuncion`, `villacastin`, `cantalejo`; see `ios/FarmaciasDeGuardiaEnSegovia/Models/ZBS.swift`). `segovia-rural` itself has no direct schedule — it only owns the source PDF that all 8 ZBS files are derived from.
- Manual trigger endpoint to force a re-parse (`POST /api/refresh/:locationId`), used for backfilling and testing before the monitor is wired up.
- No auth-gated write path beyond a shared bearer token (matches InterSego's `RELOAD_KEY` / `Authorization: Bearer` pattern).

## JSON schema

Mirrors the existing `PharmacySchedule` / `DutyDate` / `DutyTimeSpan` / `Pharmacy` Codable shapes (see `ios/FarmaciasDeGuardiaEnSegovia/Models/`) so client-side decoding barely changes:

```jsonc
{
  "locationId": "segovia-capital",
  "regionId": "segovia-capital",       // == locationId for regions; parent region id for ZBS
  "sourcePdfUrl": "https://cofsegovia.com/wp-content/uploads/.../CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf",
  "sourcePdfSha256": "…",              // ties this JSON to the exact PDF it was parsed from
  "parsedAt": "2026-07-08T10:03:00Z",
  "version": 14,                        // monotonically increasing, bumped on every republish
  "schedules": [
    {
      "date": { "dayOfWeek": "lunes", "day": 15, "month": "julio", "year": 2026 },
      "shifts": {
        "capitalDay":   [ { "id": "…", "name": "FARMACIA …", "address": "…", "phone": "921…", "additionalInfo": null } ],
        "capitalNight": [ { "...": "..." } ]
      }
    }
  ]
}
```

- `shifts` keys are the existing `DutyTimeSpan` static-instance names (`capitalDay`, `capitalNight`, `fullDay`, `ruralDaytime`, `ruralExtendedDaytime`) rather than raw start/end minutes — keeps the shift *meaning* in the wire format instead of re-deriving it client-side.
- `id` on each pharmacy: server-generated stable id (not a fresh UUID per parse) so clients can diff schedules across versions without every pharmacy looking "new."
- No cross-location normalization (e.g. sharing a pharmacy across files) — same "duplicate for simplicity" trade-off InterSego made with its `stops[]` arrays, appropriate at this data volume.

## Architecture decisions

### Storage: flat JSON files, not a database

Data volume is tiny (11 schedule files, updated at most a few times a month). A lowdb-style JSON-file store (as InterSego uses for both its services) avoids running a database for essentially static data. Files live in a `data/schedules/` dir the API reads into memory at boot and on reload.

### Reload without restart

`POST /api/admin/reload` (or `SIGHUP`) re-reads `data/schedules/*.json` into the in-memory cache, matching InterSego's pattern — lets [[pdf-change-monitor]] or a manual parse push new data live without a pm2 restart.

### Framework/stack

Fastify + TypeScript + Node ≥20, ESM, built with `tsc`, run under pm2 — same stack as InterSego's `InterSegoService`/`InterSegoMonitor`, deployed to the same Raspberry Pi behind the same Cloudflare Tunnel pattern already hosting Bugsink and Aptabase for this project. Proposed service name: `PharmaciasDataService`, proposed host: `farmacias-api.bfollon.dev` (placeholder, bikeshed later).

## API summary

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | none | liveness check |
| GET | `/api/locations` | Bearer | manifest: `{ locationId, version }[]` for all 11 locations — cheap, no ETag (same rationale as InterSego's `/api/routes`: keeps discovery decoupled from the bulk per-location ETag) |
| GET | `/api/schedules/:locationId` | Bearer | full schedule JSON for one location; supports `If-None-Match` → `304` |
| POST | `/api/refresh/:locationId` | Bearer (admin) | force re-scrape + re-parse + re-validate + publish for one location |
| POST | `/api/admin/reload` | Bearer (admin) | re-read `data/schedules/*.json` from disk without restart |

## Status

| Step | Status |
|---|---|
| Schema finalized | ✅ |
| Service scaffolded (Fastify + lowdb) | ✅ (`server/`) |
| Region parsers ported (see [[parser-port-typescript]]) | ✅ |
| Validation gate wired in (see [[parsing-validation-gate]]) | ✅ |
| Deployed to Pi / Cloudflare Tunnel | ✅ Pi (pm2, `http://homeserver.local:3765`) / ⬜ Cloudflare Tunnel — deliberately deferred, LAN-only for now |
| Client sync consuming it (see [[client-offline-sync]]) | ✅ (both iOS and Android) |
