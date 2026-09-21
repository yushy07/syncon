# SyncOn Supabase backend

This directory is the versioned source of truth for SyncOn pairing and cross-platform synchronization.

## Project

- Project reference: `nqpristylxavyexqgjtp`
- Shared client contract: version 1
- Applied migration series: `202609190001` through `202609200005`
- Detailed callable interface: [`API_CONTRACT.md`](API_CONTRACT.md)

## Current capabilities

- Supabase Auth identities and account membership
- Registered Android and Chrome installations
- Short-lived, single-use Android-to-Chrome QR pairing
- Account-scoped Row Level Security
- Idempotent activity intervals with stable client record IDs
- Incremental cursor pulls using monotonic server revisions
- Per-record acknowledgements and explicit setting conflicts
- Synchronized sources, limits, block events and logical-service mappings
- Summed device time and overlap-adjusted active digital span
- Private account Realtime broadcast topics
- Pairing rate limits, attempt locks and security audit events
- Scheduled deletion of expired pairing/anonymous records
- Device revocation and owner account deletion

## CLI workflow

```powershell
supabase login
supabase link --project-ref nqpristylxavyexqgjtp
supabase migration list
supabase db lint --linked
supabase db push
```

Review every pending migration before `db push`. Do not create destructive repair migrations casually against the personal live project.

## Security boundary

Clients use the project URL, a publishable client key and authenticated user sessions. Never place a service-role key in Android, the extension, Git history, logs or QR payloads.

Local tracking and blocking remain client responsibilities. Supabase provides pairing, synchronization, authorization, cleanup and Realtime wake signals; network loss must not disable an already-paired client's local enforcement.
