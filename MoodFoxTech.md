# MoodFox — Technical Plan

Mirrors the architecture of fatloss_track. Local-first, Compose-based, no ViewModels, DAOs injected directly into composables.

---

## Quick Reference

| Item | Value |
|---|---|
| Package | `com.moodfox` |
| Language | Kotlin 2.3.0 |
| UI | Jetpack Compose + Material 3 (BOM 2026.01.01) |
| DI | Hilt 2.57.2 |
| DB | Room 2.8.4, version **1** |
| HTTP | Ktor 3.0.3 (OkHttp engine) |
| Background | WorkManager |
| Localization | Android `res/values-xx/strings.xml` (English default + Slovenian + Hungarian) |
| Themes | HSL-generated `ThemePreset` enum, light + dark variants per hue |
| Build | Gradle 8.11.1, AGP 8.9.1, KSP 2.3.5 |
| SDK | compileSdk/targetSdk 36, minSdk 28 |
| JDK | 17 (`/opt/homebrew/opt/openjdk@17`) |

---

## Architecture

**Local-first, no ViewModels.** DAOs are injected directly into composables via Hilt. State is collected with `collectAsState()`. Room is the single source of truth.

```
Compose UI ←→ DAOs (Room Flows) ←→ Room Database
                                        ↑
                               WorkManager (reminders,
                               periodic weather refresh)
                                        ↑
                               WeatherService → weather API
```

No ViewModels. All state hoisting is done at the composable level, same as fatloss_track.

---

## Project Structure

```
moodfox/
├── MoodFoxPlan.md
├── MoodFoxTech.md
└── app/
    └── mobile/src/main/java/com/moodfox/
        ├── MoodFoxApp.kt              # @HiltAndroidApp, forces Locale.US
        ├── onboarding/
        │   └── WelcomeScreen.kt         # Intro/onboarding flow (shown once, skippable)
        ├── data/
        │   ├── local/
        │   │   ├── PreferencesManager.kt    # DataStore (helper, theme, reminders, weather toggle)
        │   │   ├── AppLogger.kt             # File logger (daily rotation, 7-day retention)
        │   │   └── db/
        │   │       ├── Entities.kt          # Room entities
        │   │       ├── Daos.kt              # DAOs
        │   │       ├── MoodDatabase.kt      # DB v1
        │   │       └── Converters.kt        # Instant↔Long, List↔String (JSON)
        │   └── remote/
        │       └── WeatherService.kt        # Open-Meteo or similar, no key required
        ├── di/
        │   ├── DatabaseModule.kt            # Room, all DAOs
        │   └── NetworkModule.kt             # Ktor HttpClient, WeatherService
        ├── domain/
        │   ├── MoodStats.kt                 # Rolling average, volatility, swing count
        │   └── ReminderScheduler.kt         # WorkManager wrapper
        └── ui/
            ├── MainActivity.kt              # Entry point, Hilt injections
            ├── Navigation.kt                # NavHost + bottom bar
            ├── theme/
            │   ├── Theme.kt                 # ThemePreset enum, AppColors, LocalAppColors
            │   └── Type.kt                  # Typography
            ├── checkin/
            │   ├── CheckInScreen.kt         # Mood scale, cause chips, note, save
            │   └── MoodScaleComponent.kt    # Reusable draggable scale composable
            ├── calendar/
            │   ├── CalendarScreen.kt        # Monthly grid
            │   └── DayDetailSheet.kt        # Bottom sheet with day entries
            ├── analysis/
            │   ├── AnalysisScreen.kt        # Charts, observations, filters
            │   ├── MoodLineChart.kt         # Canvas chart with -2/+2 band
            │   └── StabilityCard.kt         # Rolling avg + volatility summary
            ├── settings/
            │   ├── SettingsScreen.kt        # Main settings
            │   ├── CategoryManagerScreen.kt # Add/rename/reorder/remove causes
            │   └── LogViewerScreen.kt       # Debug log viewer
            └── components/
                ├── HelperCharacter.kt       # Animated helper with mood reaction
                └── CauseChips.kt            # Reusable cause selector chips
```

---

## Database (Room, v1)

### Entities

| Table | PK | Key Fields |
|---|---|---|
| `mood_entries` | `id` (auto) | `timestamp` (Instant→Long), `moodValue` (Int, -10..+10), `causeIds` (JSON list), `note` (nullable), `weatherSnapshotId` (nullable Long) |
| `cause_categories` | `id` (auto) | `name`, `emoji`, `sortOrder`, `isDefault`, `isActive` |
| `weather_snapshots` | `id` (auto) | `timestamp`, `city`, `temperatureC`, `condition`, `isRaining`, `humidity` |

