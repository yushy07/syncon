# SyncOn Chrome Extension

The Android-connected Chrome companion for SyncOn. It records only focused domain-level activity and does not read page contents, form data, search queries, or full URL paths.

## Load in Chrome

1. Copy `manifest.example.json` to `manifest.json` and replace the placeholder backend host.
2. Copy `lib/backend-config.example.js` to `lib/backend-config.js` and add the local publishable client configuration.
3. Open `chrome://extensions`.
4. Enable **Developer mode**.
5. Select **Load unpacked**.
6. Choose this `extension` folder.
7. Pin SyncOn from the Chrome toolbar.

The real manifest and backend config are ignored by Git so the private project binding is not exposed on GitHub.

The extension works without a build step. On first install it opens a pairing page; scan its one-time QR code from the Android app to activate the extension and begin syncing. Unlike the Android app, a fresh extension does not track or expose its dashboard before this first pairing.

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
- The same warm, calm visual system used by the Android app

## Pairing and privacy

After activation, tracking remains local-first in IndexedDB and `chrome.storage.local`, including during temporary network outages. A fresh install creates an anonymous Supabase identity only for secure pairing, and Android grants only that browser installation access to the user's SyncOn account. The QR contains a short-lived request ID and one-time secret—not credentials or activity data.

Revoking the browser from Android disables extension tracking and returns the extension to the pairing flow.

## Developer checks

Run `npm test` and `npm run check` from this folder. No package installation is required.
