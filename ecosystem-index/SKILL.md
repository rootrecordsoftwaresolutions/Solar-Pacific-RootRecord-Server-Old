---
name: ecosystem-index
description: >-
  Index of Ava-Core / RootRecord topic skills. Use when explaining how the
  ecosystem works, which skill to open, or after changing crons, launch, council,
  weather, RootMC, or Ava Ops. Run refresh-all.py after those edits.
---

# Ecosystem index

Carla. Pick a topic skill. Do not load all CURRENT.md files at once.

## Refresh after edits

```bash
python3 .cursor/skills/ecosystem-index/scripts/refresh-all.py
```

That rebuilds every topic CURRENT.md, EcoFlow CURRENT.md, desk/ symlinks, Ollama home stubs, and the history file list.
Canonical tree: `~/.ollama/skills`. Each topic skill folder is the ops desk (`INDEX.md`, `desk/src`). If host/power/public-door story changed, edit `ecosystem-history/NARRATIVE.md` in the same turn.

## Topics

| Skill | Open when |
| --- | --- |
| `scheduler-clock` | When does X fire? |
| `boot-idle-origin` | Launch, idle-stop, :8787, toggles |
| `launch` | AVA Console boot (function desk) |
| `idle-stop` | Desk idle (function desk) |
| `log-cleanup` | Stale log files (function desk) |
| `code-review` | Review pack only (function desk) |
| `boot-prelims` | Boot NOAA/NWS/Kīlauea (function desk) |
| `day-board-boot` | Origin start day-board (function desk) |
| `recycle-origin` | Recycle :8787 only (function desk) |
| `ollama-env` | Ollama GGUF env (function desk) |
| `companions` | Optional companions (function desk) |
| `ensure-ava-runtime` | Origin/Ollama health (function desk) |
| `ollama-lifecycle` | Ollama idle/touch/clips (function desk) |
| `ollama-client` | Local Ollama HTTP client (function desk) |
| `uptime-log` | Host uptime log (function desk) |
| `feature-toggles` | Ava Ops stack flags (function desk) |
| `python-drop-runner` | Drop-in Python terminals (function desk) |
| `xmrig` | CPU RandomX miner start/stop (function desk) |
| `net-gate` | Desk network-gate state (function desk) |
| `obs-studio` | OBS WebSocket + overlays (function desk) |
| `origin-session` | Origin operator session (function desk) |
| `ops-banner` | Ava Ops status banner (function desk) |
| `live-data-pages` | Origin live-data builders (function desk) |
| `radio` | Radio on-air/catalog/encode (function desk) |
| `discord` | Discord helpers (function desk) |
| `slack` | Slack helpers (function desk) |
| `telegram` | Telegram helpers (function desk) |
| `goals` | Goals catalog (function desk) |
| `persona` | Persona helpers (function desk) |
| `look` | Look/style helpers (function desk) |
| `bible-prayers` | Bible study, verses, prayers |
| `jesus` | Jesus The Christ Telegram agent |
| `history` | American and Hawaiian history |
| `cooking` | Recipes, user-submitted dishes, cook from pantry |
| `nutrition` | Nutrient-dense food database |
| `pantry` | Food stock on the shelf |
| `gardening` | USDA zones, polyculture, Hawaiʻi planting |
| `root-record-registry` | Root Record Registry (UH/OA papers) |
| `research-oa` | Stub → root-record-registry |
| `reports-voice` | Morning/midday/late, clips, chimes |
| `morning-report` | 09:00 generate (function desk) |
| `morning-report-play` | 09:05 play (function desk) |
| `merged-morning` | 10:20 summary queue (function desk) |
| `midday-report` | 12:00 generate (function desk) |
| `midday-report-play` | 12:05 play (function desk) |
| `late-report` | 21:00 / 23:30 generate (function desk) |
| `late-report-play` | 21:08 play (function desk) |
| `hourly-chime` | :00/:30 time chime (function desk) |
| `hourly-clip-reports` | Hourly clip packs (function desk) |
| `remaining-tasks` | Remaining-tasks desk (function desk) |
| `morning-boot-replay` | Morning MP3 replay (function desk) |
| `report-readiness` | Early generate when facts ready (function desk) |
| `report-periodic-audio` | Periodic replay wrapper (function desk) |
| `day-reports-morning` | Extra morning kinds (function desk) |
| `day-reports-midday` | Extra midday kinds (function desk) |
| `day-reports-evening` | Extra evening kinds (function desk) |
| `day-reports` | Slot report processor (function desk) |
| `overnight-relay` | Late-night snapshot (function desk) |
| `daily-reports-catchup` | 14:00 catch-up (function desk) |
| `cursor-fallback` | Cursor fallback drain (function desk) |
| `evening-report` | Evening leftover skip (function desk) |
| `evening-report-play` | Evening play leftover (function desk) |
| `evening-report-audio` | Evening audio leftover (function desk) |
| `weather-kilauea` | NWS, volcano, quakes, storms |
| `rr-kilauea` | Hourly Kīlauea poll (function desk) |
| `rr-noaa` | Hourly NWS forecast (function desk) |
| `nws-hawaii` | County hazard poll (function desk) |
| `earthquake-hourly` | Hourly EQ + M2 poll (function desk) |
| `radar-archive` | NWS radar loop archive (function desk) |
| `official-weather-media` | NHC/NWS graphics (function desk) |
| `hurricane-tracker` | Storm fetch/state (function desk) |
| `hurricane-desk` | Desk text/WAV (function desk) |
| `hurricane-fetch` | Tropical board fetch (function desk) |
| `hurricane-radio` | Storm radio play (function desk) |
| `hurricane-obs` | Storm OBS slides (function desk) |
| `nhc-media` | Folded no-op (function desk) |
| `kilauea-cams` | OBS Kīlauea embeds (function desk) |
| `live-wx` | Live weather facts (function desk) |
| `geography` | Hawaiʻi geography helpers (function desk) |
| `council-quake` | Telegram USGS posts (function desk) |
| `ecoflow-automations` | Packs, Starlink midnight, 400 W gate |
| `ecoflow-quota` | Quota cron (function desk) |
| `ecoflow-ble-poller` | BLE poller (function desk) |
| `ecoflow-ac-solar-gate` | Delta USB-C PV gate (function desk) |
| `ecoflow-river-car` | River car 12V for drives (function desk) |
| `hybrid-night-poller` | Hybrid 30 min inserts (function desk) |
| `hybrid-reports` | Hybrid stamp writer (function desk) |
| `sunrise-restore` | Sunrise restore (function desk) |
| `report-generation` | Shared report engine (function desk) |
| `reports` | Public draft queue (function desk) |
| `daily-report-board` | Daily board writer (function desk) |
| `startup-voice` | Startup spoken line (function desk) |
| `report-audio-manual` | Manual report MP3 slots (function desk) |
| `report-blog` | Report posts + blog sync (function desk) |
| `media-library` | Convert library audio to WAV (function desk) |
| `youtube-download` | YouTube audio fetch (function desk) |
| `voice-events` | Spoken-voice cooldown stamps (function desk) |
| `synth` | Report TTS routing (function desk) |
| `broadcast` | OBS broadcast helper (function desk) |
| `xai` | Grok / xAI routing (function desk) |
| `model-pick` | Model picker (function desk) |
| `hourly-solar-weather` | Hourly solar + weather (function desk) |
| `load-categories` | Solar load labels (function desk) |
| `council-telegram` | Telegram council, shared poller |
| `ava-ivy` | Ava Ivy Telegram agent |
| `bruce-monitor` | Bruce Monitor Telegram agent |
| `carly-mal` | Carly Mal Telegram agent |
| `council-bruce-stats` | Bruce desk sample (function desk) |
| `governance-daily` | Governance tally (function desk) |
| `governance-self-update` | Self-update drain (function desk) |
| `governance-boot` | Boot governance snapshot (function desk) |
| `ava-ops` | Phone app, Bluetooth, desk |
| `android-sdk` | Command-line SDK (ANDROID_HOME) |
| `android-build` | Linux APK bump/assemble/stage |
| `kilauea-alerts` | Kīlauea Alerts Android app |
| `rootmc-android` | RootMC Android app |
| `rootmc-mobile-web` | RootMC React mobile web |
| `system-perf` | Host snapshot (function desk) |
| `broadcast-loop` | OBS loop rotator (function desk) |
| `rootmc` | Minecraft, Gold, D1 |
| `minecraft-live` | Live detect / OBS (function desk) |
| `player-economy` | Economy snapshot (function desk) |
| `d1-sync` | D1 cache push (function desk) |
| `user-qrcodes` | QR backfill (function desk) |
| `account-import` | Identity import (function desk) |
| `rootmc-economy` | RootMC economy helpers (function desk) |
| `d1` | Cloudflare D1 helper (function desk) |
| `rcon` | Minecraft RCON (function desk) |
| `public-edge` | Cloudflare, public pages |
| `inbox-drain` | Offline inbox pull (function desk) |
| `vercel-builds` | Vercel poll (function desk) |
| `stripe-poll` | Stripe snapshot (function desk) |
| `api-prices` | API price capture (function desk) |
| `api-prices-boot` | Boot API price refresh (function desk) |
| `economy-brief` | Daily economy file (function desk) |
| `adsense-eod` | AdSense snapshot (function desk) |
| `admob-eod` | AdMob snapshot (function desk) |
| `heartbeat` | D1 heartbeat writer (function desk) |
| `finance-desk` | Ops finance ledger (function desk) |
| `public-finance` | Public sanitized finance board (function desk) |
| `subscribers` | Subscriber helpers (function desk) |
| `public-chat` | Public chat helpers (function desk) |
| `site-backgrounds` | Public site backgrounds (function desk) |
| `site-ops` | Public site ops helper (function desk) |
| `avaivy-cloud` | avaivy.cloud Next.js (Vercel) |
| `rootrecord-online` | rootrecord.online Next.js (Vercel) |
| `alexrs94-site` | alexrs94.site Next.js (Vercel) |
| `holding` | rootrecord.cloud holding HTML |
| `clients` | Web-dev gig customers (not memberships) |
| `fern-forest` | Fern Forest off-grid lots |
| `freeltc` | FreeLTC product (in development) |
| `cloudflare-workers` | Cloudflare Workers + wrangler |
| `media-hybrid` | Media tree, hybrid reports |
| `ecosystem-history` | How we got here / old paths |
| `desk-data-reader` | SQLite/MySQL schema, how to query live |
| `live-directories` | Topic index — path map lives in `fs-index` |
| `fs-index` | Incremental `paths.txt` (function desk) |
| `identities` | Identity records (function desk) |
| `people` | People records (function desk) |
| `guests` | Guest records (function desk) |
| `membership` | Membership helpers (function desk; not web-dev clients) |
| `mysql` | MySQL helper (function desk) |
| `db-facts` | SQLite fact reader (function desk) |
| `data-layout` | Data directory layout (function desk) |
| `reply-feedback` | Reply feedback store (function desk) |

## Rules

- Live facts only. No invented watts, SOC, players.
- Night sleep gates Ava jobs. BLE poller keeps running.
- History docs that say OptiPlex or `C:\Users\rootr\ava` are not live.

See [CURRENT.md](CURRENT.md) and [INDEX.md](INDEX.md). Every topic folder is the ops desk (`desk/src`).