### DAOs

| DAO | Key queries |
|---|---|
| `MoodEntryDao` | `getAll()`: Flow, `getByDateRange(from, to)`: Flow, `insert()`, `update()`, `delete()` |
| `CauseCategoryDao` | `getAll()`: Flow, `getActive()`: Flow, `insert()`, `update()`, `delete()`, `updateSortOrders()` |
| `WeatherSnapshotDao` | `insert()`, `getById()` |

All list queries return `Flow<List<T>>` so composables react to changes automatically.

### Important: Adding new columns

1. Add field to entity in `Entities.kt`
2. Bump version in `MoodDatabase.kt`
3. Add `MIGRATION_N_N+1` in `DatabaseModule.kt` with `ALTER TABLE … ADD COLUMN …`
4. Register with `.addMigrations()`

Never use `fallbackToDestructiveMigration()` — it wipes user mood history.

---

## Navigation

### Bottom tabs: `Check-in` | `Calendar` | `Analysis` | `Settings`

| Route | Screen | Notes |
|---|---|---|
| `welcome` | WelcomeScreen | Shown once on first launch, then skipped |
| `checkin` | CheckInScreen | Start destination after onboarding |
| `calendar` | CalendarScreen | |
| `analysis` | AnalysisScreen | |
| `settings` | SettingsScreen | |
| `settings/categories` | CategoryManagerScreen | From settings |
| `settings/log_viewer` | LogViewerScreen | Debug only |

The bottom bar and top chrome are hidden on `welcome`, `categories`, and `log_viewer` screens.

---

## Dependency Injection

Dependencies flow: `MainActivity` (Hilt `@Inject`) → `Navigation.kt` → individual screens as parameters. No ViewModels.

```kotlin
// MainActivity.kt
@Inject lateinit var moodEntryDao: MoodEntryDao
@Inject lateinit var causeCategoryDao: CauseCategoryDao
@Inject lateinit var weatherSnapshotDao: WeatherSnapshotDao
@Inject lateinit var preferencesManager: PreferencesManager
@Inject lateinit var weatherService: WeatherService
@Inject lateinit var reminderScheduler: ReminderScheduler
@Inject lateinit var appLogger: AppLogger
```

Pass these down to screens via `Navigation.kt`, same pattern as fatloss_track.

---

## Domain Logic

### MoodStats.kt

Stateless functions, called inside composables on collected lists:

```kotlin
fun rollingAverage(entries: List<MoodEntry>, days: Int): Float
fun volatility(entries: List<MoodEntry>): Float     // avg absolute swing between consecutive entries
fun swingCount(entries: List<MoodEntry>, threshold: Int = 4): Int  // crossings > threshold in magnitude
fun isWithinTargetBand(rollingAvg: Float): Boolean  // -2f..+2f
fun averageByWeekday(entries: List<MoodEntry>): Map<DayOfWeek, Float>
fun averageByHourBucket(entries: List<MoodEntry>): Map<String, Float>  // morning/afternoon/evening/night
fun causeFrequency(entries: List<MoodEntry>): Map<Long, Int>
fun causesForLowMoods(entries: List<MoodEntry>, threshold: Int = -3): Map<Long, Int>
fun causesForHighMoods(entries: List<MoodEntry>, threshold: Int = 3): Map<Long, Int>
```

### Target band constants

```kotlin
const val TARGET_BAND_LOW = -2f
const val TARGET_BAND_HIGH = 2f
```

---

## Weather Integration

### Strategy
- Optional. Off by default. User enables in Settings.
- On save: if enabled and location permission granted, fetch current weather and store a `WeatherSnapshot`. Link it to the `MoodEntry` via `weatherSnapshotId`.
- If fetch fails silently, entry is saved without weather context.
- Use a free no-key API such as **Open-Meteo** (`api.open-meteo.com`) — no API key required.
- Store approximate city/location string (reverse-geocoded or user-set).

### Permission flow
- Request `ACCESS_COARSE_LOCATION` only — no need for precise location.
- If denied, offer a manual city input fallback in Settings.
- Never block saving a mood entry on weather access.

---

## Reminders / Notifications

WorkManager periodic tasks, configured from Settings.

```kotlin
class MoodReminderWorker : CoroutineWorker() {
    // Posts a notification with a DeepLink to checkin route
    // Respects quiet hours stored in DataStore
}
```

- Configurable times (e.g. 09:00, 13:00, 19:00)
- Configurable frequency (2–4x per day)
- Quiet hours window
- Notification taps deep-link directly to check-in screen

