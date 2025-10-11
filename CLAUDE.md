# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Farmacias de Guardia Segovia** is a cross-platform mobile app (iOS + Android) providing real-time duty pharmacy schedules for Segovia, Spain. The app parses official PDF documents from health authorities and displays pharmacy duty information in a mobile-friendly format.

**Platform Status:**
- **iOS**: ✅ Complete - both platforms now receive improvements and updates
- **Android**: ✅ Complete - feature parity achieved with iOS

## Core Architecture

### Strategy Pattern for PDF Processing

The app's core innovation is a **Strategy Pattern** for parsing region-specific PDF formats. Each region has different PDF layouts requiring specialized parsing logic:

```
PDFProcessingService (coordinator)
├── PDFParsingStrategy (protocol/interface)
├── ColumnBasedPDFParser (base class)
└── Region-specific parsers:
    ├── SegoviaCapitalParser (3-column: dates, day shifts, night shifts)
    ├── CuellarParser (2-column: dates, pharmacies)
    ├── ElEspinarParser (2-column: dates, pharmacies)
    └── SegoviaRuralParser (complex ZBS healthcare area handling)
```

**Strategy Registration:**
- iOS: `PDFProcessingService.registerDefaultParsers()` (see `ios/FarmaciasDeGuardiaEnSegovia/Services/PDFProcessingService.swift:34`)
- Android: Similar pattern in `PDFProcessingService` constructor
- Parsers are mapped by region ID: `"segovia-capital"`, `"cuellar"`, `"el-espinar"`, `"segovia-rural"`

### Three-Tier Caching Architecture

**Performance optimization through layered caching:**

1. **Memory Cache** (fastest): In-memory dictionary in `ScheduleService.cachedSchedules`
2. **Persistent Cache** (fast): JSON files managed by `ScheduleCacheService`
   - Validates cache by comparing PDF modification dates
   - Dramatically reduces app startup time (avoids PDF re-parsing)
   - Location: `Documents/ScheduleCache/`
3. **PDF Parsing** (slowest): Fallback when caches are invalid or empty

**Cache Flow (iOS example):**
- See `ios/FarmaciasDeGuardiaEnSegovia/Services/ScheduleService.swift:26-106`
- Memory → Persistent → PDF parsing
- Validates PDF modification timestamps to detect stale caches

### Offline Support Architecture

**Comprehensive offline experience:**

1. **NetworkMonitor Service** (`ios/FarmaciasDeGuardiaEnSegovia/Services/NetworkMonitor.swift`)
   - Real-time network connectivity monitoring
   - Observable pattern for reactive UI updates
   - Used throughout app to show offline warnings

2. **Route Caching** (`ios/FarmaciasDeGuardiaEnSegovia/Services/RouteCacheService.swift`)
   - Location-aware route caching with distance-based invalidation
   - Routes invalidate when user moves >300m from cached origin
   - 24-hour cache expiration for freshness
   - Stores: distance, travel time, walking time
   - Location: `Documents/RouteCache/routes.json`

3. **PDF URL Validation** (`ios/FarmaciasDeGuardiaEnSegovia/Services/PDFURLValidator.swift`)
   - Validates PDF URLs before opening (HTTP HEAD requests)
   - 1-hour validation result caching
   - Automatic URL scraping on validation failure
   - Offline-aware (respects NetworkMonitor)

4. **UI Indicators**:
   - **OfflineWarningCard**: Orange warning shown when offline
   - **CacheFreshnessFooter**: Shows cache age timestamp
   - **Enhanced Empty States**: Different messages for offline vs normal empty states
   - **Loading Overlays**: Smart loading indicators only shown when needed

**Cache Maintenance:**
- Automatic cleanup on app startup (`FarmaciasDeGuardiaEnSegoviaApp.swift:65-76`)
- PDF URL validation cache: cleared on app start
- Coordinate cache: maintenance via GeocodingService

### Region Data Model

Each region has unique characteristics:

```swift
Region {
    id: String              // Maps to parser: "segovia-capital", "cuellar", etc.
    name: String            // Display name
    icon: String            // Emoji icon
    pdfURL: URL            // Official PDF source (dynamic via scraping service)
    metadata: RegionMetadata
}
```

