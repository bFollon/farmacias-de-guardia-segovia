# Feature: Client Offline Sync

## Handoff (2026-08-23)

Server side is done and live — start here, don't re-derive it:

- **Deployed and populated**: `http://homeserver.local:3765` (Pi, pm2, LAN-only for now — no Cloudflare Tunnel yet, deliberately deferred until the client side proves out locally). All 11 locations serving real parsed data at version 1.
- **Two separate keys** (`API_KEY` for reads, `RELOAD_KEY` for admin — see this doc's own MVP section for where `API_KEY` goes: `UserDefaults`/`SharedPreferences`, per-location ETag). Ask the user for `API_KEY`; never ask for `RELOAD_KEY` — that one stays server-side.
- Endpoints: `GET /api/locations` (manifest), `GET /api/schedules/:locationId` (supports `If-None-Match` → `304`). Both `Bearer $API_KEY`.
- **Unresolved before writing code**: this doc's "No client-side parsing fallback" (Architecture decisions, below) directly contradicts `migration-plan.md` Phase 3 step 8, which calls for sync to be *additive* — on-device parsing kept as a fallback if sync fails or looks wrong. Get the user to pick one before implementing; don't silently follow either.
- All 4 region parsers were ported to `server/src/parsers/` this session and found 2 confirmed live bugs in the process (Cuéllar's Aug/Sep transition week silently drops; Segovia Rural's "Cerezo de Abajo" keyword typo empties Riaza/Sepúlveda's schedule some weeks) — both fixed server-side only. Worth asking the user whether to backport those two fixes to the clients being touched now, since you'll have the relevant files open anyway.

## Problem

iOS and Android each parse PDFs on-device and cache the result locally (`ScheduleCacheService.swift` / Android equivalent), keyed by PDF modification date. This means two devices can show different data if they happened to cache different PDF versions, and every device repeats parsing work that [[backend-data-service]] will now do once, centrally.

## Target solution

Replace on-device PDF parsing with:

1. **Bundled JSON**: one JSON file per `DutyLocation.id` (11 total — 3 regions + 8 ZBS; `segovia-rural` itself has no direct schedule file), shipped in the app bundle as day-zero offline data, generated from [[backend-data-service]]'s output at build/release time.
2. **Server sync**: on app launch (when online), fetch the location manifest and any location whose `version` differs from what's cached, using conditional GET + ETag so unchanged locations cost a `304` instead of a full download.
3. **Disk cache precedence**: same rule as InterSego — disk cache (synced from server) overrides the bundle; bundle is the day-zero fallback only, never touched again after install.

`PDFProcessingService`, `PDFURLScrapingService`, `PDFCacheManager`, `PDFDownloadService`, and all region-specific parser classes get deleted from both apps once this ships — same cleanup InterSego did when it migrated off on-device parsing.

## MVP

- Manifest fetch: `GET /api/locations` → `{ locationId, version }[]`, unconditional (no ETag — same reasoning as InterSego: keeps discovery cheap and decoupled from the bulk per-location ETag scheme).
- Per-location conditional fetch: store `ETag` per `locationId` (iOS: `UserDefaults` key `schedule_etag_{locationId}`; Android: `SharedPreferences` equivalent), `If-None-Match` on every check.
- Offline fallback: manifest fetch failure → silently fall back to whatever's on disk (bundle or prior sync), matching the app's existing offline-first UX (`NetworkMonitor`, `OfflineWarningCard`).
- `ScheduleCacheService`'s existing `CacheMetadata`/versioning scaffolding gets repurposed: swap "PDF modification date" validity check for "server `version` differs from cached `version`" — the file-based cache mechanics (`Documents/ScheduleCache/{id}.json` + `{id}.meta.json`) barely change.

## Architecture decisions

### Keep the existing on-disk cache layout

`ScheduleCacheService` already has the right shape (per-location JSON + metadata file, memory cache on top). Only the *source* of truth for "is my cache stale" changes — from PDF-modification-date comparison to server-version comparison. Minimizes churn in code that isn't the actual problem.

### Hot-swap without app restart

Port InterSego's `pendingUpdates: Set<String>` pattern: a background sync that finds a newer version for a location marks it, and the in-memory `ScheduleService.cachedSchedules` entry is evicted on next access — so a background refresh takes effect without forcing an app restart.

### No client-side parsing fallback

Once this ships, there is no "parse the PDF myself" fallback path left on-device — if the server is unreachable, the client shows the last-synced (bundle or disk) data, same as InterSego's offline behavior. This is a deliberate simplification: don't keep two parsing paths (server + on-device emergency fallback) alive long-term, since that reintroduces the exact drift problem this migration exists to fix. The Swift/Kotlin parsers remain in git history and in [[parser-port-typescript]]'s reference material, just not compiled into the shipping app.

## Status

| Step | Status |
|---|---|
| Manifest + per-location sync client added (iOS) | ⬜ |
| Manifest + per-location sync client added (Android) | ⬜ |
| `ScheduleCacheService` re-keyed to server `version` | ⬜ |
| Bundled JSON generation added to release process | ⬜ |
| On-device parsers deleted (both platforms) | ⬜ |
