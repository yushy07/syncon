# Chrome Web Store permission and privacy notes

SyncOn is currently intended for unpacked personal use. This file records the justification required before any future Chrome Web Store submission.

- `tabs`: reads the URL of the active tab to derive a normalized domain. Full paths and query strings are not stored.
- `<all_urls>`: required to recognize and enforce user-configured limits on arbitrary websites.
- `idle`: excludes computer-idle time from screen-time totals.
- `alarms`: performs reliable Manifest V3 checkpoints, cleanup, warnings, and limit checks.
- `storage`: stores settings, daily summaries, categories, limits, and small state locally.
- `unlimitedStorage`: supports the user's three-year local-history requirement. Precise intervals live in IndexedDB.
- `notifications`: provides the five-minute remaining-time warning requested by the user.
- `webNavigation`: reserved for reliable website-limit enforcement across navigation events; no page contents are inspected.

Incognito uses split mode. It is disabled unless the user explicitly enables **Allow in Incognito** in Chrome, and its data remains separate from the normal profile.

Before publication, prepare store screenshots, a privacy-policy URL, permission disclosures, support contact information, and a packaged release. No remote code or analytics may be introduced.
