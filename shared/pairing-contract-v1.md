# SyncOn pairing contract v1

## Payload

`syncon://pair?v=1&id=<request-uuid>&secret=<base64url-secret>`

- `id` is a Supabase pairing request identifier.
- `secret` is 32 random bytes encoded without padding.
- The QR never contains credentials, user information, project keys, or activity data.
- Requests expire after ten minutes and are single-use.

## Identity model

- Android uses a permanent Supabase Auth user and owns one SyncOn account.
- A fresh Chrome extension signs in anonymously and owns only its local installation identity.
- Android claims the pairing request and grants that anonymous identity membership in its SyncOn account.
- The extension keeps its own session; the phone session is never copied.

## Client states

`LOCAL_ONLY`, `PAIRING`, `CONNECTED`, `SYNCING`, `OFFLINE`, `REVOKED`, `ERROR`.

## Validation

Android accepts only the `syncon` scheme, `pair` host, version `1`, a UUID request ID, and a 32-byte base64url secret. Backend validation remains authoritative.
