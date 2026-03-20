# MoodFox — Work Plan

Concrete ordered task list. Work top to bottom. Each task is small enough to complete in one sitting.

Status markers: `[ ]` not started · `[~]` in progress · `[x]` done

---

## Phase 1 — Project Skeleton

- [ ] Create Android Gradle project (`com.moodfox`, minSdk 28, targetSdk 36)
- [ ] Set up version catalog (`libs.versions.toml`) with all dependency versions from the tech plan
- [ ] Add Hilt, Room + KSP, Compose + Material 3 BOM, DataStore, Ktor, WorkManager to `build.gradle.kts`
- [ ] Add `google-services.json` placeholder (not required yet, skip Firebase for now)
- [ ] Set JDK 17 in `gradle.properties` and `local.properties`
- [ ] Verify the project builds: `./gradlew :mobile:assembleDebug`
- [ ] Create `MoodFoxApp.kt` — `@HiltAndroidApp`, force `Locale.US` in `onCreate()`
- [ ] Create `MainActivity.kt` — empty scaffold, `@AndroidEntryPoint`
- [ ] Create `di/DatabaseModule.kt` — stub (no DAOs yet)
- [ ] Create `di/NetworkModule.kt` — stub Ktor `HttpClient`

---

## Phase 2 — Localization Scaffolding

- [ ] Create `res/values/strings.xml` — all keys in English (stubs ok, fill in as screens are built)
- [ ] Create `res/values-sl/strings.xml` — Slovenian translations
- [ ] Create `res/values-hu/strings.xml` — Hungarian translations
- [ ] Convention: every composable uses `stringResource()`, zero hardcoded strings

---

## Phase 3 — Theme System

- [ ] Create `ui/theme/Theme.kt`:
  - `ThemeMode` enum (`DARK`, `LIGHT`)
  - `ThemePreset` enum (Purple, Blue, Teal, Pink, Orange × dark/light — 10 total)
  - `AppColors` data class with all semantic tokens
  - `buildDarkColors(hue)` and `buildLightColors(hue)` HSL generators
  - `LocalAppColors` composition local
  - `MoodFoxTheme(preset)` composable wrapping `MaterialTheme`
- [ ] Create `ui/theme/Type.kt` — typography scale
- [ ] Wire `MoodFoxTheme` into `MainActivity` (hardcode `PURPLE_DARK` for now, dynamic later)
- [ ] Verify theme tokens render correctly on a simple test composable

---

## Phase 4 — Database

- [ ] Create `data/local/db/Entities.kt`:
  - `MoodEntry` (`id`, `timestamp` Long, `moodValue` Int, `causeIds` String JSON, `note` String?, `weatherSnapshotId` Long?)
  - `CauseCategory` (`id`, `name`, `emoji`, `sortOrder`, `isDefault`, `isActive`)
  - `WeatherSnapshot` (`id`, `timestamp` Long, `city` String, `temperatureC` Float, `condition` String, `isRaining` Boolean, `humidity` Float)
- [ ] Create `data/local/db/Converters.kt` — `Instant ↔ Long`, `List<Long> ↔ String`
- [ ] Create `data/local/db/Daos.kt`:
  - `MoodEntryDao` — `getAll()` Flow, `getByDateRange(from, to)` Flow, `insert`, `update`, `delete`
  - `CauseCategoryDao` — `getAll()` Flow, `getActive()` Flow, `insert`, `update`, `delete`, `updateSortOrders()`
  - `WeatherSnapshotDao` — `insert`, `getById`
- [ ] Create `data/local/db/MoodDatabase.kt` — Room DB v1, register all entities, Converters, migrations
- [ ] Update `di/DatabaseModule.kt` — provide Room DB and all three DAOs
- [ ] Seed default cause categories on first launch (check if table is empty in `MoodFoxApp.onCreate()`)
- [ ] Write a quick smoke test: insert a `MoodEntry`, read it back via Flow

---

## Phase 5 — Preferences

- [ ] Create `data/local/PreferencesManager.kt` — DataStore with all keys:
  - `selectedHelper`, `helperEnabled`, `animationsEnabled`, `reduceMotion`
  - `weatherEnabled`, `manualCity`
  - `remindersEnabled`, `reminderTimes`, `quietHoursStart`, `quietHoursEnd`
  - `themePreset` (String, default `"PURPLE_DARK"`)
  - `analysisDefaultRange` (Int, default 30)
  - `onboardingComplete` (Boolean, default false)
