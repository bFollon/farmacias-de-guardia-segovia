# Analytics — Farmacias de Guardia

## Decision

**Tool: [Aptabase](https://aptabase.com) (self-hosted)**

Aptabase is a lightweight, privacy-first analytics platform designed specifically for app event tracking. It runs comfortably on a Raspberry Pi 5 (~256 MB RAM), ships as a simple Docker Compose stack, has official Swift and Android SDKs, and its data model (`eventName` + arbitrary `props` JSON object) maps directly to the events we want to track.

Other options considered and rejected:
- **PostHog OSS**: ideal data model and SDKs, but requires Kafka + ClickHouse — too heavy for Pi 5 (6–8 GB RAM minimum)
- **Umami / Plausible**: lightweight, but web-centric dashboards and no mobile SDKs
- **LGTM stack**: suited for infrastructure telemetry, not app-level product events

---

## Relationship to Existing Monitoring

This app already has **Bugsink** (self-hosted Sentry-compatible) for error reporting, gated on user consent via `MonitoringPreferencesService`.

Aptabase is complementary — a separate concern:

| System | Purpose | Consent gate |
|---|---|---|
| Bugsink (Sentry SDK) | Error reports, crash diagnostics | `MonitoringPreferencesService` — `monitoring_enabled` key |
| Aptabase | Product/operational events, usage analytics | `MonitoringPreferencesService` — `analytics_enabled` key (separate toggle) |

The two toggles are independent: a user can opt into error reporting without opting into analytics, and vice versa. `MonitoringConsentView` needs to be updated to present both choices, and `MonitoringPreferencesService` needs a second `analyticsEnabled` preference key.

---

## Integration Plan

### iOS

- New service: `AnalyticsService.swift` in `Services/`, following the same singleton pattern as `ErrorReportingService`
- SDK: `AptabaseSwift` (add via Swift Package Manager)
- API key: add `aptabaseKey: String` to `Secrets.swift` (already the pattern for `sentryDSN`)
- Initialization: call `AnalyticsService.shared.initialize()` in `FarmaciasDeGuardiaEnSegoviaApp.swift` alongside `ErrorReportingService.shared.initialize()`, gated on consent
- Fire-and-forget: all `track()` calls are non-blocking; never gate app logic on them

### Android

- New service: `AnalyticsService.kt` in `services/`, following the same singleton pattern as `ErrorReportingService`
- SDK: Aptabase Android SDK (add via Gradle)
- API key: add `APTABASE_KEY` to `Secrets.kt` (or `local.properties` → `BuildConfig`)
- Initialization: same pattern as iOS, gated on consent preference

---

## Events to Implement

### App Lifecycle

| Event | Props | Where to fire (iOS) |
|---|---|---|
| `app_launch` | `version: String`, `platform: "ios"` | `FarmaciasDeGuardiaEnSegoviaApp.swift` — on `.onAppear` of root view |

### PDF URL Scraping

The scraping operation runs at every app launch via `PreloadService.preloadAllData()` → `PDFURLScrapingService.scrapePDFURLs()`.

| Event | Props | Where to fire (iOS) |
|---|---|---|
| `pdf_url_scrape_complete` | `urls_found: Int`, `urls_changed: Bool` | `PreloadService.swift` — after `detectURLChanges()` |
| `pdf_url_scrape_failed` | `error: String` | `PDFURLScrapingService.swift` — on network/parsing failure |

**Context:** The scraping service GETs `https://cofsegovia.com/farmacias-de-guardia/`, extracts `.pdf` links via regex, and maps each URL to one of four regions: `segovia-capital`, `cuellar`, `el-espinar`, `segovia-rural`. Fewer than 4 URLs found is already treated as a partial failure.

### PDF Parsing (per region)

Parsing flows through `PDFProcessingService` → region-specific `PDFParsingStrategy`. Results are stored via `ScheduleCacheService`.

| Event | Props | Where to fire (iOS) |
|---|---|---|
| `schedules_parsed` | `region: String`, `schedules_count: Int`, `zbs_count: Int?` | `PDFProcessingService.swift` — after successful parse |
| `schedules_loaded_from_cache` | `region: String`, `schedules_count: Int` | `ScheduleService.swift` — when persistent cache hit |
| `pdf_parse_failed` | `region: String`, `error: String` | `PDFProcessingService.swift` — on parser exception |

**Context — region details:**
- `segovia-capital` — `SegoviaCapitalParser`: produces day-shift + night-shift schedules (`.capitalDay` / `.capitalNight`); 3-column coordinate-based PDF
- `cuellar` — `CuellarParser`: produces full-day shifts; 2-column
- `el-espinar` — `ElEspinarParser`: same pattern as Cuéllar
- `segovia-rural` — `SegoviaRuralParser`: most complex; scans 8 simultaneous columns for 8 ZBS healthcare areas (Cantalejo hardcoded, La Granja has weekly alternation logic); produces `[DutyLocation: [PharmacySchedule]]` with 8 entries

The `schedules_count` prop is the total number of `PharmacySchedule` objects produced. `zbs_count` applies only to `segovia-rural` (always 8 if successful).

### UI Interactions

> **Note:** Aptabase is not a session-replay tool — only explicit, meaningful interactions should be tracked. Avoid tracking every tap.

| Event | Props | Where to fire (iOS) |
|---|---|---|
| `region_selected` | `region: String` | View where region is tapped |
| `open_in_maps_tapped` | `map_app: String` (e.g. `"apple"`, `"google"`) | `PharmacyView.swift` or routing action |
| `cache_refresh_triggered` | `region: String` | `CacheRefreshView.swift` |
| `pdf_viewed` | `region: String` | `PDFViewScreen.swift` |
| `zbs_selected` | `zbs: String` | `ZBSSelectionView.swift` |

---

## Open Decisions

1. **Consent sharing**: ~~same toggle as Bugsink, or separate?~~ **DECIDED: separate toggle.** `MonitoringConsentView` and `MonitoringPreferencesService` need updating on both platforms to add the `analytics_enabled` / `analytics_choice_made` preference keys alongside the existing `monitoring_enabled` ones.
2. **Anonymous device ID**: Aptabase generates an anonymous session ID by default. No PII collected; no action needed unless you want to disable it entirely.
3. **Offline queuing**: Aptabase SDK queues events locally and flushes when connectivity returns — this is the default behaviour, nothing to configure.
4. **Retention**: configure on the Aptabase server side (Docker Compose env var `APTABASE_DATA_RETENTION_DAYS`). Recommended: 365 days.
5. **Self-hosted URL**: once deployed on the Pi, add the base URL to `Secrets.swift` (e.g. `aptabaseHost: "https://aptabase.yourdomain.dev"`).
