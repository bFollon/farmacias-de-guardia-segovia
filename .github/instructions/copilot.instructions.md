---
applyTo: '**'
---

# Farmacias de Guardia - Segovia

## About the project
This project's goal is to create an application that displays the pharmacies on duty in Segovia, which is a province and a city in Spain.

## Current Status
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

## Core functionalities
- A splash screen that displays the app logo and a loading indicator.
- A home screen that shows 4 possible locations: `Segovia`, `Cuéllar`, `El espinar`, and `Segovia Rural`.
- Each region displays pharmacy duty schedules parsed from official PDF files.
- Date picker to select the desired date for pharmacy schedules.
- Map integration showing pharmacy locations with directions via Apple Maps (on iOS) /Google Maps/Waze.
- Closest pharmacy service using location services to find the nearest on-duty pharmacy.
- Link to official pharmacy PDFs for more information.
- Caching system for offline access and performance optimization of previously loaded schedules.
- Location services integration for proximity-based pharmacy suggestions.
- Dark/light mode support following system preferences.
- Each region has it's own schedule particularities, which is parsed by the app and standardized into shifts or time spans.

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

## Methodology
- **Development**
  - Before implementing changes, explain the purpose and scope of the changes to be made, and ask for user confirmation every time. Reason through any significant design decisions or changes.
  - Development should be done in small testable increments.
	- Tasks should always limit their scope to a single feature or bug fix.
	- Avoid modifying things that fall outside the scope of the task.
  - When developing, follow SOLID principles.
  - When developing, ensure that code is modular and reusable.
  - When developing, avoid magic numbers and strings.
  - When developing, make constants configurable.
- **Status Updates**
	- After completing any feature or significant development milestone, update the "Current Status" section in this instructions file.
	- Mark completed features with ✅ and update the "Next Steps" section accordingly.
	- This ensures the instructions always reflect the current state of the project.
- **Refactoring**
	- Refactoring should be done in small, incremental steps.
	- Each refactoring step should be tested to ensure it doesn't break existing functionality.
- **Compiling**
	- Swift code should be compiled manually and copilot should prompt the user to compile the code and report any errors.
	- Kotlin code should be compiled manually and copilot should prompt the user to compile the code and report any errors.
- **Migration**
	- When migrating features from iOS to Android, ensure that the Android implementation matches the iOS functionality as closely as possible.
	- Document any discrepancies between the iOS and Android implementations.
	- There is some pre-made migration work in `./android_full_migration_backup`. You may use it as a reference or starting point for your migration efforts, although iOS implementation will always take precedence.
  - Note that the migration backup did not work due to different kind of errors with themes and configuration, so be careful when using it.