- [ ] Inject `PreferencesManager` in `MainActivity`, pass down via `Navigation.kt`

---

## Phase 6 — Logging

- [ ] Create `data/local/AppLogger.kt` — daily rotating log files, 7-day retention (copy pattern from fatloss_track)
- [ ] Register in `DatabaseModule.kt`

---

## Phase 7 — Navigation Shell

- [ ] Create `ui/Navigation.kt`:
  - Read `onboardingComplete` from `PreferencesManager`
  - `startDestination = if (onboardingComplete) "checkin" else "welcome"`
  - `NavHost` with all routes: `welcome`, `checkin`, `calendar`, `analysis`, `settings`, `settings/categories`, `settings/log_viewer`
  - Bottom navigation bar (hidden on `welcome`, `settings/categories`, `settings/log_viewer`)
- [ ] Wire `MoodFoxTheme` with live `themePreset` from `PreferencesManager.themePreset.collectAsState()`

---

## Phase 8 — Welcome / Onboarding Screen

- [ ] Create `ui/onboarding/WelcomeScreen.kt` with `HorizontalPager` (4 pages):
  - **Page 1**: App name, static helper character placeholder, tagline
  - **Page 2**: Mood scale demo (interactive drag, no save)
  - **Page 3**: Stability goal explanation (-2 to +2, no big swings)
  - **Page 4**: Helper style picker + theme preset color swatch grid (applies immediately)
- [ ] "Let's start" button on page 4: sets `onboardingComplete = true`, navigates to `checkin`
- [ ] "Skip" link on pages 1–3: same effect with defaults left in place
- [ ] Page indicators (dots) at bottom
- [ ] Verify: reopening the app skips welcome screen after completion

---

## Phase 9 — Check-in Screen

- [ ] Create `ui/components/CauseChips.kt` — reusable multi-select chip row from `CauseCategoryDao`
- [ ] Create `ui/checkin/MoodScaleComponent.kt`:
  - Horizontal draggable track -10 to +10, snaps to integers
  - Emoji anchors at -10, -5, -2, 0, +2, +5, +10 above the track
  - Thumb colored by value (gradient tertiary → onSurfaceVariant → secondary)
  - Large hit target, one-thumb friendly
- [ ] Create `ui/checkin/CheckInScreen.kt`:
  - `MoodScaleComponent`
  - Selected value display
  - `CauseChips` for cause selection
  - Optional note `TextField`
  - Save button → inserts `MoodEntry` into Room
  - Helper placeholder reacts to selected mood value
- [ ] After save: clear form, show brief confirmation, trigger weather fetch async if enabled

---

## Phase 10 — Calendar Screen

- [ ] Create `ui/calendar/CalendarScreen.kt`:
  - Monthly grid view
  - Each day cell shows average mood color + entry count
  - Tap a day → opens `DayDetailSheet`
  - Previous/next month navigation
- [ ] Create `ui/calendar/DayDetailSheet.kt` — `ModalBottomSheet`:
  - List of `MoodEntry` for the tapped day, ordered by time
  - Each entry: time, mood value, causes, note
  - Edit / delete per entry
  - Average and range summary at top

---

## Phase 11 — Domain Logic

- [ ] Create `domain/MoodStats.kt` — pure functions, no coroutines:
  - `rollingAverage(entries, days)` → Float
  - `volatility(entries)` → Float (avg absolute swing between consecutive entries)
  - `swingCount(entries, threshold)` → Int
  - `isWithinTargetBand(avg)` → Boolean (-2f..+2f)
  - `averageByWeekday(entries)` → `Map<DayOfWeek, Float>`
  - `averageByHourBucket(entries)` → `Map<String, Float>` (morning/afternoon/evening/night)
  - `causeFrequency(entries)` → `Map<Long, Int>`
  - `causesForLowMoods(entries, threshold)` → `Map<Long, Int>`
  - `causesForHighMoods(entries, threshold)` → `Map<Long, Int>`
- [ ] Unit test `rollingAverage`, `volatility`, `isWithinTargetBand` with a few cases

---

## Phase 12 — Analysis Screen

- [ ] Create `ui/analysis/MoodLineChart.kt` — Canvas composable:
  - Entry dots colored by value
  - Rolling average line in `primary` color
  - Shaded horizontal band between y=-2 and y=+2 (target zone, `accent` at low alpha)
  - X axis: dates; Y axis: -10 to +10
