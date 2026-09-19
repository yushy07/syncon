# SyncOn Shared Activity Contract v1

This contract is implemented locally by the Android app and Chrome extension. Its Supabase representation and synchronization RPCs are versioned under `supabase/`.

## Activity interval

| Field | Meaning |
|---|---|
| `recordId` | Stable client-generated identifier used for idempotent synchronization |
| `installationId` | Random installation identifier; never derived from hardware identifiers |
| `sourcePlatform` | `ANDROID` or `CHROME` |
| `sourceType` | `ANDROID_APP` or `CHROME_DOMAIN` |
| `sourceIdentifier` | Android package name or normalized Chrome domain |
| `usageDate` | Device-assigned usage day using the 4:00 AM boundary |
| `startTimeUtc` | Inclusive interval start in Unix epoch milliseconds |
| `endTimeUtc` | Exclusive interval end in Unix epoch milliseconds |
| `durationMillis` | Precise active duration |
| `timezoneId` | IANA timezone used while recording |
| `utcOffsetMinutes` | Local offset at interval start |
| `createdAtUtc` | Record creation time |
| `updatedAtUtc` | Last local modification time |
| `localRevision` | Monotonic client revision |
| `serverRevision` | Future backend revision; currently null |
| `syncState` | Currently `LOCAL_ONLY`; future values include `PENDING_UPLOAD`, `SYNCED`, `SYNC_FAILED`, and `PENDING_DELETE` |
| `isDeleted` | Tombstone marker for future synchronized deletion |

## Rules

- Clients save locally before any future upload.
- Repeating an upload with the same `recordId` must not duplicate usage.
- Activity intervals are append-only except for explicit correction or deletion revisions.
- Android package names and Chrome domains remain separate source identities.
- A future mapping layer may connect sources such as the YouTube Android app and `youtube.com` to one logical service.
- The backend must preserve the device-assigned `usageDate`, timezone, and reset boundary.
- Account dashboards should expose both summed device time and overlap-adjusted human time.
- Manual categories override automatic categories.
- The newest valid settings revision wins; usage intervals must not use last-write-wins replacement.
- Logical service mappings are maintained separately in `service-mappings-v1.json`; raw source identities are never overwritten.
- Cross-platform totals follow `cross-platform-policy-v1.md` and expose both summed device time and overlap-adjusted active span.
- Backend JSON uses snake_case field names while the existing clients keep their native camelCase/Kotlin names; sync adapters perform the explicit mapping.
- Every synchronized row is owned by `auth.uid()` and protected by Row Level Security.

## Privacy boundary

Chrome records normalized domains only. It does not record URL paths, query strings, page contents, form entries, or search terms. Android records package identifiers and system-provided foreground timing only.
