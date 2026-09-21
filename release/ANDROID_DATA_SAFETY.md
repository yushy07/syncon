# Google Play Data safety answers

Use this as the source of truth when completing the Play Console form.

## Data collection and sharing

- Data is encrypted in transit: **Yes**.
- Users can request deletion: **Yes**, directly in Android under Connected devices → Delete SyncOn account.
- Data is sold: **No**.
- Data is used for advertising: **No**.
- Data is shared with third parties for their independent purposes: **No**.

## Collected data when optional sync is enabled

| Play category | Data | Purpose | Required? |
|---|---|---|---|
| Personal info | Email address, account/user identifier | Account management | Optional; only for sync |
| App activity | Installed/used app identifiers, interaction timestamps and durations | Core analytics and limit enforcement | Optional cloud collection; local tracking is core |
| Web browsing | Normalized Chrome domain and focused duration | Cross-platform analytics and website limits | Optional; extension only |
| App info and performance | Client version, platform, last sync, conflict/error metadata | Device management and reliability | Optional; sync only |
| Device or other identifiers | Random SyncOn installation ID | Pairing, deduplication, revocation | Optional; sync only |

Passwords are sent directly to Supabase Auth over encrypted transport and are not stored in SyncOn's app database. Pairing QR secrets are not retained in plaintext by the backend.

## Processing notes

- Local data is processed on-device for screen-time reports and blocking.
- Cloud data is account-scoped and used only for user-facing SyncOn functions.
- Account deletion removes the cloud account and cascades synchronized records.
- Local history remains under the user's control on each installation.
