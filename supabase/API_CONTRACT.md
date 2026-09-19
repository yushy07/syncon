# SyncOn sync API v1

All RPCs require a signed-in Supabase user. Clients send the project publishable key and the user's access token; they never use a service-role key.

## Register an installation

Call `sync_register_installation_v1` before uploading records.

```json
{
  "p_installation_id": "client-generated-stable-id",
  "p_platform": "ANDROID",
  "p_display_name": "My phone",
  "p_client_version": "1.0.0"
}
```

`p_platform` is `ANDROID` or `CHROME`. The result is the installation's latest server revision.

## Push activity

Call `sync_push_intervals_v1` with `p_intervals`, an array using the snake_case form of the shared activity contract.

```json
{
  "p_intervals": [
    {
      "record_id": "stable-record-id",
      "installation_id": "client-generated-stable-id",
      "source_platform": "CHROME",
      "source_type": "CHROME_DOMAIN",
      "source_identifier": "youtube.com",
      "usage_date": "2026-09-19",
      "start_time_utc": "2026-09-19T10:00:00Z",
      "end_time_utc": "2026-09-19T10:05:00Z",
      "duration_millis": 300000,
      "timezone_id": "Asia/Calcutta",
      "utc_offset_minutes": 330,
      "client_created_at": "2026-09-19T10:05:00Z",
      "client_updated_at": "2026-09-19T10:05:00Z",
      "local_revision": 1,
      "is_deleted": false
    }
  ]
}
```

Uploads are idempotent by `(user_id, record_id)`. A correction is accepted only when its local revision is newer, or its matching revision carries a later client update time.

## Push settings and metadata

Call `sync_push_state_v1` with any combination of these arrays:

- `p_sources`: source identity, display name, category, manual-category flag, revision and tombstone.
- `p_limits`: stable record ID, installation, target type/identifier, optional platform, minutes, style, snooze, enabled state, revision and tombstone.
- `p_block_events`: stable record ID, installation, platform/source, date, event type/time, optional category and extra minutes.
- `p_source_mappings`: stable record ID, source identity, logical service ID, revision and tombstone.

Omitted collections default to empty arrays. The call is atomic.

## Pull changes

Call `sync_pull_v1` with the last durable cursor:

```json
{ "p_after_revision": 0, "p_limit": 1000 }
```

Persist `next_revision` only after every returned collection has been committed locally. Continue while `has_more` is true. Tombstones must be applied locally instead of ignored.

## Account totals

Call `account_usage_summary_v1` with inclusive dates. Each row returns:

- `summed_device_millis`: all device intervals added together.
- `active_digital_span_millis`: overlapping intervals merged into elapsed human time.
