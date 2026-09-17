# SyncOn — Android App Roadmap

**Scope of this document:** The Android app ONLY. There is no Chrome extension or backend work in this roadmap — those are separate future efforts and are intentionally not covered here. Antigravity should treat this as a fully standalone, offline Android app project with no plan to network with anything else, at least not in any code it writes right now.

**Owner:** Yu (solo developer, personal use, single device)
**Build tool:** Antigravity IDE

---

## ⚠️ Rules for Antigravity — Read Before Writing Any Code

1. **Do not reference, scaffold, or leave placeholder code for a Chrome extension or a backend/server/API/Firebase/network layer.** This app has zero network permissions and zero network code. If you find yourself importing `Retrofit`, `OkHttp`, `Firebase`, or adding an `INTERNET` permission — stop, that's out of scope.
2. **Work through the phases below in order.** Each phase builds on the previous one's data/services. Do not jump ahead to UI polish before the tracking engine actually works, for example.
3. **Every phase has a "Definition of Done" — do not mark it complete until every item in that list is true on a real installed build, not just "compiles without errors."**
4. **`prd.md` is the exact behavior spec.** This roadmap tells you *what order to build things in* and *what counts as done*. If you're unsure exactly how something should behave, the answer is in `prd.md`, not something to guess or invent.
5. **If a requirement seems ambiguous or you think a feature is missing, stop and ask — do not silently invent scope.** This has happened before and caused rework.
6. **Keep the project buildable at every phase.** Don't leave half-finished features that crash the app; a phase that's "in progress" should still result in an app that opens and doesn't crash.

---

## Suggested Package Structure

Antigravity should organize the codebase roughly like this (adjust naming to whatever package name is chosen, but keep this general shape):

```
com.yu.syncon/
├── data/
│   ├── local/
│   │   ├── entity/        (Room @Entity classes — one file per table)
│   │   ├── dao/            (Room @Dao interfaces)
│   │   └── AppDatabase.kt  (Room database class, version + migrations)
│   ├── repository/         (Repository classes wrapping DAOs + UsageStatsManager access)
│   └── model/              (Plain domain/UI models, if different from DB entities)
├── service/
│   ├── tracking/           (ForegroundTrackingService + the tracking loop logic)
│   ├── accessibility/      (BlockAccessibilityService)
│   └── receiver/           (BootReceiver for BOOT_COMPLETED)
├── ui/
│   ├── onboarding/         (permission request screens)
│   ├── dashboard/          (today's summary screen)
│   ├── applist/            (list of all apps with usage + quick settings entry)
│   ├── appdetail/          (per-app limit/blocking-style settings + that app's trend)
│   ├── trends/             (weekly/monthly views)
│   ├── settings/           (permission status, data retention info, about)
│   └── blocked/            (the full-screen "you're blocked" activity)
├── util/
│   ├── UsageDayCalculator.kt   (the 4AM-boundary date logic — used everywhere usage-day matters)
│   ├── PermissionUtils.kt
│   └── NotificationHelper.kt
└── di/ (optional — simple manual dependency wiring is fine for a solo project; do not over-engineer with a DI framework unless it's already comfortable)
```

---

## Phase 0 — Project Foundation

**Goal:** A blank but correctly configured project that builds and runs, with all base tooling in place before any feature work starts.

