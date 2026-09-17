# Discord autonomy on (powered-on Root Server) — 2026-08-02

## Root cause
Status `Agents 0/3` while `live · hot` looked broken, but Discord was **hard-locked to dream-only** in `recommend.mjs` (`discordDream = surface === discord`). Cursor slots never engaged for Discord asks. Pipeline also redirected digs → Slack and muted Discord under cloud-dark even with Cursor up.

## Fix
- Powered on + awake + `CURSOR_API_KEY` → Discord uses Root Server (local/Cursor agents up to concurrency).
- Dream reserved for sleep / `forceDream` / `AVA_FORCE_DREAM` / missing Cursor key.
- Slack dig redirect only while asleep / no cursor.
- Status label: **Cursor digs** `0/3 · idle` (idle ≠ broken).
- Hush / QUIET / sleep / power-down unchanged.

## Verify
`/api/status`: live·hot, cursor yes, digs idle until a Discord ask opens → asks/digs bump.
