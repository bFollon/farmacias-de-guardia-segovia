# Platform Drift Tracker

This document tracks implementation differences between iOS and Android platforms. Items are marked when they achieve parity.

## Legend
- [ ] Not implemented / Needs fixing
- [x] Implemented / Fixed
- [~] Intentional difference (performance/platform-specific)

---

## PDF URL Management

### URL Scraping Behavior
- [ ] **Android optimizes scraping with HEAD validation**
  - **iOS**: Always scrapes PDF URLs on app startup
  - **Android**: Validates persisted URLs with HEAD requests first, only scrapes if validation fails
  - **Impact**: Android won't trigger `PDFURLScrapingService.scrapePDFURLs()` spans when URLs are valid
  - **Location**: `android/.../repositories/PDFURLRepository.kt:352-382`
  - **Recommendation**: Align Android with iOS behavior (always scrape), or add iOS optimization

---

## Monitoring & Telemetry

### OpenTelemetry Implementation
- [x] **Both platforms use OpenTelemetry with SigNoz**
  - iOS: `opentelemetry-swift` (version 2.3.0+)
  - Android: `io.opentelemetry:opentelemetry-*` (version 1.44.1)

### Instrumented Services
- [x] **PDFURLScrapingService** - `pdf.url.scraping` span
- [x] **GeocodingService** - `pharmacy.geocode` span
- [x] **PDFProcessingService** - `pdf.parse` span
- [x] **RoutingService** - `route.calculate` span

### User Consent Flow
- [x] **Both platforms show consent dialog after splash screen**
- [x] **Both platforms have monitoring toggle in Settings**

---

## PDF Processing

### PDF Parsing Strategy
- [~] **Intentionally different due to performance**
  - **iOS**: Coordinate-based region scanning (extracts text from specific PDF coordinates)
  - **Android**: Text-based extraction (reads PDF text directly without coordinates)
  - **Reason**: Coordinate-based parsing was too computationally intensive on Android
  - **Note**: iOS may migrate to text-based approach in the future

---

## Caching Architecture

### Three-Tier Caching
- [x] **Both platforms implement three-tier caching**
  - Memory cache → Persistent cache → Source (PDF/Network)

### Cache Validation
- [x] **Both validate cache using PDF modification timestamps**

---

## Offline Support

### Network Monitoring
- [x] **Both platforms have NetworkMonitor service**

### Route Caching
- [x] **Both platforms cache routes with location-based invalidation**
  - 300m distance threshold
  - 24-hour expiration

---

## UI/UX Differences

### Platform-Specific UI
- [~] **Intentional platform differences**
  - **iOS**: SwiftUI with iOS design patterns
  - **Android**: Jetpack Compose with Material 3
  - **Note**: UI should follow platform conventions

---

## Architecture Patterns

### Shared Patterns
- [x] **Strategy Pattern for PDF parsing** (both platforms)
- [x] **Repository Pattern** (both platforms)
- [x] **Service-based architecture** (both platforms)

---

## Known Issues / Future Work

### To Investigate
- [ ] Verify all span attributes match between platforms
- [ ] Verify error tracking attributes are consistent
- [ ] Test monitoring with actual failures (404s, network errors, etc.)

---

## How to Use This File

1. **Found a drift?** Add it to the appropriate section with `- [ ]`
2. **Fixed a drift?** Change `- [ ]` to `- [x]` and add a note
3. **Intentional difference?** Use `- [~]` and document why
4. **Add context**: Include file paths, line numbers, and recommendations

---

## Last Updated
2025-12-30 - Created drift tracker, documented URL scraping optimization difference
