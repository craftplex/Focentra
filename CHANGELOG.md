# Changelog

All notable changes to **Focentra** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [8.0.8] - 2026-11-02

- **Earlier Chapter Study Time**: Store added previous study time as chapter-level historical data instead of a session dated today, so it increases chapter totals and subject study-hour progress without inflating daily focus statistics.
- **Existing Entry Correction**: Migrate prior "Previously studied" manual sessions into chapter-level historical totals and remove their misleading daily session records.
- **Backup Compatibility**: Include chapter historical study totals in JSON backup export and restore.
- **Database Migration**: Upgrade Room database to version 9 while preserving existing study data.
- **Version Bump**: Updated `versionCode` to `72` and `versionName` to `8.0.8`.

## [8.0.7] - 2026-11-02

- **Focus Now History**: Save Focus Now stopwatch sessions directly to History when they finish, and add optional topic and session-goal fields.
- **Manual Chapter Data**: Allow manual study entries to select a chapter for accurate chapter reporting.
- **Earlier Chapter Time**: Add earlier study minutes to a chapter from Chapter Report; the entry also contributes to the subject's accumulated study time.
- **Weekly Statistics**: Use Sunday-through-Saturday weeks and matching graph buckets so Sunday study appears in This Week on Monday.
- **Subjects Screen**: Reduce subject-title typography and keep names on one line.
- **Version Bump**: Updated `versionCode` to `71` and `versionName` to `8.0.7`.

## [8.0.6] - 2026-11-02

- **Release Signing**: Fail release packaging when the configured keystore or credentials are missing; remove the publicly known signing fallback from Gradle and GitHub Actions.
- **Daynexa Integration**: Restrict the exported sessions provider and permission-gated broadcasts to apps signed with Focentra's signing certificate.
- **Timer Accounting**: Use monotonic elapsed time for running and paused segments so wall-clock changes do not skew a session; add deterministic background/screen-off and pause/resume coverage.
- **SDK Documentation**: Align the documented Android SDK level with the project's API 35 build configuration.
- **Regression Coverage**: Add daily/monthly statistics checks and JSON backup import checks for duplicate subjects and larger session archives.
- **Version Bump**: Updated `versionCode` to `70` and `versionName` to `8.0.6`.

## [8.0.5] - 2026-11-02

- **Study Timer UI**: Reduce the full-screen accent tint to keep timer colors balanced and improve readability.
- **Quick Study Modes**: Disable and visually dim the dashboard shortcuts while a timer is running or paused.
- **Exam Milestones**: Keep milestone cards collapsed by default.
- **Revision Notifications**: Open revision reminders directly in Revision Hub and give each notification its own navigation intent.
- **Study Trend Graphs**: Calculate Monday-based week boundaries independent of locale, retain the seven weekly buckets when there is no activity, and render the zero-activity trend instead of hiding the graph.
- **Version Bump**: Updated `versionCode` to `69` and `versionName` to `8.0.5`.

## [8.0.4] - 2026-11-01

- **Study Trend Graph**: Corrected the Yesterday date range and hardened previous-week boundary calculation so matching study sessions populate the trend.
- **Subject Distribution**: Show every subject and constrain long subject names so their study-time values remain readable.
- **Manual Study Data**: Add a date picker for historical manual study entries.
- **Session History**: Preserve timer-session topics and goals with the session record.
- **Version Bump**: Updated `versionCode` to `68` and `versionName` to `8.0.4`.

## [8.0.3] - 2026-11-01

- **Background Notifications**: Schedule motivational and daily-target alerts at the requested times, restore alarms after reboot/time changes, and use exact alarms when Android grants access.
- **Mandatory Revision Flow**: Add due-stage notifications and history, enforce one-hour revisions, support up to five reasoned dismissals with a four-day reschedule window, and require ten hours of chapter study after the fifth dismissal.
- **Timer and Revision Hub**: Gate study timers while revisions are due, keep scheduled cards until a full-hour revision is saved, and correct the due-date countdown.
- **Analytics**: Persist selected chapters with study sessions, report chapter-level study time accurately, and show an explicit no-activity state in empty charts.
- **Version Bump**: Updated `versionCode` to `67` and `versionName` to `8.0.3`.

## [8.0.0] - 2026-10-22

