# Feature: Client Offline Sync

## Status (2026-08-24): Complete, shipped on both platforms

Both iOS and Android are fully migrated to server sync. The "No client-side parsing fallback" question flagged below was resolved by the user: sync is the sole source of truth, on-device parsing was deleted in the same pass rather than kept as a fallback (this deviates from `migration-plan.md`'s original additive Phase 3 design — a deliberate, informed choice given the server was LAN-only/unproven at the time, not an oversight). See `migration-plan.md`'s top-of-file note for the full rationale.

Known bugs found in the on-device parsers during the server-side port (Cuéllar Aug/Sep week drop, Segovia Rural "Cerezo de Abajo" typo) were **not** backported to the clients — moot, since the buggy client parsers were deleted outright rather than patched.

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
| Manifest + per-location sync client added (iOS) | ✅ `ScheduleSyncService.swift` |
| Manifest + per-location sync client added (Android) | ✅ `ScheduleSyncService.kt` |
| `ScheduleCacheService` re-keyed to server `version` | ✅ (both platforms; cache format version bumped to force-invalidate pre-migration caches) |
| Bundled JSON generation added to release process | ✅ `server/scripts/export-bundled-schedules.ts`, 11 files committed per platform (manual, run-before-release — no CI in this project) |
| On-device parsers deleted (both platforms) | ✅ (`PDFProcessingService`, all parser strategies, `PDFCacheManager`, `PDFDownloadService`, itext-kernel dependency) |
