# Feature: Parsing Validation Gate

## Problem

[[pdf-change-monitor]] tells us *that* a PDF changed; [[parser-port-typescript]] turns it into JSON. But a parser can "succeed" (no exception) while producing garbage — an empty schedule, a truncated date range, a shift with zero pharmacies — and nothing downstream would notice before it ships to every user's device. This is exactly the failure mode InterSego cited for abandoning automated parsing altogether.

## Target solution

Between "parser produced JSON" and "[[backend-data-service]] publishes it," run a set of sanity checks. If they pass, publish automatically (bump `version`, write the file, trigger `POST /api/admin/reload`). If they fail, **do not publish** — keep serving the last-known-good JSON, and send the same kind of alert email [[pdf-change-monitor]] already sends, flagged as a parse failure needing manual review, with the failing checks and a diff against the previous version attached.

This is the hybrid approach: automated by default, human-in-the-loop only on the exception path, rather than InterSego's always-manual model.

## MVP validation checks (per location)

- **Non-empty**: at least N schedules parsed (N = a per-region floor, e.g. ≥20 for monthly regions, ≥4 for weekly regions).
- **Date continuity**: parsed dates form a contiguous run with no unexplained gaps against the expected cadence (daily for Segovia Capital, weekly for Cuéllar/El Espinar).
- **Shift completeness**: every schedule entry has the shift keys the region is expected to have (e.g. Segovia Capital must have both `capitalDay` and `capitalNight` populated; Cuéllar/El Espinar must have `fullDay`) and none of them are empty arrays.
- **Pharmacy shape sanity**: every pharmacy has a non-empty `name` containing "FARMACIA", a non-empty `address`, and a phone number matching the expected Spanish format.
- **Delta bound**: total schedule count and total distinct-pharmacy count haven't changed by more than some threshold (e.g. 50%) versus the last published version — catches a parser that partially matched a reformatted PDF rather than fully failing on it.

## Architecture decisions

### Validation lives in the backend service, not the parser

Keeps [[parser-port-typescript]] focused on "turn PDF into JSON" and this on "is this JSON trustworthy" — a parser change and a validation-rule change are reviewed as separate, independently testable concerns.

### Fail closed

On any check failure, the previous JSON stays live and the failure is surfaced to a human — never publish partial/suspect data automatically. This is the one place the "hybrid" approach explicitly borrows InterSego's human-in-the-loop philosophy rather than trying to be fully autonomous.

### Reuse InterSego's alerting channel

Same nodemailer + iCloud SMTP path as [[pdf-change-monitor]] — one summary email listing which checks failed and why, not a new notification system.

## Status

| Step | Status |
|---|---|
| Validation rules defined per region | ✅ (all 11 locations, `server/src/validation/regionConfig.ts`) |
| Gate wired into `POST /api/refresh/:locationId` | ✅ (all 11 locations; rural handled as a group — see `admin.ts`'s `refreshRural`, which validates all 8 ZBS before publishing any) |
| Failure-alert email template built | ⬜ (no monitor/alerting service exists yet — see [[pdf-change-monitor]]) |
| Delta-bound thresholds tuned against real historical PDFs | ⬜ |