- **Major Production Release (v8.0.0)**: Production-ready stable release optimized for peak performance, zero lag, zero crashes, and 100% bug-free operation across all screens and background services.
- **Performance & Recomposition Optimization**: Comprehensive audit and tuning of Compose recomposition flows, background thread dispatching, and Room database query performance.
- **Version Bump**: Updated `versionCode` to `64` and `versionName` to `8.0.0`.

## [7.1.7] - 2026-10-22

- **Screen-Matching Motion**: Added smooth AnimatedContent transitions matching main screen tab animations when switching modes inside Timer Screen and Revision Hub.
- **Version Bump**: Updated `versionCode` to `53` and `versionName` to `7.1.7`.

## [7.1.6] - 2026-10-22

- **Timer Screen Layout Fix**: Restored setup controls, preset templates, ambient sound options, and the main Start button inside the main scrollable Column layout to fix component overlapping.
- **Smooth Time Readout Motion**: Adjusted time digit readout transition animation speed to a moderate, smooth pace (400-450ms duration).
- **Version Bump**: Updated `versionCode` to `52` and `versionName` to `7.1.6`.

## [7.1.5] - 2026-10-22

- **Timer Setup Layout**: Fixed a misplaced layout scope that caused timer setup controls and the Start button to overlap.
- **Timer Counter Motion**: Slowed the digit transition slightly while keeping the timer's real-time clock unchanged.
- **Version Bump**: Updated `versionCode` to `51` and `versionName` to `7.1.5`.

## [7.1.4] - 2026-10-22

- **Smooth App Navigation**: Added directional fade-and-slide transitions between screens and a scale/fade transition for full-screen focus mode.
- **Timer Motion**: Animated timer setup/session changes, mode-specific layout resizing, second-by-second readout, ring progress, status colors, and Pomodoro round indicators.
- **Navigation Feedback**: Added a soft spring scale response to the selected bottom navigation icon.
- **Release Signing**: Continues using the permanent release key introduced in v7.1.3. This signer differs from v7.1.2; existing installs may require reinstall. Back up local app data before uninstalling.
- **Version Bump**: Updated `versionCode` to `50` and `versionName` to `7.1.4`.

## [7.1.3] - 2026-10-21

- **App-Wide Bug Fixes**: Corrected timer accounting, study statistics, reminder scheduling, backup/restore behavior, and audio/foreground-service lifecycle issues.
- **Release Signing**: Signed with the new permanent release key. This signing certificate differs from v7.1.2; existing installs cannot update in place and may need a reinstall. Export or back up local data before uninstalling.
- **Version Bump**: Updated `versionCode` to `49` and `versionName` to `7.1.3`.

## [7.1.2] - 2026-10-21

- **Reliable Study Reminders**: Added scheduled Android notifications that survive app restarts and reboot, follow selected weekdays, and are canceled when reminders are disabled or deleted.
- **Notification State Fixes**: Queue system notifications until the app can deliver them, persist only successfully posted alerts, and correctly mark notifications seen after loading.
- **Subjects & Topics Management**: Exposed topic add/list/delete controls, synchronized linked topics and chapters when subjects are renamed, and delete linked data with its subject.
- **Chapter Revision Tracking**: Keep chapter progress and study/completion timestamps in sync, with an explicit delete action and correct revision-alert resets.
- **Version Bump**: Updated `versionCode` to `48` and `versionName` to `7.1.2`.

## [7.1.1] - 2026-10-21

- **Revision Hub Timer Controls**: Improved responsive spacing between stopwatch actions and shortened cancel dialog buttons to "Continue" and "Cancel".
- **Version Bump**: Updated `versionCode` to `47` and `versionName` to `7.1.1`.

## [5.3.0] - 2026-10-19

- **Restored Core Midnight Dark Theme**: Reverted application theme to Focentra's core AMOLED Midnight Dark aesthetic (`#090A10` canvas, `#131522` surfaces, `#818CF8` primary indigo accent).
- **Version Bump**: Updated `versionCode` to `30` and `versionName` to `5.3.0`.

## [5.2.0] - 2026-10-19

- **Deynexa White/Light Aesthetic Overhaul**: Converted Focentra's core theme to Deynexa's clean white surface cards (`#FFFFFF`), soft slate canvas (`#F1F5F9`), and modern Cobalt Blue accents (`#3B82F6`) with dark system bar indicators.
- **Enhanced Smooth Animations**: Applied `animateFloatAsState` across all progress components, line/bar charts, and dial meters.
- **Version Bump**: Updated `versionCode` to `29` and `versionName` to `5.2.0`.