- [ ] Create `ui/analysis/StabilityCard.kt`:
  - 7-day and 30-day rolling averages
  - In-band / out-of-band badge
  - Volatility value + calm / moderate / turbulent label
- [ ] Create `ui/analysis/AnalysisScreen.kt`:
  - Time range selector (7d / 30d / 90d / custom)
  - `MoodLineChart`
  - `StabilityCard`
  - Average by weekday bar chart
  - Top causes chart
  - Observation cards (generated from `MoodStats` functions, rendered as readable sentences)

---

## Phase 13 — Settings Screen

- [ ] Create `ui/settings/SettingsScreen.kt`:
  - Theme preset picker (grid of color swatches, applies immediately)
  - Helper selection
  - Animations toggle / reduce motion toggle
  - Reminder section (enable/disable, times, quiet hours)
  - Weather section (enable/disable, manual city fallback)
  - Backup / export / import
  - Privacy: delete all data
  - Debug: open log viewer
- [ ] Create `ui/settings/CategoryManagerScreen.kt`:
  - List all categories, toggle active/inactive
  - Drag-to-reorder
  - Add custom category (name + emoji picker)
  - Rename, delete custom categories (defaults cannot be deleted, only hidden)
- [ ] Create `ui/settings/LogViewerScreen.kt` — scrollable log file content, share button

---

## Phase 14 — Reminders

- [ ] Create `domain/ReminderScheduler.kt` — WorkManager wrapper:
  - Schedule `PeriodicWorkRequest` (or exact alarms) per configured reminder time
  - Cancel all on disable
  - Respect quiet hours window
- [ ] Create `MoodReminderWorker.kt` — posts notification with deep-link to `checkin`
- [ ] Wire up in Settings reminder toggle and time pickers
- [ ] Test: notification appears at configured time, tapping opens check-in screen

---

## Phase 15 — Weather

- [ ] Create `data/remote/WeatherService.kt` — Ktor client calling Open-Meteo (`api.open-meteo.com`):
  - `fetchCurrent(lat, lon)` → `WeatherSnapshot`
  - Wrap in try/catch, return null on failure
- [ ] Update `di/NetworkModule.kt` — provide `WeatherService`
- [ ] Add `ACCESS_COARSE_LOCATION` to manifest
- [ ] Permission request flow in `CheckInScreen` when weather is enabled but permission not yet granted
- [ ] Manual city fallback in Settings → `WeatherService.fetchByCity(city)`
- [ ] After mood save: `CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { fetchAndSave() }` — never blocks save

---

## Phase 16 — Helper Character

- [ ] Create `ui/components/HelperCharacter.kt`:
  - Static drawable or Lottie animation per helper type (fox, cat, bunny, panda)
  - Idle animation
  - Reacts to mood value passed in (sad / neutral / happy expression)
  - Reduce motion mode: static image only
- [ ] Wire into `CheckInScreen` and `WelcomeScreen` page 1
- [ ] Wire helper type selection in Settings and Onboarding page 4

---

## Phase 17 — Backup / Export

- [ ] Implement JSON export: serialize all `MoodEntry` + `CauseCategory` rows to a single JSON file
- [ ] Save to `Downloads` or share via system share sheet
- [ ] Implement JSON import: parse file, validate, insert (skip duplicates by `timestamp`)
- [ ] Wire into Settings backup section

---

## Phase 18 — Polish

- [ ] Empty states for Calendar (no entries this month), Analysis (not enough data), Check-in (first time)
- [ ] Onboarding: animate helper on page 1
- [ ] Check-in: helper micro-animation on save
- [ ] Consistent `stringResource()` audit — no hardcoded strings anywhere
- [ ] Translation review for SL and HU
- [ ] Test on physical device (Pixel 10 Pro XL, Android 16)
- [ ] Launcher icons (raster + adaptive, avoid 0×0 crash on Android 16)
- [ ] App name in `strings.xml` for all locales

---

## Ongoing

- Keep `res/values/strings.xml` up to date as each screen is built; add SL/HU keys immediately
- Run `./gradlew :mobile:assembleDebug` after each phase to catch build issues early
- Never use `fallbackToDestructiveMigration()` — always write an explicit migration
- One migration per DB version bump; bump version in `MoodDatabase.kt` every time an entity changes
