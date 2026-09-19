# SyncOn Supabase backend

This directory is the versioned source of truth for SyncOn's account and cross-platform synchronization backend.

## Project

- Supabase project reference: `nqpristylxavyexqgjtp`
- Contract version: `1`
- Migration: `migrations/202609190001_syncon_sync_v1.sql`

## What migration v1 provides

- Supabase Auth-owned profiles
- Registered Android and Chrome installations
- Idempotent activity intervals keyed by the client `record_id`
- Syncable source metadata, limits, block events, and manual service mappings
- Monotonic server revisions for cursor-based synchronization
- Row Level Security that isolates every account
- `sync_register_installation_v1` for device registration
- `sync_push_intervals_v1` for atomic, duplicate-safe interval uploads
- `sync_push_state_v1` for source metadata, limits, block events, and manual mappings
- `sync_pull_v1` for bounded revision-based downloads
- `account_usage_summary_v1` for summed device time and overlap-adjusted active span
- Supabase Realtime publication for synchronized record changes

## Deployment order

1. Apply the migration to the linked Supabase project.
2. Verify every table has RLS enabled and that unauthenticated requests are rejected.
3. Configure the Android app and Chrome extension with the project URL and publishable key.
4. Add account sign-in and token persistence to both clients.
5. Register each installation, then push local records and pull server revisions.
6. Keep tracking and enforcement local-first; network failure must never stop local recording or blocking.

The migration contains no service-role key and clients must never receive one. Both clients use an authenticated user JWT plus the project's publishable key.
