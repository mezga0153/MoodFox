# Mood Tracker App Plan

## Product Summary

An Android app for quick mood tracking several times per day. Users log their mood on a scale from **-10 to +10**, optionally record causes, add notes, and review patterns over time.

The app includes:
- a fast **check-in** screen with the mood scale
- a **calendar/history** view for past entries
- an **analysis** section with charts and observations
- a **settings** area for categories, backup, reminders, permissions, and helper customization
- an optional **weather context** feature when the user allows the needed permissions
- a small **animated helper** character to make the app feel less sterile

The app should be emotionally approachable, fast, and private by default.

---

## Main Goals

### Primary goals
- Let users log mood in a few seconds.
- Support multiple mood entries per day.
- Help users identify patterns over time.
- Let users attach likely causes to each mood entry.
- Make the experience simple enough that people actually keep using it.
- Encourage long-term mood stability: a sustained rolling average between **-2 and +2** is the target. Chasing high peaks or tolerating prolonged lows are both problems. Calm and steady wins.

### Secondary goals
- Add charm with a cute animated helper.
- Enrich entries with context such as weather.
- Support backup/export without forcing account creation.
- Allow users to customize categories and helper style.
- Support multiple UI color themes (dark and light variants).
- Support multiple languages — English, Slovenian, Hungarian at minimum.
- Offer a welcoming intro/onboarding flow so first use does not feel cold.

---

## Core Concept

### Mood scale
The main interaction is a horizontal scale from **-10 to +10**.

Important marked values:
- `-10`
- `-5`
- `-2`
- `0`
- `+2`
- `+5`
- `+10`

### Interpretation
- `-10` = extremely bad mood
- `0` = neutral / nothing special / emotionally flat
- `+10` = extremely good mood

The scale can use emoji bubbles placed directly on the line for visual guidance.

### Long-term target zone
The goal is **not** to be at +10 all the time. That is unsustainable and meaningless.

The healthy long-term target is a rolling average that stays between **-2 and +2** without large swings. This represents calm, stable contentment — not flat numbness and not forced euphoria.

- A long-term average above +2 often means logging is optimistic or selective.
- A long-term average below -2 is a signal worth paying attention to.
- Large swings (e.g. frequently jumping from -7 to +8 and back) are worth tracking even if the average looks fine.

The app should surface this gently, without making users feel judged.

Example mapping:
- `-10` = crying emoji
- `-5` = sad emoji
- `-2` = slightly sad / low emoji
- `0` = neutral / blank / no-emotion emoji
- `+2` = slight smile
- `+5` = happy emoji
- `+10` = very excited / star-eyes emoji

---

## Main User Flows

## 1. Quick mood check-in
1. User opens the app.
2. The default screen shows the mood scale.
3. User taps or drags to select a value.
4. User can optionally:
   - select one or more causes
   - add a short note
   - review auto-detected context such as weather
5. User saves the entry.
6. The helper gives a short reaction or supportive message.

### Goal
The full flow should work in under 10 seconds for a basic entry.

## 2. Review past moods
1. User opens the **Calendar** tab.
2. User sees a calendar with mood summaries for each day.
3. User taps a day.
4. User sees all mood entries for that day, including time, value, causes, note, and weather if available.

## 3. View analysis
1. User opens the **Analysis** tab.
2. User sees charts and pattern summaries.
3. User can filter by date range, cause, time of day, and maybe weather.
4. User sees observations based on stored entries.

## 4. Manage settings
1. User opens the **Settings** tab.
2. User manages categories, reminders, permissions, backup, helper type, theme, and app behavior.

## 5. First launch / onboarding
1. On very first launch the user sees the welcome screen instead of the check-in screen.
2. A short pager (3–4 pages) introduces the app, demonstrates the mood scale, explains the stability goal, and lets the user pick a helper style and color theme.
3. User taps "Let’s start" or skips at any point.
4. After completing or skipping, the check-in screen becomes the permanent home.

---

## Navigation Structure

Use **bottom navigation** with 4 tabs:

