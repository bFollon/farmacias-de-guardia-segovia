---
applyTo: '**'
---

# Farmacias de Guardia - Segovia

## About the project
This project creates cross-platform apps that display pharmacy duty schedules for Segovia, Spain. The iOS app is fully functional; Android is currently in incremental migration to achieve feature parity.

## Architecture Overview

### Multi-Platform Structure
- **iOS**: Swift/SwiftUI app in `ios/` (complete, stable)
- **Android**: Kotlin/Jetpack Compose app in `android/` (in development)
- **Migration Backup**: Reference implementation in `android_full_migration_backup/` (failed attempt, use with caution)

### Core Service Pattern (Strategy-based PDF Processing)
The app uses a **Strategy Pattern** for region-specific PDF parsing:

```
PDFProcessingService
├── PDFParsingStrategy (protocol/interface)
├── ColumnBasedPDFParser (base class)
└── Region-specific parsers:
    ├── SegoviaCapitalParser (3-column: dates, day shifts, night shifts)
    ├── CuellarParser (2-column: dates, pharmacies)
    ├── ElEspinarParser (2-column: dates, pharmacies) 
    └── SegoviaRuralParser (complex ZBS handling)
```

### Key Data Models
- **Region**: Core entity with `id`, `name`, `icon`, `pdfURL`, `metadata`
- **PharmacySchedule**: Links dates to shift-based pharmacy assignments
- **Pharmacy**: Extracted from PDF text with name/address parsing
- **DutyTimeSpan/DutyDate**: Time and date representations

### PDF Processing Architecture
1. **Strategy Registration**: Each region has a dedicated parser class
2. **Column-based Extraction**: Uses coordinate-based text extraction from PDF areas
3. **Caching**: Results cached to avoid re-parsing the same PDF
4. **Optimization**: Batch processing and pre-compiled regex patterns

### Current Status
- ✅ **iOS App**: Fully functional with all features implemented in SwiftUI. **DO NOT MODIFY UNLESS SPECIFICALLY ASKED TO.**
- ✅ **Android Basic Structure**: Basic working app with navigation using Jetpack Compose + Material3
- ✅ **Android Splash Screen**: Enhanced splash screen with iOS-matching animations, gradient text, progress indicator, and region emoji progression (🏙🌳🏔️🚜)
- ✅ **Android Main Screen**: Complete main screen with region selection grid (4 regions: Segovia Capital, Cuéllar, El Espinar, Segovia Rural)
- ✅ **Android ZBS Selection**: ZBS (Zona Básica de Salud) selection screen for Segovia Rural with 8 sub-areas
- ✅ **Android Navigation**: Navigation between splash → main → ZBS selection screens
- ✅ **Android Theme**: iOS-matching color scheme (blue/green) and Material3 design
- 🔄 **Android Migration**: Currently migrating iOS features to Android incrementally
- 📍 **Next Steps**: Implement PDF view screen and pharmacy schedule parsing
- 🎯 **Goal**: Feature parity between iOS and Android apps

## WORKING INSTRUCTIONS FOR COPILOT AGENT **FOLLOW THESE**

### Development Workflow
- **Before implementing changes**, explain the purpose and scope of the changes to be made, and ask for user confirmation every time. Reason through any significant design decisions or changes.
- **Small incremental changes**: Development should be done in small testable increments. Tasks should always limit their scope to a single feature or bug fix.
- **SOLID principles**: When developing, follow SOLID principles, ensure that code is modular and reusable, avoid magic numbers and strings, and make constants configurable.

### Platform-Specific Guidelines

#### iOS Development (Swift/SwiftUI)
- **Location**: All code in `ios/FarmaciasDeGuardiaEnSegovia/`
- **Key patterns**: Strategy pattern for PDF parsing, ObservableObject for state management, environmental values for theming
- **Testing**: Use `xcodebuild test -scheme FarmaciasDeGuardiaEnSegovia -destination 'platform=iOS Simulator,name=iPhone 16 Pro'`
- **Debug control**: Use `DebugConfig.debugPrint()` for conditional logging

#### Android Development (Kotlin/Jetpack Compose)
- **Location**: Main code in `android/app/src/main/java/com/example/farmaciasdeguardiaensegovia/`
- **Key patterns**: Mimic iOS architecture - Strategy pattern for PDF parsing, Repository pattern for data access
- **PDF Libraries**: Current Android uses iText7, migration backup used PDFBox (both valid approaches)
- **Build**: `./gradlew assembleDebug` from `android/` directory
- **Debug control**: Mirror iOS `DebugConfig` class patterns

