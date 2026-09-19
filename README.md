<div align="center">

<img src="assets/banner.png" alt="SyncOn Hero Banner" width="100%" />

<br/><br/>

<img src="assets/icons/android-chrome-192x192.png" width="88" height="88" alt="SyncOn App Icon" />

# SyncOn

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2012%2B%20(API%2031%2B)-brightgreen.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.4-purple.svg)](https://kotlinlang.org)
[![UI Toolkit](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-blueviolet.svg)](https://developer.android.com/jetpack/compose)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20%7C%20Zero%20Network-success.svg)](#privacy--zero-network-guarantee)

**A high-precision, offline-first personal digital wellbeing and app limit enforcement system for Android.**

*"Same phone. A more intentional you."*

[Features](#key-features) • [Design & Branding](#design--visual-identity) • [Architecture](#architecture) • [Usage-Day Logic](#usage-day-definition-400-am---400-am) • [Permissions](#permissions--setup) • [Getting Started](#getting-started) • [License](#license)

</div>

---

## Overview

**SyncOn** is an advanced, standalone Android application designed to give you uncompromising control over your screen time. While standard digital wellbeing tools provide passive observation, SyncOn enforces proactive, granular boundaries tailored to your daily schedule:

- 🔒 **100% Offline & Private:** Built with zero network permissions. Your personal usage data never leaves your device.
- 🌅 **Custom Usage Days:** Evaluates your day on a **4:00 AM to 4:00 AM** boundary—late night activity counts toward your actual awake period, not an arbitrary midnight reset.
- 🛡️ **Strict vs. Soft Blocking:** Enforce ironclad limits on distraction apps while maintaining flexible snoozes for work and utility tools.
- ⚡ **Data Gap Reconciliation:** Never loses usage history across reboots or background kills by reconciling with Android's system event log.

---

## Design & Visual Identity

SyncOn's brand aesthetic embodies mindfulness, calm focus, and intentional digital balance:

<div align="center">

| App Launcher (Squircle) | Round Launcher | Adaptive Foreground | Notification Icon | Web Favicon |
|:---:|:---:|:---:|:---:|:---:|
| <img src="assets/icons/android-chrome-192x192.png" width="64" height="64" alt="Launcher Squircle" /> | <img src="app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png" width="64" height="64" alt="Round Icon" /> | <img src="app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png" width="64" height="64" alt="Foreground Sprout" /> | <img src="app/src/main/res/drawable-xxhdpi/ic_stat_syncon.png" width="48" height="48" alt="Status Notification" /> | <img src="assets/icons/favicon-32x32.png" width="32" height="32" alt="Favicon" /> |
| `ic_launcher` | `ic_launcher_round` | `ic_launcher_foreground` | `ic_stat_syncon` | `favicon.ico` |

</div>

### Color Palette

| Color Token | Hex Code | Visual Role |
|---|:---:|---|
| **Dark Obsidian** | `#15182B` | Brand squircle background, adaptive icon base, status bar theme |
| **Primary Indigo** | `#4F67E0` | Primary action buttons, active navigation indicators, key highlights |
| **Warm Background** | `#FBF9F5` | Calming daylight background surface |
| **Accent Coral** | `#E06D53` | Strict blocking alert, approaching limit warnings |
| **Accent Sage** | `#3E6B5C` | Health indicators, permission granted states, calm accents |
| **Accent Amber** | `#E8A838` | Soft limit warnings, browser categories |

---

## Key Features

### ⏱️ High-Precision Tracking & Gap Reconciliation
- Tracks active foreground duration across all installed applications via `UsageStatsManager`.
- Updates counters in ~5-minute granular intervals using a persistent `ForegroundTrackingService`.
- **Automatic Gap Catch-up:** Even if killed by aggressive OEM battery managers or across system reboots, SyncOn catches up on missed events using historical timestamps upon wake-up.

### 🛡️ Dual-Tier Enforcement (Strict vs. Soft)
Configure each application with an independent daily limit:
- 🔴 **STRICT Mode:** Once the limit is exhausted, access is immediately blocked. A dedicated, non-dismissible `BlockedActivity` prevents usage until the 4:00 AM reset.
- 🟡 **SOFT Mode:** When blocked, a **"Snooze (+N mins)"** button allows temporary extension while auditing each snooze event into the database.

### 🔔 Smart Advance Warnings
- Fires a heads-up notification when remaining time drops to or below the warning threshold (default 5 minutes), preventing unexpected interruptions.

### 🌅 4:00 AM – 4:00 AM Usage-Day Boundary
- Standard calendar days reset at midnight. SyncOn uses a unified `UsageDayCalculator` ensuring usage at 1:30 AM is attributed to the previous day's counters.

### 🏷️ Intelligent App Categorization
- Automatically assigns categories (Social Media, Entertainment, Communication, Productivity, etc.) upon first detection.
- Full manual override support: user-defined category tags are locked and permanently respected.

### 📊 Trends & Long-Term Analytics
- **Today's Breakdown:** Instant visibility into top apps, category distribution, and remaining budgets.
- **7-Day & 30-Day Trends:** Interactive Jetpack Compose bar charts and summaries.
- **3-Year Retention:** Retains up to 1,095 days of indexed daily usage history with automated background pruning via `WorkManager`.

### 🧪 Comprehensive Test Coverage
- **Pure JVM Unit Tests:** Decoupled business logic allowing unit tests to run in seconds without heavy Android mocks or Robolectric.
- **Repository Validation:** Full test coverage for limit checks, active snooze calculations, warning thresholds (80%), and retention pruning.
- **Precision Time Tests:** Validates the 4:00 AM boundary logic across midnight, leap years, and edge-case timestamps.

---

## Privacy & Zero-Network Guarantee

SyncOn is built from the ground up for absolute privacy:
- 🚫 **No `android.permission.INTERNET` declared** anywhere in the manifest.
- 🚫 No analytics, crash reporters, telemetry, cloud databases, or third-party ads.
- 💾 All data is stored strictly on-device in a local Room (SQLite) database.

---

## Architecture

SyncOn follows modern Android Clean Architecture principles, leveraging Jetpack Compose for declarative UI, Coroutines/Flow for asynchronous data streams, and Room for persistence.

```mermaid
graph TD
    subgraph UI ["UI Layer (Jetpack Compose & Material 3)"]
        Dashboard["Dashboard Screen"]
        AppList["App List & Limits Screen"]
        AppDetail["App Detail & Settings"]
        Trends["7-Day & 30-Day Trends"]
        BlockedUI["BlockedActivity (Enforcement)"]
    end

    subgraph Service ["Background & Enforcement Layer"]
        FGS["ForegroundTrackingService<br/>(5-min Tracking & Catch-up)"]
        A11Y["BlockAccessibilityService<br/>(Instant App Switch Detection)"]
        Boot["BootReceiver<br/>(Reboot Persistence)"]
        Worker["DailyResetWorker<br/>(4 AM Daily Reset & Pruning)"]
    end

    subgraph Core ["Dependency Management"]
        App["SyncOnApp<br/>(Centralized Repository Provider)"]
    end

    subgraph Domain ["Repository & Utilities"]
        Repo["UsageRepository<br/>(Business Logic & Gap Reconciliation)"]
        DayCalc["UsageDayCalculator<br/>(4 AM Boundary Engine)"]
        CatMap["CategoryMapper"]
        Notif["NotificationHelper"]
    end

    subgraph Data ["Local Storage (Room / SQLite)"]
        DB[(AppDatabase)]
        AppInfoTbl["AppInfo"]
        DailyUsageTbl["DailyUsage"]
        LimitSettingsTbl["AppLimitSettings"]
        DailyStateTbl["AppDailyState"]
        BlockEventTbl["BlockEvent"]
    end

    App -.-> Repo
    UI --> Repo
    Service --> Repo
    Service --> DayCalc
    Repo --> DB
    FGS --> DayCalc
    A11Y --> BlockedUI
    A11Y --> Notif
```

### Architectural Highlights
- **Centralized Repository Provider:** Application-level singleton container (`SyncOnApp.repository`) ensures consistent state across UI, services, and background workers without third-party DI reflection overhead.
- **SQL-Level Aggregation:** Multi-day and category usage totals are computed directly in SQLite using native `SUM()` and `GROUP BY` queries in `DailyUsageDao`, minimizing memory footprints.
- **Room Schema Versioning:** Database schemas are exported and version-controlled under `app/schemas/` to guarantee safe and deterministic migrations.

---

## Tech Stack

| Component | Technology | Description |
|---|---|---|
| **Language** | [Kotlin 2.4](https://kotlinlang.org) | Modern idiomatic Kotlin with coroutines & Flow |
| **UI Toolkit** | [Jetpack Compose (BOM 2026.03)](https://developer.android.com/jetpack/compose) | Declarative UI with Material Design 3 |
| **Persistence** | [Room 2.8](https://developer.android.com/training/data-storage/room) | SQLite abstraction layer with KSP code generation & tracked schemas |
| **Background Work** | [WorkManager 2.10](https://developer.android.com/topic/libraries/architecture/workmanager) | Periodic 4 AM maintenance and retention cleanup |
| **Testing** | JUnit 4, Kotlin Coroutines Test | Fast, pure JVM unit testing with zero-dependency fakes |
| **System APIs** | `UsageStatsManager`, `AccessibilityService` | Real-time usage event monitoring and foreground detection |
| **Tooling & Build** | Android Gradle Plugin 9.3, Gradle 9.7, Java 21 | Version catalog (`libs.versions.toml`) |

---

## Usage-Day Definition (4:00 AM - 4:00 AM)

SyncOn centralizes all date boundary calculations into `UsageDayCalculator`:

```
localDateTime = convert(timestamp, deviceTimeZone)
if localDateTime.hour < 4:
    return localDateTime.date - 1 day
else:
    return localDateTime.date
```

All queries for "today's usage", daily limits, warning states, and historical charts run against this usage-day key (`YYYY-MM-DD`).

---

## Permissions & Setup

To deliver automated tracking and enforcement without root access, SyncOn requests specialized Android permissions during onboarding:

1. **Usage Access (`PACKAGE_USAGE_STATS`)**:
   - Enables querying `UsageStatsManager` for foreground app session times.
2. **Accessibility Service (`BlockAccessibilityService`)**:
   - Detects `TYPE_WINDOW_STATE_CHANGED` events to instantly trigger `BlockedActivity` when an over-limit app is launched.
3. **Battery Optimization Exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)**:
   - Ensures the foreground tracking loop runs consistently without being killed during device sleep.
4. **Boot Completed (`RECEIVE_BOOT_COMPLETED`)**:
   - Automatically restarts the tracking service whenever the device restarts.

---

## Project Structure

```
syncon/
├── assets/
│   ├── banner.png           # High-resolution repository hero banner
│   └── icons/               # Web favicons, PWA icons, vector SVG & webmanifest
├── app/
│   ├── schemas/             # Versioned Room database schemas (JSON)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/yu/syncon/
│       │   │   ├── data/
│       │   │   │   ├── local/
│       │   │   │   │   ├── dao/             # Room DAOs (DailyUsage, AppInfo, Limits, etc.)
│       │   │   │   │   ├── entity/          # Room @Entity schemas
│       │   │   │   │   └── AppDatabase.kt   # Database configuration & type converters
│       │   │   │   └── repository/          # UsageRepository (data access & business logic)
│       │   │   ├── service/
│       │   │   │   ├── accessibility/       # BlockAccessibilityService (real-time blocking)
│       │   │   │   ├── receiver/            # BootReceiver (boot listener)
│       │   │   │   ├── tracking/            # ForegroundTrackingService (5-min tracking loop)
│       │   │   │   └── worker/              # DailyResetWorker (4 AM maintenance)
│       │   │   ├── ui/
│       │   │   │   ├── appdetail/           # Per-app limit configuration
│       │   │   │   ├── applist/             # Installed apps with category filters
│       │   │   │   ├── blocked/             # Fullscreen blocking overlay screen
│       │   │   │   ├── components/          # Reusable Compose charts and controls
│       │   │   │   ├── dashboard/           # Today's metrics and summary
│       │   │   │   ├── navigation/          # Compose Navigation routes
│       │   │   │   ├── onboarding/          # Step-by-step permissions flow
│       │   │   │   ├── settings/            # App settings and permission status
│       │   │   │   ├── theme/               # Material 3 colors, typography, shapes
│       │   │   │   └── trends/              # 7-day and 30-day analytics charts
│       │   │   ├── util/
│       │   │   │   ├── CategoryMapper.kt    # Default category mapping
│       │   │   │   ├── NotificationHelper.kt# Heads-up limit warning notifications
│       │   │   │   ├── PermissionUtils.kt   # App-ops and permission checkers
│       │   │   │   └── UsageDayCalculator.kt# 4 AM boundary calculation engine
│       │   │   └── SyncOnApp.kt             # Application class & centralized repository provider
│       │   └── res/
│       │       ├── drawable/                # Play Store & in-app brand logos
│       │       ├── drawable-*/              # Monochrome status bar notification icons
│       │       ├── mipmap-anydpi-v26/       # Android 13+ adaptive & themed icons
│       │       ├── mipmap-*/                # Multi-density launcher icons (mdpi to xxxhdpi)
│       │       ├── values/                  # Strings, colors, themes, launcher background
│       │       └── xml/                     # Accessibility service configuration
│       └── test/java/com/yu/syncon/         # JVM unit tests (Repository, Calculator, Mapper)
```

---

## Getting Started

### Prerequisites
- **Android Studio** Ladybug (or Antigravity IDE)
- **JDK 21**
- **Android Device or Emulator** running **Android 12 (API level 31)** or higher

### Build & Run

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yushy07/syncon.git
   cd syncon
   ```

2. **Run Automated Unit Tests:**
   ```bash
   ./gradlew test
   ```

3. **Build Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on connected device via ADB:**
   ```bash
   ./gradlew installDebug
   ```

---

## Chrome Extension

The local-first Chrome companion lives in [`extension/`](extension/). It is self-contained and can be loaded directly through `chrome://extensions` using **Load unpacked**—no build step is required.

The extension tracks focused domain-level activity, excludes idle and unfocused time, supports strict and soft website limits, and stores precise backend-ready intervals locally. It does not collect full URLs, page contents, form data, or search terms.

The Android and Chrome clients share the activity model documented in [`shared/data-contract-v1.md`](shared/data-contract-v1.md). Neither client is connected to a backend yet.

---

## License

This project is licensed under the **Apache License, Version 2.0**.

```
Copyright 2026 Ayush Kant

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