## [5.1.0] - 2026-10-19

- **Deynexa Cohesive Color Palette**: Applied Deynexa's modern cobalt/indigo primary accent, amoled slate surface background, dark elevated card fills, and emerald state colors to Focentra.
- **Super Smooth UI Animations**: Integrated `animateFloatAsState` with `FastOutSlowInEasing` across progress bars, bar chart columns, trend curves, and focus score dials.
- **Version Bump**: Updated `versionCode` to `28` and `versionName` to `5.1.0`.

## [5.0.0] - 2026-10-19

- **Notification & Smart Alert Hub**: Added Top Bar Notification Icon with live badge indicators for 3-Day Spaced Repetition Revision alerts, 5-Day Chapter Inactivity warnings, Goal Target Pace alerts, and Bengali motivational quotes.
- **Syllabus UI Polishing**: Fixed chapter dropdown collapse mechanics, added long-click chapter deletion popup, streamlined status buttons, and replaced text labels with clean action icons.
- **Optimized Animations**: Snappy collapse transitions for Exam Goals cards on DashboardScreen.
- **Major Version Bump**: Updated `versionCode` to `27` and `versionName` to `5.0.0`.

## [4.7.0] - 2026-10-18

- **Simplified 3-Tap Syllabus Tracker**: Replaced old class number and sequential class unlocking with intuitive 3-tap completion and status dropdown selector.
- **Built-in Bangla 1st Paper Syllabus**: Pre-populated full 26-chapter HSC Bangla 1st Paper syllabus (গদ্য, পদ্য, উপন্যাস ও নাটক).
- **Version Bump**: Incremented `versionCode` to `26` and `versionName` to `4.7.0`.

## [4.6.0] - 2026-10-18

- **Collapsible Exam Goals & Milestones**: Multiple exam milestones on DashboardScreen now collapse into sleek compact rows displaying Goal Title & "XX Days Left", expanding on click to preserve screen space.
- **Study Reminders Redesign**: Restored "Study Reminders" section header on SettingsScreen and updated Add Study Reminder dialog UI matching design specifications.
- **Version Bump**: Incremented `versionCode` to `25` and `versionName` to `4.6.0`.

## [4.2.0] - 2026-10-17

- **Card UI Redesign**: Completely redesigned "Exam Goals & Milestones" card and "Syllabus Compilation & Exam Readiness" card with modern Midnight Dark styling, progress gauges, and daily pacing stats.
- **Upgraded Syllabus System**: Added overall Syllabus Readiness Overview header card in SubjectsScreen with exam readiness scoring and chapter class metrics.
- **Version Bump**: Incremented `versionCode` to `21` and `versionName` to `4.2.0`.

## [4.1.0] - 2026-10-17

## [4.0.0] - 2026-10-17

## [3.9.0] - 2026-10-17

### Added
- **Syllabus & Chapter Sequential Tracking System**: Add chapters to subjects with custom total class count input (e.g. 20 classes).
- **Sequential Class Unlocking**: Class N+1 is disabled until Class N is completed.
- **Revision Classes Requirement**: Chapter completion requires finishing all regular classes plus Revision Class 01 & Revision Class 02.
- **Exam Milestone Syllabus Integration**: Links chapters and sequential class progress directly to upcoming exam goals.

### Updated
- **Version Bump**: Incremented `versionCode` to `18` and `versionName` to `3.9.0`.

## [3.8.0] - 2026-10-17

### Added
- **AI Adaptive Exam Pacing & Re-balancing Engine**: Dynamically calculates re-balanced focus requirements and study pacing.
- **Cognitive Fatigue & Burnout Predictor**: Real-time brain energy meter (0-100%), recommended break duration, and burnout risk index.
- **Smart Focus Peak Window Advisor**: Analyses hourly productivity to recommend peak concentration study windows.
- **Subject Retention & Spaced Memory Decay Scale**: Predicts memory retention percentages based on time elapsed since last study session.

### Updated
- **Version Bump**: Incremented `versionCode` to `17` and `versionName` to `3.8.0`.

## [3.7.0] - 2026-10-17

