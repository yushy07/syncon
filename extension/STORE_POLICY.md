# Archived Chrome permission and privacy notes

SyncOn is currently loaded privately as an unpacked extension. This file records why each permission exists and is retained as a future distribution reference.

- `tabs`: reads the URL of the active tab to derive a normalized domain. Full paths and query strings are not stored.
- `idle`: excludes computer-idle time from screen-time totals.
- `alarms`: performs reliable Manifest V3 checkpoints, cleanup, warnings, and limit checks.
- `storage`: stores settings, daily summaries, categories, limits, and small state locally.
- `unlimitedStorage`: supports the user's three-year local-history requirement. Precise intervals live in IndexedDB.
- `notifications`: provides the five-minute remaining-time warning requested by the user.
- Supabase host access: authenticates the browser installation, performs account-scoped sync RPCs, and receives private Realtime wake signals. No other website host permission is requested.

Incognito uses split mode. It is disabled unless the user explicitly enables **Allow in Incognito** in Chrome, and its data remains separate from the normal profile.

The reusable listing copy, privacy policy, screenshot list, and packaging instructions live in `release/`. No remote code or analytics may be introduced.