### PDF Processing Strategy Architecture
When working with PDF parsing:
1. **Strategy Registration**: Register parsers in `PDFProcessingService` constructor using region IDs
2. **Column-based Extraction**: Use coordinate-based extraction - define page margins, column widths, and extraction areas
3. **Parser Implementation**: Extend `ColumnBasedPDFParser`, implement region-specific layout handling
4. **Caching**: Always implement caching to avoid re-parsing same files
5. **Error Handling**: Graceful degradation when PDF structure changes

### Status Updates
- After completing any feature or significant development milestone, update the "Current Status" section in this instructions file.
- Mark completed features with ✅ and update the "Next Steps" section accordingly.
- This ensures the instructions always reflect the current state of the project.

### Migration Guidelines
- When migrating features from iOS to Android, ensure that the Android implementation matches the iOS functionality as closely as possible.
- Use `./android_full_migration_backup` as a reference or starting point, although **iOS implementation always takes precedence**.
- Note that the migration backup had theme and configuration errors, so be careful when using it.
- **Focus on architecture patterns**: Strategy pattern for PDF parsing, service-based architecture, similar data models

### Build & Test Commands
- **iOS**: `xcodebuild test -scheme FarmaciasDeGuardiaEnSegovia -destination 'platform=iOS Simulator,name=iPhone 16 Pro'`
- **Android**: `cd android && ./gradlew assembleDebug`
- **Android Test**: `cd android && ./gradlew test`

## Core functionalities
- **Multi-region support**: 4 regions (Segovia Capital, Cuéllar, El Espinar, Segovia Rural) with different PDF formats and parsing strategies
- **Strategy-based PDF processing**: Each region uses dedicated parser classes with coordinate-based text extraction 
- **ZBS (Zona Básica de Salud) support**: Segovia Rural subdivides into 8 healthcare areas with specialized schedule handling
- **Shift-based scheduling**: Day/night shifts for Segovia Capital, full-day for other regions, time spans for rural areas
- **Caching system**: PDF parsing results cached to avoid reprocessing, with cache invalidation support
- **Location services**: Closest pharmacy detection using device GPS with proximity-based pharmacy suggestions
- **Map integration**: Pharmacy locations with routing via Apple Maps (iOS) / Google Maps/Waze (Android)
- **Date picker navigation**: Select specific dates for pharmacy schedule viewing
- **Dark/light mode**: Theme support following system preferences
- **Performance optimizations**: Batch processing, pre-compiled regex patterns, optimized PDF column extraction

## Region Details
- **🏙 Segovia Capital**: Urban area  
  📄 [PDF Schedule](https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf)

- **🌳 Cuéllar**: Natural region  
  📄 [PDF Schedule](https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf)

- **⛰ El Espinar / San Rafael**: Mountain region  
  📄 [PDF Schedule](https://cofsegovia.com/wp-content/uploads/2025/01/Guardias-EL-ESPINAR_2025.pdf)

- **🚜 Segovia Rural**: Rural emergency services with ZBS (Basic Health Area)
  📄 [PDF Schedule](https://cofsegovia.com/wp-content/uploads/2025/06/SERVICIOS-DE-URGENCIA-RURALES-2025.pdf)
  
  ### ZBS Areas within Segovia Rural:
  - **🏔️ Riaza / Sepúlveda**: Mountain highland area
  - **🏰 La Granja**: Historic palace town area  
  - **⛰️ La Sierra**: Mountain range area
  - **🏞️ Fuentidueña**: Valley countryside area
  - **🌲 Carbonero**: Forest region area
  - **🏘️ Navas de la Asunción**: Small town area
  - **🚂 Villacastín**: Railway junction town
  - **🏘️ Cantalejo**: Rural town area

## Data Sources
- Official PDF schedules from Colegio de Farmacéuticos de Segovia (cofsegovia.com)
- Real-time parsing of pharmacy duty rotations
- Geocoding services for pharmacy address resolution

## Project requirements
- The app should be implemented for both Android and iOS platforms.
- Both apps should be native.
- The iOS app should be developed using Swift and SwiftUI.
- The Android app should be developed using Kotlin, Material 3 and Jetpack Compose.