### Added
- **Super Smooth UI & Database Optimization**: Added Room indices on `StudySessionEntity` (`startTime`, `subject`, `completionStatus`) for microsecond query response.
- **Background Dispatcher Offloading**: Shifted all ViewModel Flow calculations and filtering to `Dispatchers.Default`.
- **Exam Goals Card Details Area**: Utilized whitespace under "X Days Left" to display target dates and detail hints cleanly.
- **Full App State Backup & Restoration**: Verified 100% complete import/export of subjects, colors, descriptions, distractions, topics, goals, presets, reminders, and exam targets.

### Updated
- **Version Bump**: Incremented `versionCode` to `16` and `versionName` to `3.7.0`.

## [3.6.0] - 2026-10-17

### Added
- **Dynamic Progress Level Trend Curve Chart**: Live trend graph tracking focus minutes and scores over time.
- **Enhanced Exam Goals & Milestones Card UI**: Optimized card spacing to display target dates and detail hints.
- **Complete Application State Backup**: Full streaming JSON/CSV import and export of subjects, presets, goals, reminders, and exam targets.

### Updated
- **Version Bump**: Incremented `versionCode` to `15` and `versionName` to `3.6.0`.

### Planned
- Home screen widget for fast study timer launch
- Wear OS companion module support
- Additional custom soundscapes for background white noise

---

## [3.5.0] - 2026-08-28

### Added
- **Full App 12-Hour Time Format**: Standardized all date and time displays, reminder alarms, and history logs to 12-hour AM/PM format. Added AM/PM toggle selector to study reminder dialogs.
- **Redesigned Exam Goals & Milestones Detail Popup**:
  - Re-organized layout cleanly separating title, subject/topic, syllabus/description, progress bar, daily pace, and target date.
  - Added full **Edit Option** via `EditExamTargetDialog` allowing editing of Title, Subject, Target Hours, Days Left, and Syllabus/Notes directly from popup and milestone cards.
- **Sub-Screen Navigation Bar Fix**: Hidden floating bottom navigation bar on Study Presets screen (`NavigationTab.PRESETS`) to clean up sub-screen layout.
- **Enhanced APK Signing**: Explicitly enabled v1 and v2 signature schemes in release/debug build configurations to eliminate package installer legitimacy warnings.

### Changed
- **Version Bump**: Incremented `versionCode` to `14` and `versionName` to `3.5.0`.

---

## [3.4.0] - 2026-08-28

### Added
- **Manual Study Data Input System**: Added manual session entry dialog on TimerScreen to log subject, topic, duration, productivity rating, category, and notes directly.
- **Strict 4-Option Import-Export System**:
  - JSON Export (File)
  - JSON Import (File)
  - CSV Text Export (Share/Copy)
  - CSV Text Import (Paste/Import)
- **Large File Streaming & Performance Optimization**: Refactored JSON Export/Import and CSV Import to use `JsonWriter`, `JsonReader`, and chunked 500-record Room DB batch operations on `Dispatchers.IO` to process large files smoothly without OOM or UI freezing.
- **Study Preset Edit Capability**: Enabled editing existing presets with prefilled settings in `PresetsScreen`.
- **Character Limits & Details Popup**:
  - Added strict character limits and character counter indicators for Exam Goals & Milestones (Title max 50, Subject max 30, Notes max 200) and Study Presets (Title max 50, Topic max 40).
  - Added clean popup detail dialogs (`PresetDetailDialog` & `ExamMilestoneDetailDialog`) to display full untruncated notes and info cleanly.

### Removed
- **Flashcards System Completely**: Removed `FlashcardsScreen.kt`, navigation tab, dashboard cards, and ViewModel logic as requested.
- **All Themes Except Midnight Dark Theme**: Enforced Midnight Dark Theme as the sole default theme and removed theme preview selection cards from Settings.
- **Click Ripple Shadow Effects**: Disabled global click ripple feedback across the UI via Material 3 `LocalRippleConfiguration`.

### Changed
- **Version Bump**: Updated `versionCode` to `13` and `versionName` to `3.4.0`.

---

## [1.4.0] - 2026-08-28

