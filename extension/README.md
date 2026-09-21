# SyncOn Chrome Extension

Local-first Chrome screen-time tracking for the SyncOn project. The extension records only focused domain-level activity. It does not read page contents, form data, search queries, or full URL paths.

## Load in Chrome

1. Open `chrome://extensions`.
2. Enable **Developer mode**.
3. Select **Load unpacked**.
4. Choose this `extension` folder.
5. Pin SyncOn from the Chrome toolbar.

The extension works without a build step. On first install it opens a local pairing page; scan its one-time QR code from the Android app to connect Supabase sync.

## Included

- Focused active-tab tracking
- Idle and unfocused-window exclusion
- 4:00 AM usage-day boundary
- Domain categories and manual overrides
- Per-domain strict and soft limits
- Shared category budgets
- Snooze support
- Native remaining-time warnings
- Clean-day streaks
- 7, 14, and 30 day trends
- Versioned JSON export/import
- Three-year local retention
- Stable installation, interval, and settings IDs
- Account-scoped Android + Chrome summaries and logical-service merging
- Overlap-adjusted active digital span
- IndexedDB interval, cursor, conflict, retry, and remote-cache storage
- Private Supabase Realtime wakeups with alarm/manual fallback

## Pairing and privacy

Tracking remains local-first in IndexedDB and `chrome.storage.local`. A fresh install creates an anonymous Supabase identity, and Android grants only that browser installation access to the user's SyncOn account. The QR contains a short-lived request ID and one-time secret—not credentials or activity data.

## Developer checks

Run `npm test` and `npm run check` from this folder. No package installation is required.
