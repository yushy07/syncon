# PRD — SyncOn: Android App

**Scope of this document:** The Android app only, as a fully standalone, fully offline product. There is no Chrome extension, no backend, no sync, and no network permission anywhere in this app. If any future work connects this app to something else, it will be a separate effort built later, on top of this — not something to design around right now.

**Status:** Active build target for Antigravity. This is the single source of truth for exact app behavior.

---

## 1. Purpose & Background

Android's built-in Digital Wellbeing tracks phone usage but has no awareness of anything beyond the phone. Yu wants a personal, standalone Android app that does the same core job — tracking how much time he spends in each app — but with more control than Digital Wellbeing gives him: custom per-app time limits, and the ability to actually block an app once its limit is hit, with different strictness per app depending on what that app is for.

This app is being built for Yu's own personal use, on his own device, by him using an AI coding tool (Antigravity). It is not intended for publishing to the Play Store, and does not need to handle other users, other devices, or edge cases specific to a public release (like Play Store policy compliance for sensitive permissions).

## 2. Goals

- Track how much time is spent in every installed app, updated roughly every 5–10 minutes
- Automatically sort every app into a category (Social Media, Entertainment, etc.), with the ability to manually re-assign any app's category
- Let Yu set an independent daily time limit for any app
- Let Yu choose, independently per app, whether hitting that limit results in a hard "Strict" block or a "Soft" block that can be snoozed
- Warn before a block happens, not just block with no notice
- Treat each "day" as running 4:00 AM to 4:00 AM, not midnight to midnight, since that's when Yu wants his daily counters to reset
- Keep working automatically through a phone reboot — no manual restart step
- Never permanently lose usage data, even if Android kills the background tracking process
- Let Yu see usage trends over the last 7 days and last 30 days, not just "today"
- Keep 3 years of history before auto-deleting old data
- Work reliably as a single-user, single-device, fully local app — no login, no account, no internet required

## 3. Non-Goals (Explicitly Out of Scope for This App)

- No Chrome extension, no browser tracking of any kind
- No backend, no server, no cloud storage, no sync of any kind, no network permission
- No multi-user accounts, no login screen
- No home screen widget
- No guarantee of matching Android Digital Wellbeing's numbers exactly (explained in §8 — this is a platform limitation, not a target to chase)
- No Play Store publishing considerations (this is a personal sideloaded app)

## 4. Users & Usage Context

- Single user: Yu
- Single device: Yu's personal Android phone
- Usage context: installed once, run continuously in the background indefinitely, occasionally opened to check stats or adjust a limit

## 5. Target Platform & Technical Environment

- **Minimum SDK:** Android 12 (API level 31)
- **Target SDK:** the latest stable SDK available at build time
- **Language:** Kotlin
- **UI toolkit:** Jetpack Compose
- **Local database:** Room (SQLite under the hood)
- **Concurrency:** Kotlin Coroutines
- **Permissions model:** all special/runtime permissions requested and explained through in-app onboarding, not just fired as raw system dialogs with no context
- **Network:** none. This app should compile and run with zero network permissions declared.

---

## 6. Functional Requirements

### 6.1 Usage Tracking

- Track foreground time per installed app, at the app (package) level — not per-screen or per-activity inside an app, and not per-website inside a browser (that level of detail is not needed here; total time in "Chrome" as a whole app is enough).
- **Data source:** Android's `UsageStatsManager`.
  - Use `queryEvents()` to read raw usage events rather than relying solely on the higher-level `queryUsageStats()` aggregate, since events give the precision needed for 5–10 minute incremental updates.
  - Be aware that the classic `UsageEvents.Event.MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` constants exist but that on modern Android versions, `ACTIVITY_RESUMED` and `ACTIVITY_PAUSED` are the more reliable and recommended event types for detecting when an app enters/leaves the foreground. Antigravity should use whichever pairing is correct and reliable for the target API level — test this specifically, since usage event semantics have shifted across Android versions and getting this wrong silently produces incorrect durations.
- **All installed apps are tracked, with zero filtering.** System apps, pre-installed apps, everything — nothing is hidden from tracking or from the app list UI.

### 6.2 Update Frequency — Critical Technical Constraint