### Added
- **Daynexa Integration API (Final v1 Contract)**:
  - Implemented secure, read-only Android `ContentProvider` (`DaynexaSessionsProvider`) with authority `com.focentra.provider`.
  - Added **Push Broadcast System**: Real-time `com.focentra.ACTION_SESSION_COMPLETED` events sent upon session save.
  - Finalized Schema v1 Contract: `sessionId`, `subject`, `topic`, `startTime`, `endTime`, `duration` (min), `completionStatus` (String), `focusScore`, and `schemaVersion`.
  - Enforced strict read-only guarantees (mutations reject with `UnsupportedOperationException`).
  - Defined secure custom permission `com.focentra.permission.READ_SESSIONS` to protect both Provider access and Broadcast delivery.
- **Integrations Hub & User Consent Controls**:
  - Added dedicated `INTEGRATIONS` section in `SettingsScreen` with live status indicator and Daynexa companion card.
  - Transparent authorization consent dialog detailing provider authority, endpoints, schema format, and privacy invariants.
  - One-tap Disconnect toggle to instantly revoke data access and stop all outgoing broadcasts.
- **Unit & Robolectric Test Suite**:
  - Comprehensive test coverage in `DaynexaSessionsProviderTest` and `DaynexaBroadcastTest` verifying pull/push data integrity, secure permission enforcement, and consent gate logic.

### Changed
- **Version Bump**: Incremented `versionCode` to `6` and `versionName` to `1.4.0`.
- **Documentation**: Updated `README.md`, `ROADMAP.md`, and `ARCHITECTURE.md` with finalized API v1 specifications.

---

## [1.3.2] - 2026-08-25

### Changed
- **Version Bump**: Updated `versionCode` to `5` and `versionName` to `1.3.2` to ensure consistent and correct chronological versioning history.
- **Documentation**: Updated `README.md` setup commands to correctly recommend the Gradle wrapper (`./gradlew`) rather than the global Gradle installation.

---

## [1.3.1] - 2026-08-25

### Changed
- **Architectural Refactoring**:
  - Fully migrated all source package declarations and directory structures from `com.example` to `com.niloy.focentra` to align with the production application ID.
- **UI Redesign & Polish**:
  - Removed the `Quick Start (25m)` Floating Action Button from the Home Screen (`DashboardScreen`) as it cluttered the UI and felt out of place.
  - Adjusted padding and layout constraints in `FlashcardsScreen` to properly align the "New Flashcard" button with the screen's bottom navigation bar, preventing awkward floating.
  - Improved typography and semantics by changing the Top App Bar title from "Recall Flashcards" to a cleaner "Flashcards".
- **Version Bump**: Updated `versionCode` to `4` and `versionName` to `1.3.1`.

---

## [1.3.0] - 2026-08-25

### Added
- **Persistent 'Quick Start' Floating Action Button (FAB)**:
  - Added a persistent `ExtendedFloatingActionButton` on the Home (`DashboardScreen`) allowing users to launch a predefined 25-minute study timer with one tap.
  - Dynamically updates icon and state based on whether a study session is active, paused, or idle.

### Removed
- **10 Full-Screen UI Theme Presets**:
  - Removed full-screen theme selection presets to simplify application architecture and eliminate unnecessary clutter.
  - Restored clean, high-contrast OLED dark canvas for Full-Screen Focus Mode.

### Changed
- **Version Bump**: Updated `versionCode` to `3` and `versionName` to `1.3.0`.
- **Documentation & Presentation**: Updated GitHub presentation (`README.md`, `ROADMAP.md`, `CHANGELOG.md`) reflecting the Quick Start FAB component and version 1.3.0.

---

## [1.2.1] - 2026-08-25

### Added
- **Active Recall Flashcards Studio**:
  - Dedicated `FlashcardsScreen` for creating, reviewing, and organizing subject-based study cards.
  - Interactive 3D perspective card flip animation (`graphicsLayer { rotationY }`) revealing solution and optional clue hints.
  - Mastery progress indicator bar calculating percentage of mastered cards per subject deck.
  - Quick access integrated into Top Bar actions, More menu, and Dashboard cards.
- **Focus Brain Dump & Distraction Shield**:
  - Instant thought-capture dialog (`BrainDumpDialog`) enabling learners to record off-topic ideas or tasks during study sessions without interrupting timer flow.
  - Accessible directly from `TimerScreen`, `FullScreenFocusScreen` (AMOLED mode), and `DashboardScreen`.
  - Checkable thought items with time stamps and one-tap clear for completed items.

