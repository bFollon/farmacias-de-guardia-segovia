# Platform Notes

This document tracks known structural differences between the iOS and Android implementations
that may affect feature parity or require dual maintenance.

---

## Schedule View Structure

### iOS
The schedule view is split into **two separate views**:

| View | File | When shown |
|------|------|------------|
| `ScheduleContentView` | `ios/.../Views/ScheduleContentView.swift` | Today's date with an active schedule |
| `DayScheduleView`     | `ios/.../Views/DayScheduleView.swift`     | Any past or future date, or today when no schedule matches |

Both are hosted by `PDFViewScreen`, which owns the shared state (`schedules`, `cacheTimestamp`, `confidenceResult`) and passes them as parameters.

**Implication**: any new footer/indicator added to the schedule view must be added to **both** iOS views. See the confidence indicator (`ConfidenceIndicatorView`) as an example.

### Android
The schedule view is a **single composable**: `ScheduleScreen` in `android/.../ui/screens/ScheduleScreen.kt`. It handles all date states (today, past, future) through `ScheduleViewModel.ScheduleUiState`.

---

## PDF Parsing Strategy

| Platform | Approach | Reason |
|----------|----------|--------|
| iOS      | Coordinate-based region scanning (PDFKit) | Most accurate for known layouts |
| Android  | Text-based extraction (iText7)            | Coordinate scanning too CPU-intensive on Android |

---

## TODO

- [ ] Consider unifying iOS `ScheduleContentView` + `DayScheduleView` into a single parameterised view
      to reduce dual-maintenance burden (non-trivial: they differ in shift-transition logic and footer content).