1. **Check-in**
   - Main mood entry screen
   - Default landing screen

2. **Calendar**
   - Calendar/history view
   - Daily summaries and day details

3. **Analysis**
   - Charts, trends, and observations

4. **Settings**
   - Preferences, permissions, categories, backup, helper selection

This is enough for version 1. Adding more tabs early is how you end up building a bloated mess.

---

## Feature Plan

## 1. Check-in / Mood Logging

### Required
- Select mood on a scale from `-10` to `+10`
- Multiple entries per day
- Save exact timestamp for each entry
- Optional note field
- Optional cause selection
- Save entry locally

### Nice to have
- Emoji bubbles directly on the scale
- Animated helper reaction to selected mood
- Quick category suggestions based on past entries
- Very fast repeat logging flow

### UX requirements
- The scale must be easy to use with one thumb.
- Optional details must never block saving.
- The screen should not feel like filling out a tax form.

---

## 2. Mood Cause Categories

Users can select one or more causes that may explain their mood.

### Default categories
- Work
- Family
- Relationship
- Friends / Social
- Sleep
- Health
- Exercise
- Food
- Weather
- Money
- Routine
- Stress / Anxiety
- Achievement
- Rest
- Nothing specific
- Other

### Settings support
- Add a custom category
- Rename category
- Remove category
- Reorder category
- Mark favorites for faster access

### Recommendation
Do not force users to always choose a cause. Sometimes there is no clear cause and pretending otherwise is nonsense.

---

## 3. Calendar / History

### Required features
- Monthly calendar view
- Visual daily summary on each date
- Tap a day to see entry details
- Show number of entries per day
- Show average or range of moods for the day

### Possible visual ideas
- Day color reflects average mood
- Tiny emoji summary for the day
- Daily detail screen with timeline of entries

### Day detail content
- Entry time
- Mood value
- Selected causes
- Note
- Weather context

---

## 4. Analysis

### Basic analysis
- Mood over time line chart
- Rolling long-term average with target zone band (**-2 to +2**) highlighted
- Mood volatility indicator — how large the typical swing between consecutive entries is
- Average mood by weekday
- Average mood by time of day
- Mood distribution chart
- Most common causes overall
- Most common causes linked to low moods
- Most common causes linked to high moods

### Advanced analysis
- Compare mood with weather conditions
- Compare mood by morning / afternoon / evening
- Weekly and monthly trend summaries
- Cause frequency over time
- Swing frequency: how often the user crosses from positive to negative territory or vice versa
- "Stability score" based on rolling average staying within the -2 to +2 band

### Important limitation
The app should provide **observations**, not fake psychology wisdom. If the analysis is weak, say it clearly. Do not dress up simple averages as deep insight.

Example wording:
- “Your average mood is lower on Mondays.”
- “Sleep is often tagged on low-mood entries.”
- “Rainy days appear slightly associated with lower mood.”- "Your 30-day average is +1.4 — within the calm zone."
- "Your 30-day average is -3.1 — a bit below the stable range."
- "You have several large swings this week. Average looks fine but the ride is rough."
Bad wording:
- “You may have a hidden emotional disorder.”
- “Our AI understands your mental state.”

---

## 5. Animated Helper

A cute helper character adds warmth and encourages repeated use.

### Possible helper types
- Fox
- Cat
- Bunny
- Panda
- Simple blob creature
- Emoji-style face helper

### Behavior ideas
- Idle animation on the check-in screen
- Reacts to selected mood
- Gives short supportive or neutral comments
- Celebrates streaks carefully
- Explains features during onboarding

### Settings options
- Select helper type
- Enable or disable helper
- Enable or disable animation
- Reduce motion mode
- Mute helper messages

### Design rule
Keep the helper subtle. Cute is useful. Annoying is fatal.

---

## 6. Weather Tracking

### Goal
Capture weather context at the time of each mood entry, but only when the user allows it.

