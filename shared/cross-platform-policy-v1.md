# SyncOn Cross-Platform Policy v1

## Time metrics

SyncOn will expose two different totals instead of hiding simultaneous-device behavior:

1. **Summed device time** adds every Android and Chrome interval. One hour on both devices simultaneously equals two device-hours.
2. **Active digital span** merges overlapping intervals before summing. The same simultaneous hour equals one elapsed hour.

Neither metric replaces the other. Product copy must label them clearly.

## Source mapping

Android packages and Chrome domains remain immutable source identities. `service-mappings-v1.json` optionally associates them with a `logicalServiceId`. Mapping is enrichment, not destructive rewriting; unknown sources remain valid and independent.

Manual user mappings override the bundled registry and require their own revision metadata.

## Limit targets

The shared settings model supports four explicit targets:

- `SOURCE`: one Android package or Chrome domain.
- `CATEGORY`: all sources in a category, optionally filtered by platform.
- `LOGICAL_SERVICE`: mapped sources such as the YouTube app plus `youtube.com`.
- `ACCOUNT`: all tracked activity across selected platforms.

Android enforces Android-local source/category limits and Chrome enforces Chrome-local source/category limits. Synchronized settings and logical-service views are shared, but neither offline client pretends to know live activity that has not yet arrived from the other device. `ACCOUNT`-wide enforcement remains outside the current client behavior.

## Conflict policy

- Usage intervals are append-only and deduplicated by `recordId`.
- Manual categories override automatic categories.
- Settings use stable IDs, monotonic local revisions, server revisions, update timestamps, sync state, and deletion tombstones.
- A deletion remains a tombstone until all connected devices acknowledge it.
- A newer valid settings revision wins; usage data never uses last-write-wins replacement.

## Contract status

The activity and settings contracts are implemented as version 1. Future incompatible changes require a new version and explicit migration. Real-device Android/Chrome verification remains separate from automated checks.