### Changed
- **Shareable Study Report & PNG Card**:
  - Optimized the "Share PNG" and "Share Text" buttons in `AnalyticsScreen` with uniform heights (`46.dp`), explicit text padding, and single-line constraints to ensure perfectly symmetrical alignment on all screen sizes.
  - Updated high-contrast bitmap card generation for crisp social media sharing.
- **System & Version Updates**:
  - Version bumped to `1.2.1`.
  - Synchronized Settings screen database info string to reflect v1.2.1.

---

## [1.2.0] - 2026-08-25

### Added
- **Full-Screen Focus UI Preset Themes (10 Presets)**:
  - Custom full-screen themes for Timer, Pomodoro, and Stopwatch modes (*OLED Midnight*, *Cyberpunk Neon*, *Nordic Aurora*, *Sunset Amber*, *Tokyo Night*, *Emerald Oasis*, *Matrix Green*, *Solar Crimson*, *Deep Cosmos*, *Zen Slate*).
  - Theme selection integrated directly in `SettingsScreen` under Appearance & Themes with interactive preview swatches and a live "Preview" button.
  - Persistent theme configuration cached locally via `DataStore` and application preferences.
- **System Stability & Android 14+ Compatibility**:
  - Updated `StudyTimerService` foreground notification dispatch with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for Android 14 (API level 34+) compatibility.
  - Enhanced grid alignment in `CalendarScreen` month view to ensure 7-column layout balance.

### Changed
- **Version Set**: Set `versionCode` to `2` and `versionName` to `1.2.0`.
- **Documentation & Presentation**: Synchronized GitHub presentation (`README.md`, `ROADMAP.md`, `CHANGELOG.md`) detailing all core features, architecture, and full-screen theme presets.

---

## [1.1.0] - 2026-08-25

### Changed
- **Version**: Pre-release version `1.1.0`.
- **Developer Info & Contact**:
  - Replaced temporary learning status descriptions with a clean, professional profile layout.
  - Added direct **Gmail** contact integration with one-tap email composer and clipboard fallback (`niloymitra889@gmail.com`).
  - Added direct **GitHub repository & profile** link integration (`https://github.com/Niloy-Track-Dev/Focentra`).
  - Polished the About dialog design with Material 3 elevated cards and typography.

### Fixed
- Verified and fine-tuned all UI layouts, text contrast ratios, and touch targets across all screens.
- Synchronized CI/CD GitHub Actions release pipeline and artifact naming with v1.1.0.

---

## [1.0.0] - 2026-08-25

### Added
- **Focus Timer Engine**:
  - Countdown, Stopwatch, and classic Pomodoro multi-interval study modes.
  - Background execution with Android Foreground Service (`StudyTimerService`) and interactive notification controls.
  - Distraction logging counter with optional reason/distraction tag tracking.
  - Ambient white noise audio generator (Rain, White Noise, Cafe, Stream, Ambient, Forest).
  - Immersive Full-Screen Focus Mode with flip-down AMOLED dark theme and anti-burn-in dimming.
- **Subject & Topic Management**:
  - Color-coded subjects with custom icon selection.
  - Sub-topic hierarchical organization with specific goal durations.
  - Subject study velocity, completion percentage, and target progress tracking.
- **Analytics & Insights**:
  - Interactive multi-period analytics (Today, Yesterday, This Week, Last Week, This Month, Last Month, This Year, All Time).
  - 16-week GitHub-style study heatmap grid with intensity levels.
  - Hourly productivity distribution curves (00:00 to 23:00).
  - Algorithmic Focus Score calculation (0–100) factoring productivity ratings, distractions, pause ratios, and completion status.
  - Automated smart offline productivity insights and tips.
- **Milestones & Gamification**:
  - Unlockable achievement badges based on total focus hours, streaks, and subject mastery.
  - Study streak tracking (Current Streak, Longest Streak, Active Days).
- **Data Portability & Security**:
  - Full offline database architecture built on AndroidX Room and SQLite.
  - 100% on-device data guarantee with zero telemetry, zero analytics trackers, and zero account requirements.
  - Full JSON backup export and import with clipboard integration.
  - Universal CSV spreadsheet export for Excel, Google Sheets, and Notion.
- **Modern UI & Theming**:
  - Jetpack Compose with Material Design 3 dynamic color scheme and edge-to-edge layout.
  - Floating pill navigation bar with haptic feedback.
  - Customizable study reminders and repeat schedules.
