# Feature: Server-Based Schedule Migration — Plan

## Status: Phases 1-2 complete; Phases 3-4 done via a different path than originally planned (see note below); server deployed to the Pi

`server/` is deployed and populated (all 11 locations, `http://homeserver.local:3765`, LAN-only for now). `pdf-change-monitor/` is built and verified end-to-end but not yet deployed to the Pi. Both iOS and Android apps are fully migrated to server sync (see below).

**Deviation from the original Phase 3/4 sequencing**: this doc's steps 8-9 called for an *additive* dual-path rollout (sync alongside on-device parsing, with a monitoring period before cutover). The user explicitly chose to skip straight to [[client-offline-sync]]'s "no fallback" design instead — on-device parsing was deleted in the same pass that added sync, not kept as a safety net. This was a deliberate, informed choice (flagged and confirmed with the user given the server was LAN-only/unproven at the time), not an oversight. Both apps now depend entirely on the sync server; see `Features/client-offline-sync.md` for the full implementation record.

## Problem

See [[backend-data-service]], [[parser-port-typescript]], [[parsing-validation-gate]], [[pdf-change-monitor]], and [[client-offline-sync]] for the individual pieces. This doc is the sequencing: what order to build things in, how to de-risk the cutover, and how to know it worked — modeled on InterSego's `docs/DYNAMIC_ROUTE_DISCOVERY.md`, the closest prior art for "migrate a live app from one data-sourcing model to another without breaking it for existing users."

**Context worth restating up front**: InterSego attempted almost exactly this architecture (server-side automated PDF parsing) and abandoned it, replacing it with manual JSON authoring + a change-alert monitor, because their bus-timetable PDFs were too irregular to parse reliably. We're deliberately choosing a **hybrid** here instead (automated parsing + a validation gate that fails closed to manual review) because Segovia's pharmacy PDFs are simpler, regular tabular layouts — but Phase 1 below exists specifically to test that assumption before committing further.

## Current architecture (baseline)

- iOS: PDFKit coordinate-based parsing, 4 region parsers, 3-tier cache (memory → `ScheduleCacheService` JSON → PDF re-parse) — see project `CLAUDE.md`'s "Three-Tier Caching Architecture" section.
- Android: iText7 text-based parsing, same Strategy Pattern, same 3-tier shape.
- No shared source of truth — each install can be on a different PDF version.

## Target architecture

Server owns parsing (hybrid automated + validation gate); apps sync pre-parsed JSON with bundle-first offline fallback. See the five linked feature docs for detail per component.

## Implementation phases

### Phase 1 — Backend foundation (no client changes yet)

1. Scaffold [[backend-data-service]] (Fastify + lowdb, deployed to the Pi, empty of real parsers initially).
2. Port `SegoviaCapitalParser` only, per [[parser-port-typescript]]'s MVP ordering, with fixture tests.
3. Build [[parsing-validation-gate]]'s MVP checks and wire them into the refresh endpoint.
4. Manually verify Segovia Capital JSON output against the current app's parsed output for several real dates — this is the trust-building step before any client depends on it. **If parsing proves as unreliable here as it did for InterSego, stop and fall back to the manual-authoring model before investing further.**

**Phase 1 done for segovia-capital (2026-08-23).** All 4 steps complete: `server/` scaffolded and running locally (not deployed); `server/src/parsers/segoviaCapital.ts` ported from the Android Kotlin implementation with a fixture test against a real production PDF; the validation gate (`server/src/validation/gate.ts`) is wired into `POST /api/refresh/segovia-capital` and fails closed. Step 4's manual verification: parsing the real live PDF through the TS port produced schedule counts, date ranges, and per-pharmacy data that matched the Android client's on-device output exactly (256 schedules, 43 days with FARMACIA MARTÍN CANTERO, identical values for every spot-checked date) — parsing is proving reliable, no InterSego-style abandonment needed. The other three regions haven't been ported yet (Phase 2).

### Phase 2 — Remaining parsers + monitor

5. Port Cuéllar, El Espinar, Segovia Rural parsers.
6. Build and deploy [[pdf-change-monitor]] as its own pm2 process, wired to auto-trigger refreshes.
7. Let the full pipeline (monitor → parse → validate → publish) run unattended for at least one real PDF update cycle per region before touching client code — the whole point of this migration is trustworthy shared data, so the server side needs to earn that trust before clients start relying on it exclusively.