- Usage totals must update roughly every 5–10 minutes while the device is actively being used.
- **`WorkManager`'s `PeriodicWorkRequest` has a hard, OS-enforced minimum repeat interval of 15 minutes.** This is not configurable and not a bug to work around — it is a deliberate Android system limitation. Do not attempt to configure `WorkManager` for a 5–10 minute interval; it will silently get clamped to 15 minutes minimum, and will NOT satisfy this requirement.
- **Correct approach:** implement a **Foreground Service** that runs its own internal timing loop, independent of `WorkManager`, e.g.:
  ```kotlin
  class ForegroundTrackingService : Service() {
      private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

      override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
          startForeground(NOTIFICATION_ID, buildOngoingNotification())
          scope.launch {
              while (isActive) {
                  performTrackingTick()
                  delay(5 * 60 * 1000L) // 5 minutes
              }
          }
          return START_STICKY
      }
  }
  ```
- This service requires a persistent (ongoing) notification per Android's foreground service rules. Keep this notification low-importance/minimized so it doesn't feel intrusive — it's expected to always be present while the service runs, and that's fine for this use case.
- Declare an appropriate `foregroundServiceType` in the manifest for this service (Android has tightened requirements around this in recent versions — check the current requirement for the target SDK at build time, and pick the type that best matches "ongoing local device monitoring," commonly `specialUse` with an accompanying `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` description if using that type).

### 6.3 App Categorization

- On first encountering any installed app, it is auto-assigned a category from a bundled static mapping table (package name → category string). A reasonable starting set of categories and examples:

| Category | Example Apps |
|---|---|
| Social Media | Instagram, Facebook, X/Twitter, Snapchat, TikTok, Reddit |
| Entertainment | YouTube, Netflix, Prime Video, Spotify, Disney+ |
| Browser | Chrome, Firefox, Edge, Brave |
| Communication | WhatsApp, Telegram, Gmail, Outlook, Messages |
| Productivity | Google Docs, Notion, Microsoft Office apps, Google Sheets |
| Games | (best-effort; can rely on the app's Play Store category metadata if easily accessible, otherwise leave for manual tagging) |
| System / Utility | Settings, Camera, Phone, Clock, Files |
| Other | fallback for anything unmatched |

- **This table does not need to be exhaustive.** Anything not matched falls into "Other," and Yu can manually recategorize any app at any time.
- Manual override: tapping into any app's detail screen lets Yu pick a different category from a fixed list (the same list used for auto-categorization, plus "Other"). Once manually set, that app's category is never overwritten by the auto-categorization logic again — this is tracked via the `isCategoryManuallySet` flag on `AppInfo`.
- **Newly installed apps** (installed after SyncOn itself has already been running for a while) must also get auto-categorized the first time they're detected — this isn't a one-time-at-launch-only operation.

### 6.4 Daily Limits & Blocking — Per App

Every app can independently be configured with:

| Field | Type | Meaning |
|---|---|---|
| `dailyLimitMinutes` | `Int?` | `null` = no limit set for this app at all |
| `blockingStyle` | `"STRICT"` \| `"SOFT"` | How blocking behaves once the limit is hit |
| `snoozeMinutes` | `Int` | Only relevant when `blockingStyle == "SOFT"`; default `5` |

**Example from Yu's own stated intent:**
- Instagram → `STRICT`, e.g. 60-minute limit (pure entertainment, no leniency wanted)
- YouTube → `SOFT`, e.g. 120-minute limit with a 5-minute snooze (used for study as well as entertainment, so some flexibility is wanted)

#### 6.4.1 Warning Notification

- Fires exactly once per usage-day, per app, when the remaining time for that app drops to or below a threshold (default: 5 minutes remaining — this can be a constant in code, doesn't need to be user-configurable in v1).
- Example notification text: *"5 minutes left for Instagram today."*
- The "already warned today" state is tracked per app via `AppDailyState.warningShown`, and resets at the 4 AM daily reset (§6.5, §7.5).
- **Edge case:** if a single 5-minute tracking tick pushes usage from, say, 8 minutes remaining straight past the limit to 0 remaining (because the app was used continuously through that whole tick), the warning may never get a chance to fire before the block does. This is acceptable — it's a known consequence of the 5-minute check granularity — but the block itself must still fire correctly regardless of whether the warning got a chance to show first.

#### 6.4.2 Blocking Trigger & Behavior

- Triggered when `usedTodayMinutes >= dailyLimitMinutes` for that specific app.
- **Detection mechanism:** an `AccessibilityService` configured to listen for `TYPE_WINDOW_STATE_CHANGED` events, which fire when the foreground app changes. This is checked independently of (and in addition to) the 5-minute tracking loop, since blocking needs to react as soon as the user opens an over-limit app — not wait up to 5 minutes.
- **STRICT apps:**
  - As soon as the blocked app is detected in the foreground, immediately bring the user out of it — recommended approach: launch the app's own full-screen `BlockedActivity` on top using `Intent.FLAG_ACTIVITY_NEW_TASK` (an alternative is redirecting Home via `Intent.ACTION_MAIN` + `Intent.CATEGORY_HOME`, but showing a dedicated `BlockedActivity` is better since it can clearly explain why the app got blocked).
  - No way to dismiss back into the blocked app. The only options from `BlockedActivity` are to leave (go Home, or switch to a different app).
  - Remains blocked for the rest of the current usage-day (until the next 4 AM reset).
- **SOFT apps:**
  - Same detection and same `BlockedActivity`, but with an additional **"Snooze +N minutes"** button (where N = that app's configured `snoozeMinutes`).
  - Tapping snooze extends that app's effective limit for the rest of today by `snoozeMinutes`, dismisses the blocked screen, and lets the user back into the app.
  - Every snooze action is logged as a `BlockEvent` row (type `"SNOOZED"`) so the app's history reflects how often limits were extended — useful for Yu to notice if he's snoozing YouTube every single day, for example.
- **Recommendation on implementation approach:** build blocking via `AccessibilityService` detection + launching your own `Activity` on top, rather than using a `SYSTEM_ALERT_WINDOW` overlay. This avoids needing an additional sensitive permission and is simpler to implement correctly.
- **Re-triggering:** if a blocked app is backgrounded and then re-opened again later the same usage-day, the block must fire again immediately — the app should not "forget" that this app is already over its limit for today.
- **Back button / Home button on the blocked screen:** pressing back or Home from `BlockedActivity` should behave like normal navigation away (fine), but should NOT be a way to sneak back into the blocked app underneath it — make sure `BlockedActivity` is genuinely on top of the task stack, not just visually covering it.

### 6.5 Usage-Day Definition — 4:00 AM to 4:00 AM, Not Midnight

This is one of the most important and easiest-to-get-wrong details in the whole app. **Do not use the device's calendar day (midnight to midnight) anywhere in this app's logic.** Instead:

- A "usage day" runs from **4:00 AM to the next 4:00 AM**.
- Example: usage that happens at 1:00 AM on a Tuesday is counted as part of **Monday's** usage-day, because it's before 4 AM.
- Every place that computes "today," queries "today's usage," checks a daily limit, or triggers the daily reset must consistently use this 4 AM boundary. There should be exactly one function in the codebase responsible for this calculation (see `UsageDayCalculator` in the roadmap's suggested package structure, and the pseudocode in §7.2 below) — every other part of the app calls into that single function rather than re-implementing the boundary logic in multiple places.
- The daily reset job (§7.5) runs at 4:00 AM and is what actually rolls the "current usage-day" forward and clears the daily flags.

### 6.6 Restart Persistence (Surviving Reboot)

- The app must resume tracking and blocking automatically after the phone reboots — Yu should never need to manually reopen the app after a restart for tracking to resume.
- **Implementation:** a `BroadcastReceiver` registered for the `BOOT_COMPLETED` action, which starts `ForegroundTrackingService`.
- **Important technical detail:** starting a foreground service from a `BOOT_COMPLETED` broadcast receiver is one of the specifically allowed exceptions to Android's background-service-start restrictions (these restrictions mainly target apps trying to start services from the background outside of a small set of allowed triggers, and boot completion is one of them). The requirement that still applies: the service must call `startForeground()` with a valid notification within 5 seconds of being started, or Android will kill it. Make sure this timing is respected — don't do heavy initialization work before calling `startForeground()`.
- Also double check the manifest's `foregroundServiceType` declaration is compatible with being started this way on the target SDK version, since Android's rules here have gotten stricter release over release.

### 6.7 Data Gap Reconciliation — No Missing Data, Ever

- If `ForegroundTrackingService` gets killed by the OS for any reason (memory pressure, an aggressive OEM battery manager, etc.) and stops updating for a period of time, **no usage data should be permanently lost.**
- This works because `UsageStatsManager` is a system-level service that keeps recording usage events regardless of whether this app's own process is alive. The app just needs to "catch up" on reading those events later — nothing is actually lost at the source, only the app's own copy of it can fall behind.
- **Reconciliation trigger points:** run the reconciliation check both when `MainActivity` resumes (the user opens the app) AND when `ForegroundTrackingService` itself starts up (in case the service restarts before the user manually opens the app).
- **Reconciliation logic:**
  1. Read `AppConfig`'s `last_synced_at` timestamp.
  2. If `now - last_synced_at` exceeds a small buffer (e.g. 6 minutes — slightly more than one normal tracking tick, to avoid unnecessary reconciliation runs on every single normal tick), query `UsageStatsManager.queryEvents(last_synced_at, now)`.
  3. Process those events exactly the same way the normal tracking tick does (§7.1), upserting into `DailyUsage`.
  4. Update `last_synced_at` to `now`.
- **Idempotency / no double-counting:** because reconciliation and the normal tracking loop both read from the same `last_synced_at` marker and always advance it forward after processing, the same event window should never be processed twice. Antigravity should make sure this marker is updated atomically/reliably (e.g. within the same transaction as the `DailyUsage` upserts, or immediately after, with care taken that a crash between the two doesn't cause duplicate processing on the next run).
- **Manual test scenario:** force-stop the app via Android's App Info screen (a stronger kill than simply backgrounding it), use several different apps normally for 20+ minutes, then reopen SyncOn. The usage during that gap should appear correctly and exactly once — not missing, not doubled.

### 6.8 History & Trends

- Required views:
  - **Today** — per-app breakdown plus a total
  - **Last 7 days** — per-day total (chart) plus per-app or per-category breakdown for the period
  - **Last 30 days** — same shape as the 7-day view, longer window
- All of these queries must use the usage-day (4 AM boundary) definition consistently — "today" always means the current usage-day, not the current calendar day, and "last 7 days" means the last 7 usage-days.
- Charts can be simple (bar charts for daily totals are sufficient) — visual polish is not the priority here, correctness of the underlying numbers is.

### 6.9 Data Retention

- Keep 3 years (1095 days) of `DailyUsage` history.
- A periodic cleanup job (daily is fine, bundled with the 4 AM reset job in §7.5) deletes any `DailyUsage` row where `usageDate` is older than 1095 days from the current usage-day.
- `BlockEvent` log rows can follow the same retention window for consistency, unless Antigravity finds a strong reason to treat them differently (not required either way for v1).

### 6.10 Permissions Required — Full List

| Permission | Purpose | Requested how |
|---|---|---|
| `PACKAGE_USAGE_STATS` (Usage Access) | Read per-app foreground usage time | Special app-ops permission, granted via a dedicated Settings screen — deep-linked from onboarding |
| Accessibility Service | Detect foreground app changes to trigger blocking | Special permission, granted via Accessibility Settings — deep-linked from onboarding |
| Ignore Battery Optimizations | Keep the foreground tracking service alive reliably in the background | Requested via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent — deep-linked from onboarding |
| `RECEIVE_BOOT_COMPLETED` | Restart tracking automatically after a device reboot | Normal manifest permission, no runtime prompt |
| `FOREGROUND_SERVICE` (+ specific type, e.g. `FOREGROUND_SERVICE_SPECIAL_USE`) | Legally run the always-on tracking loop as a foreground service | Normal manifest permission |
| Query all installed packages (`QUERY_ALL_PACKAGES` or equivalent, depending on target SDK's package-visibility rules) | List every installed app for tracking and categorization, with no filtering | Normal manifest permission — not a Play Store concern here since this app isn't being published |

---

## 7. Core Logic (Reference Pseudocode)

This section is meant to remove ambiguity about exactly how the moving pieces fit together. Treat this as the intended logic, not just inspiration — deviating from it should be a deliberate, flagged decision, not an accident.

### 7.1 Tracking Loop (inside `ForegroundTrackingService`)

```
every 5 minutes, while the service is alive:
    events = UsageStatsManager.queryEvents(lastCheckTime, now)
    group events into per-app foreground/background pairs
    for each app with a valid foreground-to-background (or foreground-to-now, if still open) duration:
        durationMs = endTime - startTime
        usageDate = UsageDayCalculator.getUsageDate(startTime)
        Repository.upsertDailyUsage(packageName, usageDate, addMinutes = durationMs / 60000)
    lastCheckTime = now
    AppConfig.set("last_synced_at", now)
    runLimitCheckForCurrentForegroundApp()
```

### 7.2 UsageDayCalculator.getUsageDate(timestamp)

```
localDateTime = convert timestamp to the device's current local date/time
if localDateTime.hour < 4:
    return localDateTime.date.minusDays(1)
else:
    return localDateTime.date
```

This single function must be the ONLY place in the codebase that implements this rule. Every other piece of logic that needs "what usage-day does this timestamp belong to" calls this function.

### 7.3 Limit Check (runs on every AccessibilityService foreground-app-change event, and also at the end of every tracking tick as a safety net)

```
on foregroundAppChanged(packageName) OR on trackingTickCompleted():
    settings = Repository.getLimitSettings(packageName)
    if settings == null or settings.dailyLimitMinutes == null:
        return  // no limit configured for this app, nothing to do

    today = UsageDayCalculator.getUsageDate(now)
    usedToday = Repository.getDailyUsage(packageName, today)?.durationMinutes ?: 0
    dailyState = Repository.getOrCreateDailyState(packageName, today)

    remaining = settings.dailyLimitMinutes - usedToday

    if remaining <= WARNING_THRESHOLD_MINUTES and not dailyState.warningShown:
        NotificationHelper.showWarning(packageName, remaining)
        Repository.updateDailyState(dailyState.copy(warningShown = true))

    if usedToday >= settings.dailyLimitMinutes:
        launchBlockedActivity(
            packageName = packageName,
            allowSnooze = (settings.blockingStyle == "SOFT"),
            snoozeMinutes = settings.snoozeMinutes
        )
```

### 7.4 Snooze Action (triggered from `BlockedActivity`)

```
on snoozeButtonTapped(packageName):
    settings = Repository.getLimitSettings(packageName)
    today = UsageDayCalculator.getUsageDate(now)
    Repository.logBlockEvent(packageName, today, type = "SNOOZED")
    Repository.extendEffectiveLimitForToday(packageName, today, extraMinutes = settings.snoozeMinutes)
    dismissBlockedActivity()
```

(Note: "extending the effective limit for today" can be implemented either by tracking an `extraSnoozeMinutesUsedToday` counter on `AppDailyState` that gets added to `dailyLimitMinutes` when checking `usedToday >= effectiveLimit`, or by any equivalent approach — the important behavior is that the limit check in §7.3 must account for any snoozes already used today.)

### 7.5 Daily Reset & Retention Cleanup (scheduled daily at 4:00 AM — a `WorkManager` `PeriodicWorkRequest` is appropriate here, since this is a once-a-day job, not the 5-minute tracking loop)

```
at 4:00 AM daily:
    for each row in AppDailyState:
        reset warningShown = false
        reset isBlocked = false
        reset extraSnoozeMinutesUsedToday = 0
    delete DailyUsage rows where usageDate < (today - 1095 days)
    delete BlockEvent rows where usageDate < (today - 1095 days)
```

### 7.6 Gap Reconciliation (on `MainActivity.onResume()` and on `ForegroundTrackingService` startup)

```
lastSynced = AppConfig.get("last_synced_at") ?: appInstallTime
if (now - lastSynced) > 6 minutes:
    events = UsageStatsManager.queryEvents(lastSynced, now)
    process identically to the 7.1 tracking loop, using these historical events
AppConfig.set("last_synced_at", now)
```

---

## 8. Known Limitations (Set Expectations Up Front, Do Not Try to "Fix" These)

- **Usage numbers will be close to, but not guaranteed identical to, Android's built-in Digital Wellbeing.** Digital Wellbeing has access to internal system-level APIs that third-party apps cannot use. `UsageStatsManager` is the best publicly available equivalent and should be accurate within a small margin, but exact parity is not achievable and is not a goal (see §3, Non-Goals).
- **Accessibility Service–based blocking may have a small delay (roughly 1–2 seconds) after an app is opened**, since detecting the foreground-app change isn't instantaneous. This is acceptable for this app's purpose.
- **Some Android OEMs (e.g. certain Xiaomi, Oppo, Vivo devices) apply extra-aggressive background process killing beyond stock Android**, even when battery optimization is disabled through the standard Android setting. If Yu's device is one of these, an additional OEM-specific "autostart" or "protected apps" setting may need to be enabled manually outside of what this app can control programmatically. This isn't something to build a workaround for in code — just something to be aware of if tracking seems to drop out unexpectedly despite everything above being implemented correctly.

## 9. Non-Functional Requirements

- The app is expected to run an always-on foreground service plus an always-on accessibility service. Some amount of battery usage beyond a typical lightweight app is an accepted tradeoff for this feature set (Yu has already agreed to disable battery optimization for this app specifically).
- No crashes during a continuous 24-hour real-world usage test.
- The app must remain fully functional with zero internet connectivity at all times — this isn't just "works offline," it's "never attempts to go online in the first place."
- Database queries for the Trends screens (7-day, 30-day) should feel instant in normal use — with only a single device's worth of personal usage data, performance should not be a real concern, but make sure the `@Index` on `usageDate` (per the roadmap's Phase 2) is in place so date-range queries don't do a full table scan as history grows toward the 3-year retention limit.

## 10. Data Model (Room Database) — Full Schema

```kotlin
@Entity(tableName = "app_info")
data class AppInfo(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,                  // e.g. "Social Media", "Entertainment", "Other"
    val isCategoryManuallySet: Boolean = false,
    val isSystemApp: Boolean = false
)

@Entity(
    tableName = "daily_usage",
    primaryKeys = ["packageName", "usageDate"],
    indices = [Index(value = ["usageDate"])],
    foreignKeys = [ForeignKey(
        entity = AppInfo::class,
        parentColumns = ["packageName"],
        childColumns = ["packageName"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class DailyUsage(
    val packageName: String,
    val usageDate: String,                 // ISO date "YYYY-MM-DD", per the 4AM-4AM usage-day rule
    val durationMinutes: Long,
    val lastUpdatedAt: Long                 // epoch millis
)

@Entity(
    tableName = "app_limit_settings",
    foreignKeys = [ForeignKey(
        entity = AppInfo::class,
        parentColumns = ["packageName"],
        childColumns = ["packageName"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AppLimitSettings(
    @PrimaryKey val packageName: String,
    val dailyLimitMinutes: Int?,            // null = no limit
    val blockingStyle: String,              // "STRICT" or "SOFT"
    val snoozeMinutes: Int = 5,
    val isEnabled: Boolean = true
)

@Entity(
    tableName = "app_daily_state",
    indices = [Index(value = ["packageName", "usageDate"], unique = true)],
    foreignKeys = [ForeignKey(
        entity = AppInfo::class,
        parentColumns = ["packageName"],
        childColumns = ["packageName"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AppDailyState(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val usageDate: String,
    val warningShown: Boolean = false,
    val isBlocked: Boolean = false,
    val extraSnoozeMinutesUsedToday: Int = 0
)

@Entity(
    tableName = "block_event_log",
    foreignKeys = [ForeignKey(
        entity = AppInfo::class,
        parentColumns = ["packageName"],
        childColumns = ["packageName"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class BlockEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val usageDate: String,
    val eventType: String,                  // "WARNING_SHOWN", "BLOCKED", "SNOOZED"
    val timestamp: Long
)

@Entity(tableName = "app_config")
data class AppConfig(
    @PrimaryKey val key: String,            // e.g. "last_synced_at"
    val value: String
)
```

Room database class should be declared at `version = 1`. Since this is a brand-new app with no existing installs to migrate, no migration path needs to be written yet — just leave the database open to adding one later if the schema ever changes after v1 ships.

---

## 11. Edge Cases & Error Handling

Each of these must be deliberately handled, not just left to "probably work":

1. **Usage Access permission revoked mid-use** (user goes into Settings and turns it off after initially granting it) — the app should detect this (e.g., re-check permission status periodically, or when `MainActivity` resumes) and show a clear in-app warning rather than crashing or silently producing zeroed-out data.
2. **Accessibility Service disabled mid-use** — blocking stops working, but usage tracking should continue unaffected (tracking depends on Usage Access, not Accessibility). Show a warning in Settings so Yu notices blocking has stopped, rather than wondering why an over-limit app suddenly stopped blocking.
3. **A tracked app gets uninstalled** — its `AppInfo` row can either be kept (marked somehow as no longer installed) or removed; if removed, the `onDelete = CASCADE` foreign keys ensure its `DailyUsage`, `AppLimitSettings`, `AppDailyState`, and `BlockEvent` rows don't become orphaned garbage. Either approach is fine as long as the App List screen doesn't crash or show a broken entry for an app that no longer exists on the device.
4. **Device clock changed manually** (user sets the time backward or forward) — the usage-day calculation and tracking logic should not throw exceptions or corrupt data if it encounters non-monotonic timestamps. It doesn't need to "correct" for this — just needs to not crash, and any resulting weirdness in that day's numbers is an acceptable, known consequence of the user changing their clock.
5. **First run, before any usage history exists** — Dashboard and Trends screens must show a sensible empty/"not enough data yet" state rather than crashing on an empty dataset or rendering a broken chart.
6. **Foreground service repeatedly killed by the OS** — gap reconciliation (§6.7) already covers this for data correctness; additionally, confirm this doesn't cause a crash loop or a battery-draining restart loop if it happens frequently.
7. **A newly installed app appears while the app is already running** — it should get picked up and auto-categorized without requiring Yu to reinstall or manually refresh anything (see roadmap Phase 5).

---

## 12. Testing & QA Checklist

This is the full manual verification list referenced by the roadmap's Phase 11. Every item should be checked on a real device, not assumed from reading the code.

- [ ] Fresh install → onboarding correctly shows all 3 permissions as ungranted
- [ ] Each permission's "Grant" button correctly deep-links to the right system settings screen
- [ ] After granting all 3, the app proceeds into the main experience without needing a reinstall
- [ ] Using several different apps for 15+ minutes results in correct, roughly-5-minutes-granular updates in `DailyUsage` (verified via direct database inspection)
- [ ] Force-stopping the app, using the phone for 20+ minutes, then reopening it results in complete, non-duplicated gap-reconciled data
- [ ] All installed apps appear in the App List with a sensible auto-assigned category
- [ ] Manually changing an app's category persists and is never silently overwritten afterward
- [ ] Installing a brand-new app while SyncOn is already running results in that new app appearing and being categorized without extra steps
- [ ] Setting a short test limit (e.g. 2 minutes) on a Strict app: warning fires close to the threshold, block fires at the limit, block cannot be bypassed, block re-triggers if the app is reopened later the same usage-day
- [ ] Setting the same short test limit on a Soft app: same as above, but the Snooze button appears and correctly extends the limit when tapped, and the snooze is logged
- [ ] Usage between midnight and 4 AM is correctly attributed to the previous usage-day, not the new calendar day
- [ ] The 4 AM reset (verified via a debug trigger) correctly clears `warningShown`, `isBlocked`, and `extraSnoozeMinutesUsedToday` for every app
- [ ] Data older than the retention window is correctly pruned by the daily cleanup job (can be tested with an artificially shortened retention window during development, then set back to 1095 days for the real build)
- [ ] Rebooting the device results in tracking and blocking resuming automatically, with zero manual interaction
- [ ] Revoking Usage Access after initial setup surfaces a clear warning rather than silent failure
- [ ] Revoking Accessibility Service after initial setup surfaces a clear warning, while usage tracking continues to work
- [ ] Uninstalling a tracked app does not crash the App List or any screen referencing that app afterward
- [ ] Dashboard and Trends screens render a sensible empty state on a completely fresh install with no data yet
- [ ] A full 24-hour continuous soak test produces no crashes and no unexplained gaps in the data

---

## 13. Glossary (For Reference)

- **Usage-day:** a 24-hour period running from 4:00 AM to the next 4:00 AM, used everywhere "today" or "daily" is mentioned in this app — deliberately not the same as the calendar day.
- **Strict blocking:** once an app's daily limit is hit, it stays blocked with no way to extend or bypass until the next usage-day's reset.
- **Soft blocking:** once an app's daily limit is hit, the user can tap "Snooze" to extend that app's limit by a configured number of extra minutes, as many times as needed (each snooze is logged).
- **Gap reconciliation:** the process of catching up on usage data that accumulated while the tracking service wasn't actively running, using Android's system-level usage history rather than the app's own (potentially interrupted) live tracking.