**Regions:**
- 🏛 **Segovia Capital**: 3-column PDF with day/night pharmacy shifts
- 🌳 **Cuéllar**: 2-column PDF with weekly schedules
- ⛰ **El Espinar / San Rafael**: 2-column PDF with weekly schedules
- 🚜 **Segovia Rural**: Complex ZBS (healthcare area) subdivision with 8 sub-regions

### PDF URL Management

Dynamic PDF URL scraping system:
- `PDFURLScrapingService` scrapes official website for latest PDF links
- `PDFURLRepository` provides fallback static URLs
- `Region` entities use scraped URLs with fallback to hardcoded URLs
- iOS: See `ios/FarmaciasDeGuardiaEnSegovia/Services/PDFURLRepository.swift`

## Build & Test Commands

### iOS (Swift/SwiftUI)
```bash
# Build
xcodebuild -scheme FarmaciasDeGuardiaEnSegovia -project ios/FarmaciasDeGuardiaEnSegovia.xcodeproj

# Run tests
xcodebuild test -scheme FarmaciasDeGuardiaEnSegovia -destination 'platform=iOS Simulator,name=iPhone 16 Pro'

# Build for device (requires signing)
xcodebuild -scheme FarmaciasDeGuardiaEnSegovia -configuration Release -sdk iphoneos
```

### Android (Kotlin/Jetpack Compose)
```bash
# Build debug APK
cd android && ./gradlew assembleDebug

# Build release AAB (for Play Store)
cd android && ./gradlew bundleRelease

# Run tests
cd android && ./gradlew test

# Run instrumented tests (requires emulator/device)
cd android && ./gradlew connectedAndroidTest

# Clean build
cd android && ./gradlew clean
```

## Key Services & Data Flow

### ScheduleService (Three-Tier Cache Coordinator)
**iOS**: `ios/FarmaciasDeGuardiaEnSegovia/Services/ScheduleService.swift`

Main methods:
- `loadSchedules(for: Region, forceRefresh: Bool)` - Load schedules with caching
- `clearCache(for: Region)` - Clear region-specific cache
- `findCurrentSchedule(in: [PharmacySchedule], for: Region)` - Get current duty schedule

### PDFProcessingService (Strategy Pattern Coordinator)
**iOS**: `ios/FarmaciasDeGuardiaEnSegovia/Services/PDFProcessingService.swift`

- Maintains registry: `parsingStrategies: [String: PDFParsingStrategy]`
- `register(parser:, for:)` - Register region-specific parser
- `loadPharmacies(for: Region)` - Parse PDF using appropriate strategy

### ScheduleCacheService (Persistent Storage)
**iOS**: `ios/FarmaciasDeGuardiaEnSegovia/Services/ScheduleCacheService.swift`

- Saves to: `Documents/ScheduleCache/{region-id}.json`
- Metadata tracking: `{region-id}.meta.json` (includes PDF modification timestamp)
- Cache validation: Compares PDF modification date vs cache timestamp
- ZBS schedules (Segovia Rural): Separate cache file `{region-id}.zbs.json`

### ClosestPharmacyService (Location-Based Routing)
**iOS**: `ios/FarmaciasDeGuardiaEnSegovia/Services/ClosestPharmacyService.swift`

- Geocoding with caching: `GeocodingService` + `CoordinateCache`
- Route caching: `RouteCacheService` stores Apple/Google Maps routing data
- Platform-agnostic routing: Supports Apple Maps and Google Maps

### Debug Configuration

**Centralized debug logging:**
- iOS: `DebugConfig.debugPrint()` in `ios/FarmaciasDeGuardiaEnSegovia/Services/DebugConfig.swift:40`
- Toggle via `DebugConfig.isDebugEnabled` (default: `true`)
- Environment variable override: `DEBUG_ENABLED=true/false`

All services use `DebugConfig.debugPrint()` for consistent, toggleable logging.

## PDF Processing Implementation

### Column-Based Extraction Pattern

**Example: Segovia Capital Parser** (3-column layout)
```swift
// Define page layout
let pageMargin: CGFloat = 40
let contentWidth = pageWidth - (2 * pageMargin)
let dateColumnWidth = contentWidth * 0.22  // 22% for dates
let pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2

// Extract text from specific regions
let dateRect = CGRect(x: pageMargin, y: y, width: dateColumnWidth, height: rowHeight)
let dayPharmacyRect = CGRect(x: pageMargin + dateColumnWidth, y: y, ...)
let nightPharmacyRect = CGRect(x: pageMargin + dateColumnWidth + pharmacyColumnWidth, ...)
```

