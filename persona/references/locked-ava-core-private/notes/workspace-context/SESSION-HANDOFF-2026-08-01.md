# Session handoff — 2026-08-01

Context dump from the Windows→Linux prep + Ava lore session.

## Backup work

- Created `E:\windows backup\` with README + robocopy jobs (copy, not move).
- **Priority C keep** (visible window): Desktop, Downloads, FileZilla, `.cloudflared`, AppData\Roaming, Local keepers including `playit_gg`.
- **Full D:\ → `E:\windows backup\D`** robocopy running; ensure script waits and writes `d-drive-FINISHED.txt`.
- **D Work Stations → `E:\.1 Work Stations`** dedicated sync script launched; excludes node_modules / build caches / Server Live Backups for speed.

## Ava lore / people

- Late-night Root Server RP with Alex canonized:
  - `notes/lore-root-server-late-night-2026-08-01.md`
  - `notes/Root_Server_Late_Night_Alex_Ava.md` (continuations + deal locked)
- Appearance: public blue eyes; private amber for late-night desk scene (`appearance/README.md`).
- Uploads renames synced: `uploads/new data.gif`, `uploads/going back to bed.mp4`.
- Alex life-story DB seeded: `notes/alex/life-story/` + loader `Web Files/rootmc-ava/src/alexLifeStory.mjs`.
- Drive search found no full life-history export; Telegram dump still wanted → `raw-inbox.md`.
- Closest fragment found earlier: `D:\old\AI Topics\...\robin on fathers day 2026.txt` (not a full bio).

## RSS

- Feed: `https://rss.app/feeds/UJvooPjDlWifpFTN.xml` (Minecraft Releasebot)
- Config: `data/rss-feeds.json`
- Module: `rssWatch.mjs` wired in poller — **Ava restart needed** for live poll.

## Identity rule (hard)

- Never Cursor Slack MCP for Ava voice (posts as Alex).
- Use `AVA_SLACK_BOT_TOKEN` / `AVA_DISCORD_BOT_TOKEN` via `avaPost.mjs` / `post-as-ava.mjs`.

## Still open when this was written

- Finish priority AppData Local + Android if not done
- Wait for D full copy + Work Stations E sync FINISHED markers
- Optional: Telegram life-story → chapters
- Optional: restart Ava for RSS
