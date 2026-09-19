# SyncOn Chrome Extension

Local-first Chrome screen-time tracking for the SyncOn project. The extension records only focused domain-level activity. It does not read page contents, form data, search queries, or full URL paths.

## Load in Chrome

1. Open `chrome://extensions`.
2. Enable **Developer mode**.
3. Select **Load unpacked**.
4. Choose this `extension` folder.
5. Pin SyncOn from the Chrome toolbar.

The extension works without a build step and without a backend.

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
- Cross-platform-ready timestamps and local sync metadata
- IndexedDB interval storage with Manifest V3 worker-session recovery

## Privacy

All data is stored with `chrome.storage.local`. No network request or backend connection is included in this phase.

## Developer checks

Run `npm test` and `npm run check` from this folder. No package installation is required.