- [ ] Kotlin + Jetpack Compose project created
- [ ] `minSdk = 31` (Android 12), `targetSdk` = latest stable at build time
- [ ] Gradle dependencies added: Room, Room KTX, Kotlin Coroutines, Jetpack Compose (BOM), Navigation Compose, a simple charting approach (either a lightweight Compose charting library or plain Canvas-based custom charts — Antigravity's choice, keep it simple)
- [ ] Base app theme set up (Material 3 is fine, no specific branding needed for a personal-use app)
- [ ] Package structure created as shown above, even if folders are initially empty

**Definition of Done:** App builds, installs, and shows a blank/placeholder home screen with no crashes.

---

## Phase 1 — Permissions & Onboarding Flow

**Goal:** Before any tracking logic exists, the app must be able to detect, request, and confirm all the special permissions it needs. This has to be rock solid since literally everything else depends on these permissions being granted.

- [ ] Build a permission-status checker utility that can check, at any time, whether each of these is currently granted:
  - Usage Access (`AppOpsManager` check against `OPSTR_GET_USAGE_STATS`, or equivalent)
  - Accessibility Service enabled (check `Settings.Secure.ACCESSIBILITY_ENABLED` + the enabled services list for this app's service)
  - Battery optimization ignored (`PowerManager.isIgnoringBatteryOptimizations()`)
- [ ] Build an onboarding screen (shown on first launch, and re-shown any time a required permission is missing) that:
  - Lists all 3 permissions with a live status indicator (granted / not granted) next to each
  - Each has a "Grant" button that deep-links to the correct system settings screen:
    - Usage Access → `Settings.ACTION_USAGE_ACCESS_SETTINGS`
    - Accessibility → `Settings.ACTION_ACCESSIBILITY_SETTINGS`
    - Battery optimization → `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (with the app's package URI)
  - Re-checks status automatically when the user returns to the app (use `onResume` in the hosting Activity)
  - Blocks navigation into the rest of the app until all 3 are granted
- [ ] Register `RECEIVE_BOOT_COMPLETED` in the manifest (no runtime prompt needed for this one, it's a normal manifest permission)

**Definition of Done:** Fresh install → app shows onboarding → all 3 permissions can be granted through the app's own buttons → once all granted, the app proceeds past onboarding automatically without needing a manual "Continue" tap (or with one final explicit "Continue" tap, either is fine — Antigravity's call).

---

## Phase 2 — Data Layer (Room Database)

**Goal:** All persistent storage exists and is tested in isolation before anything else tries to read/write to it.

- [ ] Create every entity exactly as specified in `prd.md` §6: `AppInfo`, `DailyUsage`, `AppLimitSettings`, `AppDailyState`, `BlockEvent`, `AppConfig`
- [ ] Add an `@Index` on `DailyUsage.usageDate` (range queries by date will be common — for trends screens)
- [ ] Add a foreign key relationship (with `onDelete = CASCADE`) from `DailyUsage`, `AppLimitSettings`, `AppDailyState`, and `BlockEvent` back to `AppInfo.packageName`, so that if an app entry is ever removed, its related rows clean up automatically
- [ ] Write the Room `Database` class with `version = 1` (leave a comment showing how a future migration would be added, but don't build migration logic yet since there's nothing to migrate from)
- [ ] Write a DAO interface for each entity, covering: insert, update, upsert (where relevant), delete, and the specific queries needed for trends (e.g. "get all DailyUsage rows between two dates")
- [ ] Write a thin Repository layer on top of the DAOs — this is what the rest of the app talks to, never the DAOs directly

**Definition of Done:** A temporary test screen (can be deleted later) can successfully insert a row into each table and read it back. No schema errors, no crashes.

---

## Phase 3 — Usage Tracking Engine (Foreground Service)

**Goal:** The core feature. The app can continuously track how long each app is used, at the required 5–10 minute cadence, saved into the database.

- [ ] Build the `UsageDayCalculator` utility first (`prd.md` §5.5, §7.2) — this is a pure function with no dependencies, easy to build and verify on its own before anything else uses it
- [ ] Create a Notification Channel for the foreground service (low importance, since this is just an "always on" indicator, not something that should interrupt Yu)
- [ ] Build `ForegroundTrackingService`:
  - Starts in the foreground with the required notification within 5 seconds of being started (Android requirement)
  - Runs an internal coroutine loop: check usage stats every 5 minutes (do NOT use `WorkManager` for this loop — see `prd.md` §5.2 for exactly why it won't work)
  - On each tick: query `UsageStatsManager` for events since the last check, compute per-app duration deltas, and upsert into `DailyUsage` via the Repository
- [ ] Wire up a way to start this service automatically once onboarding completes (Phase 1) — from this point forward, the service should always be the thing keeping usage data current
- [ ] Manual test: use the phone normally for 15–20 minutes across a few different apps, then check the database directly (via Android Studio's / Antigravity's database inspector, not just the UI) to confirm per-app durations look correct and update roughly every 5 minutes

**Definition of Done:** With the service running, real usage data accumulates correctly in `DailyUsage` for multiple apps, verified by direct database inspection, not just visually skimming a UI screen.

---

## Phase 4 — Gap Reconciliation

**Goal:** No usage data is ever silently lost, even if Android kills the tracking service in the background.

- [ ] Implement the reconciliation function described in `prd.md` §7.6, using `AppConfig`'s `last_synced_at` key as the reference point
- [ ] Call this reconciliation function from `MainActivity.onResume()` AND from the tracking service's own startup (`onCreate`/`onStartCommand`), so it catches up regardless of which one runs first after a gap
- [ ] Guard against double-counting: if reconciliation and the normal tracking loop ever overlap in the same time window, the upsert into `DailyUsage` should be additive only for genuinely new event data, not reprocessing the same events twice (track exactly which time range has already been processed)
- [ ] Manual test: force-stop the app from Android's App Info screen (a harder kill than just backgrounding it), use the phone normally for 20+ minutes across a few apps, then reopen the SyncOn app — confirm that gap's usage data appears correctly, not missing and not doubled

**Definition of Done:** The force-stop-and-reopen test above passes with accurate, non-duplicated data every time.

---

## Phase 5 — App List & Categorization

**Goal:** Yu can see every installed app with an assigned category, and can override that category manually.

- [ ] Query all installed apps via `PackageManager` (no filtering — system apps included, per `prd.md` §5.1)
- [ ] Populate `AppInfo` for every installed app not already in the table (app name, package name, `isSystemApp` flag)
- [ ] Build the bundled category-mapping list described in `prd.md` §5.3 and auto-assign categories on first insert
- [ ] Build the App List screen: scrollable list of all apps, each row showing app icon, name, category, and today's usage so far
- [ ] Build the ability to tap into an app and manually change its category (this sets `isCategoryManuallySet = true` so future auto-categorization logic never overwrites a manual choice)
- [ ] Handle newly installed apps discovered later (not just at first launch) — e.g., re-scan the installed app list periodically or on app open, and insert any new ones found

**Definition of Done:** Every installed app appears in the list with a sensible category, manual overrides stick permanently, and newly installed apps show up without needing a fresh install of SyncOn itself.

---

## Phase 6 — Limits & Blocking (Accessibility Service)

**Goal:** The actual behavior Yu asked for by name — per-app limits, warnings, and blocking (strict or soft with snooze).

- [ ] Build the App Detail screen: for a given app, let Yu set/clear a daily limit (minutes), choose blocking style (Strict/Soft), and (if Soft) set the snooze duration
- [ ] Build `BlockAccessibilityService`, configured via its XML config to listen for `TYPE_WINDOW_STATE_CHANGED` events
- [ ] On every foreground-app-change event, run the limit check logic from `prd.md` §7.3
- [ ] Build the warning notification (fired once per usage-day per app, per `prd.md` §5.4.1)
- [ ] Build the full-screen `BlockedActivity`:
  - Launched on top of the blocked app via `FLAG_ACTIVITY_NEW_TASK` (see `prd.md` §5.4.2 for why this approach over `SYSTEM_ALERT_WINDOW`)
  - Shows which app is blocked and why
  - For Strict apps: no dismiss option other than leaving (Home button / back to a different app)
  - For Soft apps: shows a "Snooze +N minutes" button that extends today's effective limit and logs a `BlockEvent`
- [ ] Make sure the block re-triggers correctly if the user backgrounds the blocked app and re-opens it again the same day (it shouldn't "forget" that this app is over its limit)
- [ ] Manual test per `roadmap.md`'s own suggestion: set a 2-minute limit on a throwaway test app, verify the warning fires close to the threshold, verify the block fires right at the limit, verify snooze behavior if set to Soft, verify Strict really cannot be bypassed

**Definition of Done:** Both Strict and Soft blocking styles work correctly and reliably on a real device, across multiple test apps, including re-triggering after backgrounding a blocked app.

---

## Phase 7 — Daily Reset & Data Retention

**Goal:** Usage counters and flags reset cleanly at 4 AM every day, and old data gets cleaned up automatically.

- [ ] Implement the 4 AM daily reset job from `prd.md` §7.5 (a daily `WorkManager` periodic task is fine here — this is NOT the 5-10 minute tracking loop, so `WorkManager`'s 15-minute-minimum limitation doesn't apply; daily granularity is exactly what `WorkManager` is good at)
- [ ] Implement the data-pruning logic (delete `DailyUsage` rows older than 1095 days / 3 years) as part of the same daily job
- [ ] Manual test: use a debug-only trigger (a hidden button, or a Antigravity-added test hook) to simulate the reset firing, and confirm all `AppDailyState` flags clear correctly

**Definition of Done:** The reset job reliably fires daily and can be verified to work correctly via the debug trigger, without needing to literally wait for real 4 AM every time during development.

---

## Phase 8 — Dashboard & Trends UI

**Goal:** Yu actually gets to see the data in a useful way, not just have it silently collected.

- [ ] Build the main Dashboard screen: today's total screen time, plus a "top apps today" list (ranked by usage)
- [ ] Build the Trends screen with two views: Last 7 Days and Last 30 Days, each showing:
  - A simple chart (bar chart is fine) of total usage per day
  - A per-app or per-category breakdown for the selected period
- [ ] Make sure trend queries respect the usage-day (4 AM boundary) definition consistently, not calendar days

**Definition of Done:** Dashboard and Trends screens show real, correct data pulled from the database, matching what direct database inspection shows for the same period.

---

## Phase 9 — Settings Screen

**Goal:** A place to check on the app's health and permissions without digging through Android system settings manually.

- [ ] Show live status of all 3 core permissions (reuse the checker utility from Phase 1), with the ability to re-grant any that got revoked later
- [ ] Show current data retention setting (3 years, informational for now — doesn't need to be user-editable in v1 unless Antigravity finds it trivial to add)
- [ ] Basic "About" info (app name/version is enough, no need for anything fancier)

**Definition of Done:** If any permission gets revoked after initial setup (e.g. Yu manually turns off Accessibility later), the Settings screen reflects that accurately and lets him re-grant it.

---

## Phase 10 — Edge Case Handling & Resilience

**Goal:** The app doesn't quietly break or produce wrong numbers when real-world weirdness happens. Go through `prd.md` §14 (Edge Cases) one by one and confirm each is handled:

- [ ] Permission revoked mid-use (Usage Access or Accessibility) — app detects this and shows a clear warning instead of silently failing or crashing
- [ ] Tracked app gets uninstalled — its related settings/history rows are cleaned up via cascading delete, no orphaned data, no crash when its row is displayed
- [ ] Device clock changed manually (forward or backward) — usage-day computation doesn't corrupt data or throw exceptions on unexpected timestamp ordering
- [ ] First run before any usage history exists — dashboard/trends screens show a sensible "no data yet" state, not a crash or an empty broken chart
- [ ] Foreground service killed unexpectedly by the OS — already covered by gap reconciliation (Phase 4), but confirm no crash loop happens if this occurs repeatedly

**Definition of Done:** Each item above has been deliberately tested (not just assumed to work) and behaves gracefully.

---

## Phase 11 — Full Stabilization Pass

**Goal:** Final confidence pass before calling v1 "done."

- [ ] Real device reboot test: confirm tracking + blocking resume automatically with zero manual steps
- [ ] 24-hour continuous soak test: leave the phone in normal daily use for a full day, confirm no crashes, no data gaps, no runaway battery drain beyond what's expected for this kind of app
- [ ] Full walkthrough of every functional requirement in `prd.md` §5, checked off one by one against the actual running app
- [ ] Full walkthrough of the testing checklist in `prd.md` §15

**Definition of Done:** Every item in `prd.md` §5 and §15 has been manually verified on-device. Not "the code should do this" — actually confirmed on the phone.

---

## Master Completion Checklist (v1)

- [ ] Phase 0 — Project Foundation
- [ ] Phase 1 — Permissions & Onboarding
- [ ] Phase 2 — Data Layer (Room)
- [ ] Phase 3 — Usage Tracking Engine
- [ ] Phase 4 — Gap Reconciliation
- [ ] Phase 5 — App List & Categorization
- [ ] Phase 6 — Limits & Blocking
- [ ] Phase 7 — Daily Reset & Data Retention
- [ ] Phase 8 — Dashboard & Trends UI
- [ ] Phase 9 — Settings Screen
- [ ] Phase 10 — Edge Case Handling
- [ ] Phase 11 — Full Stabilization Pass

**The Android app is only "v1 complete" when every phase above is checked AND its own Definition of Done has been personally verified by Yu on a real device.**
