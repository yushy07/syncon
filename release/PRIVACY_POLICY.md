# SyncOn Privacy Policy

Effective date: September 21, 2026

SyncOn is a local-first Android application with an Android-connected Chrome extension for screen-time tracking and limit enforcement. Android works without the extension or an internet connection. Chrome requires one successful Android QR pairing before activation; after that, temporary internet outages do not stop local tracking or blocking.

## Data SyncOn processes

- Android application identifiers, app display names, categories, focused-use intervals, limits, warning/block events, and device installation identifiers.
- Chrome domain names, categories, focused-use intervals, limits, warning/block events, and extension installation identifiers.
- Account email address and authentication identifiers when the user creates a SyncOn account.
- Connected-device names, client versions, last-sync times, sync cursors, and user-created app/domain mappings.

SyncOn does not collect full browsing URLs, page contents, search queries, form entries, keystrokes, contacts, precise location, advertising identifiers, payment information, or data for advertising.

## Why the data is used

The data is used only to calculate screen time, show Android/Chrome/combined reports, enforce user-configured limits, pair devices, synchronize user settings and history, prevent pairing abuse, and diagnose synchronization failures.

## Local storage and connected sync

Android data is written to the app's local Room database. Once activated by Android, Chrome data is written to extension-owned IndexedDB and Chrome storage. When the user signs in and pairs a browser, selected activity and settings are synchronized to a Supabase project over encrypted HTTPS/WebSocket connections. Each cloud row is restricted to the user's account through Row Level Security.

## Sharing and sale

SyncOn does not sell personal data and does not use it for advertising or cross-site tracking. Supabase acts as the infrastructure provider for authentication, database storage, scheduled cleanup, and Realtime delivery. Google Play Services provides the permission-light QR scanner on Android. These providers process data only to supply those functions under their own terms.

## Retention and deletion

Local usage history is retained for up to three years unless the user clears it sooner. Expired pairing requests are deleted automatically. Abandoned anonymous pairing identities are pruned after the configured retention period. Users can disconnect a browser, clear local Chrome history, export local data, sign out, or permanently delete the SyncOn cloud account from Android's Connected devices screen. Cloud account deletion does not silently erase separate local history on already-installed clients.

## Security

Sessions are stored in platform-owned encrypted/extension storage. Pairing QR codes expire, are single-use, and contain no password or access token. Pairing secrets are stored by the backend only as hashes. Sync uses per-account authorization, conflict detection, rate limits, audit events, private Realtime topics, and encrypted transport.

## Permissions

Android uses Usage Access to read foreground application events, Accessibility to enforce limits, notifications for tracking/warnings, boot/background capabilities for continuity, internet for optional sync, and camera-free Google Code Scanner UI for QR pairing. Chrome permissions are explained in `release/CHROME_PERMISSION_JUSTIFICATIONS.md`.

## Children

SyncOn is a general digital-wellbeing utility and is not directed to children under 13. It should not be used to monitor another person without their knowledge and authorization.

## Contact

For privacy or support requests, open an issue at https://github.com/yushy07/syncon/issues. Do not include passwords, QR secrets, access tokens, or private usage exports in an issue.

## Policy changes

Material changes will be published in the project repository and reflected by an updated effective date.
