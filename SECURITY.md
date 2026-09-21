# Security policy

SyncOn handles private usage history, authentication sessions and device-pairing state. Security reports are welcome even though the project is maintained primarily for personal use.

## Reporting

Use GitHub's private **Report a vulnerability** flow for this repository when available. Do not place passwords, access tokens, service-role keys, live QR payloads, private usage exports or personal email addresses in a public issue.

For non-sensitive bugs, use the normal GitHub issue tracker.

## Supported code

Only the latest commit on `main` is actively maintained.

## Client security boundary

- Android and Chrome use only the Supabase publishable client key, supplied from ignored local configuration rather than tracked source.
- Supabase service-role credentials must remain outside both clients and Git history.
- Every synchronized row must remain account-scoped through Row Level Security.
- Pairing requests must remain short-lived and single-use, with backend verification authoritative.
- Chrome must not store full URLs, page contents, form data, searches or keystrokes.
