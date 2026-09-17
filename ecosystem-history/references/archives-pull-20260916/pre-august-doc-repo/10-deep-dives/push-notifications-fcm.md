# Push notifications (FCM)

The primary Worker carries **Firebase Cloud Messaging** credentials in secrets (`FCM_*`). Push routes are gated and tied to user prefs.

## User-facing story

Users toggle notifications in **Account Hub** / Weather prefs. The server stores preference bits; clients register device tokens.

## Engineering story

1. Mobile obtains FCM token from OS.
2. App posts token + prefs endpoints (`handlePushRoutes`, `handlePrefsRoutes`).
3. Broadcast or targeted pushes originate from **trusted** admin routes—never open to anonymous internet.

## Failure modes

- Missing secrets → silent drop in Worker logs.
- OS-level battery restrictions → user blames app—document Android settings.

## Related reading

- [../07-operations/crons-and-background-jobs.md](../07-operations/crons-and-background-jobs.md)