### Permission strategy
- Weather tracking should be **optional**.
- If location permission is granted, the app can fetch local weather.
- If permission is denied, the app still works normally.
- Optional fallback: let the user manually set a city.

### Weather data to store
- Temperature
- Weather condition summary
- Rain / no rain
- Cloud cover if available
- Humidity if useful
- Timestamp of weather data
- Approximate location or city name

### Recommendation
Only collect what is actually needed. If the app starts behaving like spyware, people will uninstall it.

---

## Screen-by-Screen Plan

## 1. Check-in Screen

### Main components
- Greeting / short title
- Animated helper
- Mood scale from `-10` to `+10`
- Emoji bubbles directly on the line at major anchor points
- Selected mood value display
- Cause selector chips
- Optional note input
- Save button

### Optional extras
- Quick-add from home screen widget later
- Suggested causes based on recent patterns

### UX goal
This is the most important screen. If this screen sucks, the app sucks.

---

## 2. Calendar Screen

### Main components
- Monthly calendar
- Day cells with mood indicators
- Day detail modal or page
- List of entries for selected day

### Useful metrics per day
- Average mood
- Highest/lowest mood
- Number of entries
- Common causes

---

## 3. Analysis Screen

### Main components
- Time range selector
- Mood trend chart with **-2 / +2 target band** rendered as a shaded region
- Rolling average line overlaid on the chart
- Volatility / swing indicator card
- Cause chart / breakdown
- Weather comparison section
- Summary cards with observations

### Filters
- Last 7 days
- Last 30 days
- Last 90 days
- Custom date range
- By cause
- By time of day
- With/without weather context

---

## 4. Settings Screen

### Main components
- Manage cause categories
- Reminder settings
- Weather permission toggle/info
- Backup/export/import
- Helper selection
- **Color theme picker** (multiple presets, dark and light variants)
- **Language** follows system locale automatically; no in-app picker needed for MVP
- Theme and animation preferences
- Privacy settings

### Extra settings
- Data export format
- Delete all data
- Local PIN/app lock later
- Notification frequency

---

## Suggested Data Model

## MoodEntry
- `id`
- `timestamp`
- `moodValue` (integer from `-10` to `+10`)
- `causeIds` (list)
- `note` (nullable)
- `weatherSnapshotId` (nullable)
- `createdAt`
- `updatedAt`

## CauseCategory
- `id`
- `name`
- `iconOrEmoji`
- `sortOrder`
- `isDefault`
- `isActive`

## WeatherSnapshot
- `id`
- `timestamp`
- `cityOrApproxLocation`
- `temperature`
- `condition`
- `isRaining`
- `humidity`
- `provider`

## AppSettings
- `selectedHelper`
- `helperEnabled`
- `animationsEnabled`
- `reduceMotionEnabled`
- `weatherTrackingEnabled`
- `remindersEnabled`
- `backupEnabled`
- `theme`

---

## Technical Recommendation

## Platform choice
Since this is Android-first, the best default choice is:

**Kotlin + Jetpack Compose**

### Why
- Best native Android experience
- Better control over permissions
- Better support for reminders/background tasks
- Good Compose support for modern UI and animations
- Easier long-term maintenance for an Android-only app

### Alternative
**Flutter** is reasonable if you want iOS later and accept extra complexity now.

But for Android-only version 1, native Kotlin is the cleaner option.

---

## Storage and Architecture

### Local storage
Start with local-first architecture:
- **Room** for mood entries, categories, weather snapshots
- **DataStore** for settings/preferences

### Architecture
Use a standard modern Android structure:
- UI: Jetpack Compose
- State: ViewModel
- Data: Repository layer
- Local DB: Room
- Background work: WorkManager
- Permissions: Android runtime permissions

### Weather integration
Use a weather API only when enabled. Save a weather snapshot at entry time instead of constantly recalculating history later.

---

## Backup Strategy

### MVP
- Manual export/import
- Local backup file

### Later phase
- Optional cloud backup
- Possibly Google Drive backup or encrypted app-managed sync

### Recommendation
Do not start with accounts unless you enjoy wasting time on problems that have nothing to do with the core product.

