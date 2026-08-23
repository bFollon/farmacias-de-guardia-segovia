# Feature: PDF Change Monitor

## Problem

Right now, the app itself notices a PDF changed only when a device happens to refresh its cache — there's no proactive signal to the owner that a health authority published a new schedule PDF. [[backend-data-service]] needs the same signal to know *when* to re-parse.

## Target solution

Adapt InterSego's `InterSegoMonitor` service (`services/InterSegoMonitor` in that repo) near-verbatim: a small standalone service that periodically scrapes the cofsegovia.com PDF links for all 4 regions, hashes each PDF, and compares against the last known hash. On a change, it:

1. Triggers [[backend-data-service]]'s `POST /api/refresh/:locationId` (new — InterSego's monitor only alerts, doesn't trigger re-parsing, because InterSego has no automated parser to trigger) for the affected region.
2. Sends the existing-style summary email regardless of parse outcome — "PDF changed" is always worth knowing, and the [[parsing-validation-gate]] result (published vs. held for review) rides along in the same email.

## MVP

- Cron schedule: reuse InterSego's `'0 8,20 * * *'` (twice daily) as a starting point — cheap enough to run more often if the owner wants tighter latency later.
- One `RouteCheckState`-equivalent record per region (not per ZBS — all 8 ZBS schedules come from the single `segovia-rural` PDF, so one hash covers all of them): `{ regionId, url, sha256, lastChecked, lastChanged }`, persisted the same lowdb way.
- Manual trigger: `POST /check` (Bearer-auth), same as InterSego, for testing without waiting for the cron.

## Architecture decisions

### Separate service, not folded into the data service

Matches InterSego's split (`InterSegoMonitor` on its own port/pm2 process, distinct from `InterSegoService`). Keeps "watch for changes" decoupled from "serve current data" — the monitor can be down without affecting API availability, and vice versa.

### Scraper needs new URL-extraction logic

cofsegovia.com's PDF listing page has a different structure from Linecar's — this can't reuse InterSego's regex patterns as-is, but can reuse/port the existing `PDFURLScrapingService` (iOS) logic, which already knows how to find these PDFs, rather than writing new scraping code from scratch.

### Trigger, don't just alert

This is the one deliberate deviation from InterSego's monitor: because [[backend-data-service]] *can* auto-parse (unlike InterSego), the monitor should call the refresh endpoint automatically rather than only emailing "go do something." The email becomes a notification of outcome, not a to-do item.

### Certificate/TLS quirks

InterSego's monitor needed a scoped `undici` Agent with certificate verification disabled just for `linecar.es`, due to an incomplete cert chain. Check whether cofsegovia.com has similar issues before assuming a plain `fetch()` will work.

## Status

| Step | Status |
|---|---|
| Scraper ported for cofsegovia.com (4 regions) | ✅ (`pdf-change-monitor/src/scraper/index.ts`, ported from `PDFURLScrapingService.swift`) |
| Hash-diff + persistence wired up | ✅ (`data/checksums.json` via lowdb) |
| Auto-trigger of `/api/refresh` on change | ✅ (`src/refreshTrigger.ts`) |
| Email notifier adapted | ✅ (iCloud SMTP via nodemailer, verified with a real delivered email) |
| Deployed as separate pm2 process | ✅ (`pdf-change-monitor`, deployed 2026-08-23/24, cron `0 8,20 * * *`) |

## Status (2026-08-24)

Fully built, verified end-to-end (including a deliberately-induced auth failure and its fix), and deployed to the Pi as its own pm2 process. Nothing outstanding beyond letting it observe a real (non-simulated) PDF change per region — see `migration-plan.md`'s testing checklist.