**Step 5 done (2026-08-23).** All 4 region parsers ported (`server/src/parsers/{segoviaCapital,cuellar,elEspinar,segoviaRural}.ts`), 28 tests passing, all verified live against real production PDFs via `POST /api/refresh/:locationId` — every one of the 11 servable locations (3 town regions + 8 rural ZBS) now publishes successfully. Along the way this surfaced **3 live, currently-shipping bugs** in the Kotlin/Swift originals (not ported forward — fixed in the TS port instead, and flagged here for the client codebases):
- El Espinar: a fold-logic ordering bug in `ElEspinarParser.kt` misattributes whole weeks to the wrong pharmacy (not just drops them) — see commit c72b9f4's description.
- Cuéllar: the August/September month-boundary week silently disappears — `CuellarParser.kt`'s docstring claims to handle it, but the regex doesn't exist in the actual code.
- Segovia Rural: both clients key Riaza/Sepúlveda's "Cerezo de Abajo" pharmacy as "CEREZO ABAJO" (missing "DE"), which never matches the real PDF text, dropping that ZBS's schedule for every week the town is on duty.

Segovia Rural's La Granja ZBS also isn't a port of `detectFirstLaGranjaPharmacy()` (that function has an inverted-index bug and would likely pick the wrong pharmacy) — it's re-derived via majority vote across the whole document and was verified against **live data pulled from a real Android device** (2026-08-23 → Farmacia Cristina Mínguez Del Pozo), per commit 762c5e5.

**Step 6 done (2026-08-23).** `pdf-change-monitor/` built, near-verbatim port of InterSego's `services/InterSegoMonitor` per this doc's own architecture notes, with the "trigger, don't just alert" deviation: on a detected change it calls `POST /api/refresh/:locationId` directly rather than only emailing. Verified end-to-end locally: live scraping of cofsegovia.com found all 4 region PDFs matching the server's static fallbacks exactly, SMTP (iCloud) verified, and a simulated PDF change correctly triggered a real refresh call against the deployed Pi server (published successfully) and delivered the summary email. Not yet deployed as a pm2 process on the Pi — that's the next step.

### Phase 3 — Client sync, additive

8. Add [[client-offline-sync]]'s manifest/ETag sync to both apps **alongside** the existing on-device parsing path — sync populates the disk cache, but on-device parsing remains as the fallback if sync fails or looks wrong. This is the safest cutover point: a bug in the new sync path degrades to today's behavior, not to a broken app.
9. Ship this dual-path version, monitor for divergence between server-synced and on-device-parsed data (log/report any mismatch) to catch parser-port bugs the fixture tests missed.

### Phase 4 — Cutover

10. Once no divergence has been observed for a full schedule cycle per region, flip the client to server-data-only and delete the on-device parsing code (both platforms), per [[client-offline-sync]]'s "no fallback" decision.
11. Update `CLAUDE.md`'s architecture docs to describe the new model; retire the old "Strategy Pattern for PDF Processing" section or repoint it at the TS service.

## Edge cases & notes

- **Segovia Rural / ZBS**: one PDF backs 8 JSON files. The monitor hashes once per region, not per ZBS, so a single PDF change triggers 8 republishes — [[parsing-validation-gate]] needs to validate all 8 before any of them publish (a partial-ZBS failure shouldn't silently publish 7 good + 1 stale).
- **Year rollover**: existing `DutyDate.getCurrentYear()` / year-detection logic for PDFs that omit the year needs a home somewhere in the ported TS parsers — don't lose this edge case in the port.
- **First-install bundle staleness**: bundled JSON is only as fresh as the last app-store release; if a schedule changes between releases, first-launch-offline users see stale data until they're online. Same trade-off InterSego accepted — worth confirming this is acceptable before Phase 4.

## Testing checklist (before Phase 4 cutover)

- [ ] Fixture-based parser tests passing for all 4 regions
- [ ] Validation gate has caught at least one deliberately-broken test case per rule
- [ ] Monitor has correctly detected at least one real PDF change per region (not just synthetic)
- [ ] Client sync tested offline (airplane mode from first install) and online (fresh data after a server-side change)
- [ ] Divergence-logging from Phase 3 shows zero unexplained mismatches for one full cycle
- [ ] Manual side-by-side comparison: app-displayed schedule vs. the actual official PDF, for a sample of dates across all 4 regions