---

## Notifications / Reminders

### Use case
Help users remember to log mood several times per day.

### Features
- Enable/disable reminders
- Set reminder frequency
- Set reminder times
- Quiet hours
- Snooze option

### Important rule
Reminders should help, not harass. Too many notifications and people will kill the app.

---

## Privacy Principles

Mood data is sensitive. Treat it like that.

### Requirements
- Local-first by default
- Optional permissions only
- Clear explanation of what is collected
- Weather tracking opt-in
- Export and delete all data
- No fake therapy claims
- No dark-pattern bullshit

### Later options
- PIN lock / biometric lock
- Encrypted export
- Privacy mode to hide notes on overview screens

---

## MVP Scope

Build this first.

### In MVP
- Check-in screen with mood scale
- Multiple entries per day
- Cause selection
- Optional notes
- Calendar/history
- Basic analysis charts
- Settings for categories and helper selection
- **Welcome / onboarding screen on first launch**
- **Multiple color themes (dark + light) with real-time preview**
- **Localization: English, Slovenian, Hungarian**
- Reminders
- Optional weather tracking toggle
- Local storage
- Basic export/import

### Not in MVP
- Social features
- AI therapist nonsense
- Cloud account system
- Advanced predictions
- Chatbot companion
- Wearables integration
- Deep clinical recommendations

That last group is where projects usually get stupid and die.

---

## Suggested Milestones

## Milestone 1: Product definition
- Finalize feature scope
- Define mood scale behavior
- Define helper concept
- Define default categories
- Define supported languages and theme presets
- Create wireframes for 4 tabs + welcome screen

## Milestone 2: Core logging flow
- Set up project structure
- Set up localization scaffolding (EN / SL / HU string files)
- Implement theme system with HSL-based presets
- Build welcome / onboarding screen
- Implement local database
- Build check-in screen
- Save mood entries with causes and notes

## Milestone 3: History
- Build calendar screen
- Build day detail view
- Add daily summary visuals

## Milestone 4: Analysis
- Add charts
- Add basic observation cards
- Add filters by time and cause

## Milestone 5: Settings and backup
- Category management
- Helper selection
- Reminder scheduling
- Weather toggle and permission flow
- Export/import

## Milestone 6: Polish
- Helper animations
- Onboarding
- Empty states
- Performance improvements
- Better visuals and iconography

---

## Risks and Product Traps

## 1. Too much friction in logging
If it takes too many taps, users stop using it.

## 2. Annoying helper
If the helper talks too much or animates too much, it becomes unbearable.

## 3. Weak analysis
If the analysis tells users obvious garbage, it adds no value.

## 4. Privacy concerns
If weather tracking or permissions feel invasive, trust is gone.

## 5. Scope creep
If you try to build a therapy platform, habit tracker, AI coach, and social network at once, the project turns into garbage.

---

## Open Questions

- Should users be allowed to edit past entries?
- Should notes support only plain text or richer tags?
- Should there be daily summaries written automatically?
- Should the helper have its own personality styles?
- Should there be onboarding that asks users to pick favorite causes?
- Should there be a home screen widget for ultra-fast check-ins?
- Should weather use exact location or approximate city only?

---

## Recommended First Build Order

1. Check-in screen
2. Local database and entry saving
3. Cause selection
4. Calendar history
5. Basic analysis charts
6. Settings and reminders
7. Weather integration
8. Helper polish and animation

That order keeps the product focused on the actual core value instead of decorative nonsense.

---

## Short Summary

Build a **local-first Android mood tracking app** with:
- a fast mood scale check-in flow
- optional causes and notes
- calendar history
- lightweight analysis
- optional weather context
- customizable animated helper
- strong privacy defaults

The underlying philosophy: the goal is not to feel great all the time. The goal is a long-term average that stays between **-2 and +2** without wild swings — calm, stable, sustainable.

The most important thing is not the fox, not the charts, not the weather. It is whether logging mood is fast enough that people will keep doing it.
