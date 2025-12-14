# Implementation Plan: Next Shift Display & Transition Warning

## Overview

Add two features to show users information about the upcoming pharmacy shift:
1. **"Siguiente turno" card** - Display the next pharmacy shift after the current one
2. **Shift transition warning** - Alert when within 30 minutes of shift change (shown only on current pharmacy card)

This plan is platform-agnostic and designed for reuse on iOS.

---

## Feature Requirements

### Next Shift Display
- Show pharmacy information for the shift immediately following the current one
- Handle cross-day transitions (night shift → next day's day shift)
- Display all pharmacies on duty for that shift
- Position after current pharmacy cards in the UI

### Shift Transition Warning
- Show warning card only when user is within 30 minutes of current shift ending
- Display on current pharmacy card only (not on next shift card)
- Show minutes until change and suggest considering next pharmacy
- Orange-themed alert card for visibility

---

## Implementation Details

### 1. Service Layer - ScheduleService.kt

**File**: `android/app/src/main/java/com/github/bfollon/farmaciasdeguardiaensegovia/services/ScheduleService.kt`

**Refactor existing method**: Rename `findCurrentSchedule()` → `findScheduleForTimestamp()`

```kotlin
// Rename existing method and add timestamp parameter (default to now)
fun findScheduleForTimestamp(
    schedules: List<PharmacySchedule>,
    timestamp: Long = System.currentTimeMillis()
): Pair<PharmacySchedule, DutyTimeSpan>? {
    // Use the provided timestamp instead of hardcoded System.currentTimeMillis()
    val matchingSchedule = schedules.find {
        it.shifts.entries.find { (timeSpan, _) ->
            timeSpan.contains(it.date, timestamp)
        } != null
    }

    return matchingSchedule?.let {
        Pair(it, it.shifts.entries.find { (key, _) ->
            key.contains(it.date, timestamp)
        }?.key!!)
    } ?: run {
        // Fallback: same-day matching
        val existingSchedule = schedules.find {
            it.shifts.entries.find { (timeSpan, _) ->
                timeSpan.isSameDay(it.date, timestamp)
            } != null
        }
        existingSchedule?.let {
            Pair(it, it.shifts.entries.find { (key, _) ->
                key.isSameDay(it.date, timestamp)
            }?.key!!)
        }
    }
}

// Convenience method for current schedule (backward compatibility)
fun findCurrentSchedule(schedules: List<PharmacySchedule>): Pair<PharmacySchedule, DutyTimeSpan>? {
    return findScheduleForTimestamp(schedules, System.currentTimeMillis())
}
```

**Add new method: `findNextSchedule()`**

```kotlin
fun findNextSchedule(
    schedules: List<PharmacySchedule>,
    currentSchedule: PharmacySchedule?,
    currentTimeSpan: DutyTimeSpan?
): Pair<PharmacySchedule, DutyTimeSpan>? {
    if (currentSchedule == null || currentTimeSpan == null) return null

    // Calculate timestamp for end of current shift
    val currentDate = currentSchedule.date
    val shiftDate = LocalDate.of(
        currentDate.year!!,
        DutyDate.monthToNumber(currentDate.month)!!,
        currentDate.day
    )

    val shiftEndTime = if (currentTimeSpan.spansMultipleDays) {
        // Night shift ending next day (e.g., 22:00 Dec 11 → 10:15 Dec 12)
        LocalDateTime.of(shiftDate.plusDays(1), LocalTime.of(currentTimeSpan.endHour, currentTimeSpan.endMinute))
    } else {
        // Day shift ending same day (e.g., 10:15 → 22:00)
        LocalDateTime.of(shiftDate, LocalTime.of(currentTimeSpan.endHour, currentTimeSpan.endMinute))
    }

    // Add 1 minute to shift end to get timestamp that falls in next shift
    val nextShiftTimestamp = shiftEndTime.plusMinutes(1)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    // Reuse existing logic to find schedule for that timestamp
    return findScheduleForTimestamp(schedules, nextShiftTimestamp)
}
```

**Why This Works**:
1. **Leverages existing logic**: `findScheduleForTimestamp()` already handles all edge cases
2. **Handles midnight crossing**: `DutyTimeSpan.spansMultipleDays` correctly calculates end date
3. **Simple and elegant**: Just calculate next timestamp and reuse search logic
4. **Automatic fallback**: If no exact match, falls back to same-day matching (existing behavior)

**Edge Cases Handled Automatically**:
- ✅ Same-day transitions (Day 10:15-22:00 → Night 22:00-10:15): Works because we add 1 minute to 22:00 = 22:01
- ✅ Midnight-crossing (Night 22:00-10:15 → Day 10:15-22:00): Works because `plusDays(1)` gives correct end date
- ✅ No next schedule: Returns null if `findScheduleForTimestamp()` finds nothing
- ✅ 24-hour shifts: Adding 1 minute to 23:59 gives next day 00:00

---

### 2. ViewModel - ScheduleViewModel.kt

**File**: `android/app/src/main/java/com/github/bfollon/farmaciasdeguardiaensegovia/ui/viewmodels/ScheduleViewModel.kt`

**Add to ScheduleUiState**:
```kotlin
val nextSchedule: PharmacySchedule? = null,
val nextTimeSpan: DutyTimeSpan? = null,
val minutesUntilShiftChange: Long? = null,
val showShiftTransitionWarning: Boolean = false
```

**Modify `loadSchedules()` method** (after line 91):
```kotlin
// Find next schedule
val nextInfo = scheduleService.findNextSchedule(schedules, currentInfo?.first, currentInfo?.second)

// Calculate minutes until current shift ends
val minutesUntilChange = currentInfo?.second?.let {
    calculateMinutesUntilShiftEnd(it)
}

// Show warning if within 30 minutes
val showWarning = minutesUntilChange != null && minutesUntilChange <= 30

// Update state with new properties
_uiState.value = _uiState.value.copy(
    // ... existing properties ...
    nextSchedule = nextInfo?.first,
    nextTimeSpan = nextInfo?.second,
    minutesUntilShiftChange = minutesUntilChange,
    showShiftTransitionWarning = showWarning
)
```

**Add helper method: `calculateMinutesUntilShiftEnd()`**:
```kotlin
private fun calculateMinutesUntilShiftEnd(timeSpan: DutyTimeSpan): Long {
    val now = LocalDateTime.now()
    val currentMinutes = now.hour * 60 + now.minute
    val endMinutes = timeSpan.endHour * 60 + timeSpan.endMinute

    return if (timeSpan.spansMultipleDays) {
        // Night shift crossing midnight (e.g., 22:00 → 10:15)
        if (currentMinutes >= (timeSpan.startHour * 60 + timeSpan.startMinute)) {
            // Currently in "today" portion (after 22:00)
            val minutesUntilMidnight = (24 * 60) - currentMinutes
            minutesUntilMidnight + endMinutes
        } else {
            // Currently in "tomorrow" portion (before 10:15)
            endMinutes - currentMinutes
        }
    } else {
        // Same-day shift (e.g., 10:15 → 22:00)
        endMinutes - currentMinutes
    }
}
```

**Examples**:
- At 21:45 in day shift (ends 22:00): `1320 - 1305 = 15 minutes` → Show warning
- At 23:00 in night shift (ends 10:15): `(1440-1410) + 615 = 645 minutes` → No warning
- At 10:00 in night shift (ends 10:15): `615 - 540 = 75 minutes` → No warning

---

### 3. UI Components

#### New Component: NextShiftCard.kt

**File**: `android/app/src/main/java/com/github/bfollon/farmaciasdeguardiaensegovia/ui/components/NextShiftCard.kt`

```kotlin
@Composable
fun NextShiftCard(
    timeSpan: DutyTimeSpan,
    pharmacies: List<Pharmacy>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: "Siguiente turno" label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, tint = secondary, size = 20.dp)
                Text("Siguiente turno", style = labelLarge, color = secondary)
            }

            // Shift info (e.g., "Nocturno • 22:00 - 10:15")
            Text(
                "${timeSpan.displayName} • ${timeSpan.displayFormat}",
                style = titleMedium,
                fontWeight = SemiBold
            )

            // Pharmacy cards (reuse existing PharmacyCard)
            pharmacies.forEach { pharmacy ->
                PharmacyCard(pharmacy, isActive = false)
            }
        }
    }
}
```

#### New Component: ShiftTransitionWarningCard.kt

**File**: `android/app/src/main/java/com/github/bfollon/farmaciasdeguardiaensegovia/ui/components/ShiftTransitionWarningCard.kt`

```kotlin
@Composable
fun ShiftTransitionWarningCard(
    minutesUntilChange: Long,
    nextShift: DutyTimeSpan,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)  // Light orange
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, tint = Color(0xFFFF9800), size = 24.dp)

            Column {
                Text(
                    "El turno cambia pronto",
                    style = titleSmall,
                    fontWeight = SemiBold,
                    color = Color(0xFF5D4037)  // Dark brown
                )
                Text(
                    "En $minutesUntilChange minutos comienza el ${nextShift.displayName.lowercase()}. " +
                    "Considera la farmacia del siguiente turno.",
                    style = bodySmall,
                    color = Color(0xFF5D4037)
                )
            }
        }
    }
}
```

---

### 4. UI Integration - ScheduleScreen.kt

**File**: `android/app/src/main/java/com/github/bfollon/farmaciasdeguardiaensegovia/ui/screens/ScheduleScreen.kt`

**Modify LazyColumn layout** (around lines 514-556, after ShiftHeaderCard):

```kotlin
// Current shift header
item {
    ShiftHeaderCard(
        uiState.activeTimeSpan!!,
        isActive = uiState.activeTimeSpan.isActiveNow(),
        onInfoClick = { ... }
    )
}

// NEW: Shift transition warning (if within 30 min)
if (uiState.showShiftTransitionWarning &&
    uiState.minutesUntilShiftChange != null &&
    uiState.nextTimeSpan != null) {
    item {
        ShiftTransitionWarningCard(
            minutesUntilChange = uiState.minutesUntilShiftChange!!,
            nextShift = uiState.nextTimeSpan!!
        )
    }
}

// Current pharmacy cards
pharmacies.forEach { pharmacy ->
    item { PharmacyCard(pharmacy, isActive = true) }
}

// NEW: Next shift section
if (uiState.nextSchedule != null && uiState.nextTimeSpan != null) {
    uiState.nextSchedule!!.shifts[uiState.nextTimeSpan]?.let { nextPharmacies ->
        if (nextPharmacies.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                NextShiftCard(
                    timeSpan = uiState.nextTimeSpan!!,
                    pharmacies = nextPharmacies
                )
            }
        }
    }
}
```

**Visual Order** (top to bottom):
1. Current shift header (ShiftHeaderCard with "Activo ahora")
2. ⚠️ Shift transition warning (only if within 30 min)
3. Current pharmacy card(s) (with "Activa ahora" badge)
4. 16dp spacer
5. 🕐 Next shift card (with "Siguiente turno" label and pharmacy details)

---

## Critical Files Summary

### Files to Modify:
1. `android/.../services/ScheduleService.kt`
   - **Refactor**: Rename `findCurrentSchedule()` → `findScheduleForTimestamp(timestamp)` with default parameter
   - **Add**: `findNextSchedule()` method that calculates end timestamp and reuses `findScheduleForTimestamp()`
2. `android/.../viewmodels/ScheduleViewModel.kt`
   - Add state properties: `nextSchedule`, `nextTimeSpan`, `minutesUntilShiftChange`, `showShiftTransitionWarning`
   - Add `calculateMinutesUntilShiftEnd()` helper method
3. `android/.../screens/ScheduleScreen.kt`
   - Integrate new UI components in LazyColumn

### Files to Create:
4. `android/.../components/NextShiftCard.kt` - Display next shift info
5. `android/.../components/ShiftTransitionWarningCard.kt` - 30-minute warning

### Key Insight:
The elegant solution is to **reuse existing `findCurrentSchedule()` logic** by:
1. Calculating the end timestamp of the current shift (handling midnight crossing)
2. Adding 1 minute to get a timestamp that falls in the next shift
3. Calling the refactored `findScheduleForTimestamp()` with that timestamp
This avoids complex sorting/flattening and leverages battle-tested edge case handling.

---

## Platform Translation Notes (iOS)

### Core Algorithm (Platform-Agnostic)
The `findNextSchedule()` logic is identical across platforms:
1. Calculate end timestamp of current shift (handling midnight crossing via `spansMultipleDays`)
2. Add 1 minute to get a timestamp in the next shift
3. Reuse existing schedule-finding logic with new timestamp

### Swift Equivalents:
- `List<T>` → `[T]`
- `Pair<A, B>` → `(A, B)` tuple
- `LocalDateTime.now()` → `Date()` with `Calendar.current`
- `StateFlow` → `@Published` property
- `LazyColumn` → `List` or `LazyVStack`
- `Card` → `VStack` with `.background()` + `.cornerRadius()`

### iOS Files to Modify:
1. `ios/.../Services/ScheduleService.swift`
   - Refactor `findCurrentSchedule()` → `findScheduleForTimestamp(timestamp:)`
   - Add `findNextSchedule()`
2. `ios/.../ViewModels/ScheduleViewModel.swift`
   - Add state properties
   - Add `calculateMinutesUntilShiftEnd()`
3. `ios/.../Views/ScheduleView.swift`
   - Add SwiftUI components: `NextShiftCard`, `ShiftTransitionWarningCard`

---

## Testing Checklist

### Scenarios to Test:
- [ ] At 21:45 in day shift → Warning shows, next shift is night
- [ ] At 10:00 in night shift → Warning shows, next shift is day (same date)
- [ ] At 15:00 in day shift → No warning, next shift is night
- [ ] At 23:00 in night shift → No warning, next shift is tomorrow's day
- [ ] Last schedule in dataset → No next shift card shown
- [ ] Cuéllar (24-hour) → Next shift is tomorrow's full day
- [ ] Segovia Capital (day/night) → Proper same-day transitions

### Edge Cases:
- No schedules available
- Only one schedule exists
- Midnight-crossing calculations
- Multiple pharmacies per shift (if applicable)

---

## Implementation Order

1. **Service Layer**: Implement `findNextSchedule()` + refactor existing method
2. **ViewModel**: Add state properties + `calculateMinutesUntilShiftEnd()`
3. **UI Components**: Create NextShiftCard + ShiftTransitionWarningCard
4. **Integration**: Add to ScheduleScreen layout
5. **Testing**: Manual testing with all regions/shift types
6. **iOS Port**: Translate to Swift/SwiftUI using same algorithm