**Key Principles:**
1. **Coordinate-based extraction**: Define exact column boundaries
2. **Row iteration**: Process PDF line-by-line within columns
3. **Text cleaning**: Remove extra whitespace, normalize phone numbers
4. **Pharmacy parsing**: Extract name, address, phone from combined text blocks

### Parser Base Classes

**iOS**: `ios/FarmaciasDeGuardiaEnSegovia/Services/PDFParsing/`
- `PDFParsingStrategy.swift` - Protocol all parsers implement
- `ColumnBasedPDFParser.swift` - Base class for column-based layouts
- `RowBasedPDFParser.swift` - Base class for row-based layouts
- `Strategies/` - Region-specific implementations

## Development Workflow

### Collaborative Development Pattern (from .cursorrules)

When implementing features:

1. **Explain First**: Describe planned changes and reasoning
2. **Request Approval**: Ask for explicit confirmation before editing
3. **Small Increments**: Implement in short, testable chunks
4. **Test Each Change**: Use debug output and manual testing
5. **Adjust Based on Results**: Iterate based on feedback

**Format for incremental changes:**
```
## [Increment Name] Plan

### What I will do:
[Detailed explanation]

### Why this approach:
[Reasoning]

### Specific changes:
- [Bullet point 1]
- [Bullet point 2]

**May I proceed with [specific action]?**
```

### Platform Guidelines

**iOS**:
- Location: `ios/FarmaciasDeGuardiaEnSegovia/`
- Swift/SwiftUI with ObservableObject pattern
- PDF library: PDFKit
- **PDF Parsing**: Coordinate-based region scanning (extracts text from specific PDF coordinates)
- Structure: Models/, Services/, Views/

**Android**:
- Location: `android/app/src/main/java/com/github/bfollon/farmaciasdeguardiaensegovia/`
- Shares core architecture patterns with iOS (Strategy pattern, service-based design)
- PDF library: iText7
- **PDF Parsing**: Text-based extraction (reads PDF text directly without coordinates)
  - Coordinate-based parsing was too computationally intensive on Android
  - iOS may migrate to text-based approach in the future
- UI: Jetpack Compose + Material3
- Package structure:
  - `ui/` - Screens, components, viewmodels
  - `services/` - Business logic (similar to iOS Services/)
  - `data/` - Data models
  - `repositories/` - Data access layer

**Note**: While both platforms share the same Strategy Pattern architecture and service-based design, the PDF parsing implementations are **not 1:1**. iOS uses coordinate-based region scanning while Android uses direct text extraction for performance reasons.

## Data Models

### Core Models (iOS reference)
- **PharmacySchedule**: Links dates to shift-based pharmacy assignments
  - `date: DutyDate`
  - `shifts: [DutyTimeSpan: [Pharmacy]]`
- **Pharmacy**: Extracted pharmacy information
  - `name: String`
  - `address: String`
  - `phone: String?`
  - `formattedPhone: String` (computed)
- **DutyDate**: Date representation with Spanish month parsing
- **DutyTimeSpan**: Shift types (`.capitalDay`, `.capitalNight`, `.fullDay`)
- **ZBSSchedule**: Segovia Rural specific - healthcare area schedules

### Region-Specific Business Logic

**Segovia Capital**:
- Day shift: 9:30 AM - 10:00 PM
- Night shift: 10:00 PM - 9:30 AM (next day)
- Three-column PDF parsing

**Cuéllar / El Espinar**:
- 24-hour full-day shifts
- Weekly rotation
- Two-column PDF parsing

**Segovia Rural**:
- 8 ZBS (healthcare areas): Cantalejo, Carbonero, Nava, Riaza, Sacramenia, Santa María, Sepúlveda, Villacastín
- Complex multi-ZBS PDF with shared/separate schedules
- Requires ZBS selection before viewing schedules

## Git Commit Guidelines

**DO NOT include Claude Code promotional text in commit messages.**

Never add the following to any commits:
```
🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

Keep commit messages clean and professional without AI tool attribution.

## Project Context Notes

- **"Vibe coded"**: Built primarily with AI assistance (see README.md)
- **Learning project**: First serious iOS/Android app from creator
- **GPL-v3 licensed**: All files include copyright header
- Not a professional iOS/Android developer work - expect unconventional patterns
- Focus on practical functionality over perfect architecture
- Don't commit changes until they have been validated by the user.