---

## Preferences (DataStore)

Stored in `PreferencesManager` via Jetpack DataStore (typed Preferences):

| Key | Type | Default |
|---|---|---|
| `selectedHelper` | String | `"fox"` |
| `helperEnabled` | Boolean | `true` |
| `animationsEnabled` | Boolean | `true` |
| `reduceMotion` | Boolean | `false` |
| `weatherEnabled` | Boolean | `false` |
| `manualCity` | String? | `null` |
| `remindersEnabled` | Boolean | `false` |
| `reminderTimes` | String (JSON list) | `"[\"09:00\",\"13:00\",\"19:00\"]"` |
| `quietHoursStart` | String | `"22:00"` |
| `quietHoursEnd` | String | `"08:00"` |
| `themePreset` | String | `"PURPLE_DARK"` |
| `analysisDefaultRange` | Int (days) | `30` |
| `onboardingComplete` | Boolean | `false` |

---

## Theme & Colors

Identical HSL-based approach to fatloss_track. A `ThemePreset` enum drives all colors; every composable that needs colors reads from `LocalAppColors`.

### ThemePreset enum

```kotlin
enum class ThemeMode { DARK, LIGHT }

enum class ThemePreset(val label: String, val accentHue: Float, val mode: ThemeMode) {
    PURPLE_DARK("Purple Dark",  250f, ThemeMode.DARK),
    PURPLE_LIGHT("Purple Light", 250f, ThemeMode.LIGHT),
    BLUE_DARK("Blue Dark",     220f, ThemeMode.DARK),
    BLUE_LIGHT("Blue Light",   220f, ThemeMode.LIGHT),
    TEAL_DARK("Teal Dark",     170f, ThemeMode.DARK),
    TEAL_LIGHT("Teal Light",   170f, ThemeMode.LIGHT),
    PINK_DARK("Pink Dark",     330f, ThemeMode.DARK),
    PINK_LIGHT("Pink Light",   330f, ThemeMode.LIGHT),
    ORANGE_DARK("Orange Dark",  25f, ThemeMode.DARK),
    ORANGE_LIGHT("Orange Light", 25f, ThemeMode.LIGHT),
}
```

### AppColors

All semantic tokens are resolved per preset via `buildAppColors(accentHue, mode)` — same generator pattern as fatloss_track:

| Token | Usage |
|---|---|
| `surface` | App background |
| `cardSurface` | Card backgrounds |
| `primary` | CTAs, scale thumb, highlights |
| `secondary` | Positive mood, above-band |
| `tertiary` | Negative mood, below-band |
| `accent` | Helper character glow, target band shading |
| `onSurface` | Primary text |
| `onSurfaceVariant` | Secondary / caption text |
| `isDark` | Boolean, drives Material 3 `darkColorScheme` vs `lightColorScheme` |

Colors are derived automatically from the accent hue via HSL so dark and light variants look coherent without hard-coding every hex value.

The mood scale thumb and entry dots are colored on a gradient: `tertiary` (−10) → `onSurfaceVariant` (0) → `secondary` (+10).

Default preset: `PURPLE_DARK`.

---

## Mood Scale Component

Custom `MoodScaleComponent` composable:

- Horizontal draggable track from -10 to +10
- Snaps to integer values
- Emoji anchors at -10, -5, -2, 0, +2, +5, +10 rendered above the track
- Colored thumb that shifts color with value
- Large enough hit target for one-thumb use
- Selected value displayed numerically above the thumb

The component is a pure composable with no side effects — caller owns the state.

---

## Analysis Charts

All charts are Canvas-based composables (no third-party chart library), same approach as fatloss_track's `TrendChart.kt`.

### MoodLineChart
- Plots mood values over time
- Shaded horizontal band between y=-2 and y=+2 (target zone, `NeutralBand` color at low alpha)
- Rolling average line overlaid in `Primary` color
- Individual entry dots colored by value

### StabilityCard
- Shows 7-day and 30-day rolling averages
- Badge indicating whether each average is within the target band
- Volatility value (avg swing magnitude)
- " calm / moderate / turbulent" label based on volatility thresholds

---

## Localization

Same pattern as fatloss_track: Android resource strings in `res/values-xx/strings.xml`.

### Supported languages (MVP)

| Code | Language |
|---|---|
| (default) | English |
| `sl` | Slovenian |
| `hu` | Hungarian |

Add more by creating `res/values-xx/strings.xml` with all keys translated. Language selection follows the system locale automatically — no in-app language picker needed for MVP.

