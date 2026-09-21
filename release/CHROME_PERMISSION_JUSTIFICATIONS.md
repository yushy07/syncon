# Chrome Web Store permission justifications

## Single purpose

SyncOn tracks time spent on the focused website, shows digital-wellbeing reports, and enforces limits chosen by the user. First-run QR pairing with the Android app is required to activate the extension and synchronize reports and limits.

## Permissions

- `tabs`: reads the active tab URL long enough to normalize its hostname. Paths, query parameters, page contents, and form data are not retained.
- `idle`: excludes time when the computer is idle.
- `alarms`: checkpoints active sessions, enforces limits, retries local-first sync, and performs retention cleanup under Manifest V3.
- `storage`: stores preferences, summaries, pairing/session state, and connection status.
- `unlimitedStorage`: retains up to three years of user-owned precise intervals in IndexedDB without quota-related loss.
- `notifications`: warns when a selected website or category budget is nearly exhausted.
- The exact local Supabase project host: authenticates the installation and calls only SyncOn's account-scoped pairing/sync endpoints. It also establishes the matching private Realtime connection. The live hostname is kept in the ignored local manifest; no arbitrary website host access is requested.

## Remote code

The extension executes no remotely hosted JavaScript or WebAssembly. QR generation, tracking, blocking, sync transport, and Realtime protocol code are packaged inside the submitted extension.

## Incognito

The manifest uses split mode. Incognito access is off until the user explicitly enables it in Chrome. Chrome keeps its extension storage/context separate from the normal profile.
