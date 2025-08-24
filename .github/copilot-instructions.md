# Farmacias de Guardia - AI Development Guidelines

## Architecture Overview

This is a **cross-platform pharmacy duty schedule app** for Segovia, Spain. The iOS app is complete; Android is in active migration.

### Key Architecture Pattern: Strategy-Based PDF Processing

The core innovation is a **Strategy Pattern** for parsing region-specific PDF formats:

```
PDFProcessingService
├── PDFParsingStrategy (protocol/interface)  
├── ColumnBasedPDFParser (base class)
└── Region parsers:
    ├── SegoviaCapitalParser (3-column: dates, day/night shifts)
    ├── CuellarParser (2-column: dates, pharmacies)
    └── SegoviaRuralParser (complex ZBS healthcare areas)
```

**Example**: iOS `SegoviaCapitalParser` uses coordinate-based extraction:
- Defines page margins (40px), column widths (22% dates, remaining split for shifts)
- Extracts text from specific PDF areas using `PDFKit`
- Parses into `PharmacySchedule` objects with day/night shifts

### Platform Status & Guidelines

**iOS** (`ios/FarmaciasDeGuardiaEnSegovia/`): ✅ **Complete - DO NOT MODIFY**
- Swift/SwiftUI with ObservableObject pattern
- Strategy registration in `PDFProcessingService.registerDefaultParsers()`
- Conditional debug logging via `DebugConfig.debugPrint()`

**Android** (`android/app/src/main/java/.../`): 🔄 **Active Migration**
- Kotlin/Jetpack Compose + Material3 
- Currently uses iText7 for PDF processing (vs iOS PDFKit)
- Mirrors iOS architecture but incomplete feature set

### Critical Development Patterns

#### PDF Processing Implementation
1. **Strategy Registration**: Register parsers in service constructor by region ID
2. **Column Extraction**: Use coordinate-based text extraction with defined margins/widths
3. **Caching**: Always cache parsed results to avoid reprocessing same PDFs
4. **Error Handling**: Graceful fallbacks when PDF structure changes

Example from iOS `SegoviaCapitalParser`:
```swift
let dateColumnWidth = contentWidth * 0.22  // 22% for dates
let pharmacyColumnWidth = (contentWidth - dateColumnWidth) / 2
```

#### Debug Control Pattern
Both platforms use centralized debug configuration:
- iOS: `DebugConfig.debugPrint()` with environment variable override
- Android: Mirror this pattern for consistent logging control

### Build Commands
- **iOS**: `xcodebuild test -scheme FarmaciasDeGuardiaEnSegovia -destination 'platform=iOS Simulator,name=iPhone 16 Pro'`
- **Android**: `cd android && ./gradlew assembleDebug`

### Migration Guidelines
- **Reference**: Use `android_full_migration_backup/` cautiously (has configuration errors)
- **Priority**: iOS implementation always takes precedence for feature behavior
- **Architecture**: Maintain Strategy pattern, service-based design, similar data models

### Region-Specific Business Logic
- **Segovia Capital**: 3-column PDF with day/night pharmacy shifts
- **Cuéllar/El Espinar**: 2-column PDF with full-day pharmacy assignments  
- **Segovia Rural**: Complex ZBS (healthcare area) subdivision with 8 sub-regions
- **All regions**: Date parsing handles Spanish format ("lunes, 15 de julio de 2025")

Before making changes, explain scope and ask for confirmation. Focus on incremental development matching iOS patterns.