### Rules
- All user-visible strings go through `stringResource()` — never hardcode text in composables.
- Default `values/strings.xml` is always English.
- Mood scale anchor labels (+10, +5, +2, 0, -2, -5, -10) are numeric and need no translation.
- Emoji anchors need no translation.
- Analysis observation sentences are composed from string templates (`stringResource(R.string.obs_avg_below_band, avg)`) — keep them short enough to translate cleanly.
- `Locale.US` is forced in `MoodFoxApp` for number formatting only — this does **not** affect UI language.

---

## Welcome / Onboarding Screen

Shown once on first launch. Skipped on every subsequent launch (`onboardingComplete` flag in DataStore).

### Structure

A single `WelcomeScreen` composable with a pager (Compose `HorizontalPager`) of 3–4 pages:

| Page | Content |
|---|---|
| 1 | App name + animated helper character + tagline: *"How are you feeling?"* |
| 2 | Quick explanation of the mood scale (-10 to +10) with a live interactive preview |
| 3 | The stability goal: calm average between -2 and +2, no big swings |
| 4 | Pick your first helper style + theme preset (shown as a grid of color swatches) |

Final page has a **"Let's start"** button that sets `onboardingComplete = true` and navigates to `checkin`.

Each page except the last also has a **"Skip"** link that jumps straight to `checkin` without any selections — defaults are applied.

### UX rules
- The helper character is already animated on page 1 — first impression matters.
- Page 2 mood scale preview is interactive but saving is disabled — it's a feel demo, not a real entry.
- Theme swatches on page 4 apply immediately so the user sees the effect in real time.
- No account creation, no email, no sign-in. This is local data.
- Onboarding should take under 30 seconds if the user just taps through.

### Navigation

```kotlin
// In Navigation.kt, before setting startDestination:
val onboardingComplete by preferencesManager.onboardingComplete.collectAsState(initial = false)
val startDestination = if (onboardingComplete) "checkin" else "welcome"
```

---

## Backup / Export

MVP: JSON export only.

```kotlin
// Exports all mood_entries, cause_categories as a single JSON file
// Importable back into the app
// File saved to Downloads or shared via system share sheet
```

No cloud, no accounts. User picks where to save it.

---

## Logging

`AppLogger.kt` — identical pattern to fatloss_track:
- Daily rotating log files
- 7-day retention
- Viewable in `LogViewerScreen`
- Used for debugging weather fetch failures, WorkManager issues

---

## Gotchas and Rules

1. **Never use `fallbackToDestructiveMigration()`** — mood history is the entire product value.
2. **Locale.US forced in `MoodFoxApp.onCreate()`** — prevents decimal comma issues.
3. **Weather fetch is always best-effort** — wrap in try/catch, never throw to UI.
4. **All DB list queries must return Flow** — composables must react to changes without manual refresh.
5. **Saving a mood entry must never wait on weather** — fetch weather async after save.
6. **The mood scale must snap to integers** — floats look precise but add no value and complicate analysis.
7. **Cause IDs are stored as a JSON list string** — simple, no join table needed at this scale.
8. **Analysis functions are pure Kotlin** — no coroutines, no Room calls inside them. Collect data upstream, pass lists in.
9. **All user-visible strings use `stringResource()`** — never hardcode text in composables.
10. **Theme is applied at the root** — `MoodFoxTheme(preset = currentPreset) { … }` wraps the entire NavHost in `MainActivity`. Changing preset in Settings re-composes the whole tree.
11. **Onboarding is gated by a DataStore flag** — `startDestination` in `Navigation.kt` is derived from `onboardingComplete`. No separate activity, no splash screen hack.

---

## Build Commands

```bash
cd app && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :mobile:assembleDebug
```

APK output: `app/mobile/build/outputs/apk/debug/mobile-debug.apk`

---

## MVP Build Order

Following the product plan milestone order:

1. Set up Gradle project, Hilt, Room v1, DataStore
2. `res/values/strings.xml` (EN), `values-sl/`, `values-hu/` — all keys stubbed from the start
3. `Theme.kt` with `ThemePreset` enum and HSL `AppColors` generator
4. Default cause categories seeded on first launch
5. Welcome/onboarding screen (pager, helper preview, theme picker)
6. Check-in screen: mood scale, cause chips, note, save to Room
7. Calendar screen: monthly grid reading from DAO Flow
8. Day detail bottom sheet
9. Analysis screen: charts, rolling average, stability card
10. Settings: category manager, theme picker, reminder toggle
11. WorkManager reminder scheduling
12. Weather: permission flow, Open-Meteo fetch, snapshot save
13. Helper character: idle animation, mood reaction
14. Export/import
15. Log viewer, polish, empty states
