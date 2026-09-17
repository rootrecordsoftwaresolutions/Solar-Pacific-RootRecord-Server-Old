# Ecosystem index — generated

Generated 2026-09-17T01:41:42-10:00.

- Scheduler jobs parsed: **62**
- Topic skills: **34**

## All jobs

- `heartbeat` — interval seconds=60 — CF heartbeat writer
- `rr-noaa` — interval minutes=60 — NOAA weather
- `radar-archive` — interval minutes=10 — NWS Hawaii radar archive
- `official-weather-media` — interval minutes=10 — Official NHC/NWS media
- `nws-hawaii-counties` — cron minute="7,22,37,52" — NWS Hawaii by county
- `rr-kilauea` — interval minutes=60 — Kīlauea
- `time-chime` — cron minute="0,30" — Time chime (:00/:30)
- `remaining-tasks` — cron minute=32 — Remaining tasks (:32, 1h + failed due)
- `morning-boot-replay` — cron minute=32 — Morning boot MP3 replay (:32 until noon)
- `hourly-clip-prebuild` — cron minute=55 — Prebuild hourly clip reports
- `hourly-clip-reports` — cron minute=2 — Play hourly clip reports
- `earthquake-hourly` — cron minute=8 — Earthquake hourly local WAV
- `earthquake-m2-poll` — interval minutes=10 — Earthquake local M≥2 poll
- `council-quake` — interval minutes=2 — Ava USGS quake Telegram
- `council-bruce-stats` — cron hour="7,15,21", minute=18 — Bruce measured desk sample
- `hourly-solar-weather` — cron minute=4 — Hourly solar+weather
- `solar-notes-quarter-hour` — interval minutes=30 — Solar Notes EcoFlow status (every 30 minutes)
- `hybrid-charge-status` — interval minutes=30 — Hybrid charge status (every 30 minutes)
- `system-performance` — cron minute=6 — System performance
- `player-economy-report` — interval minutes=60 — Player economy
- `morning-report` — cron hour=9, minute=0 — Morning report
- `report-readiness` — cron minute="*/5" — Report readiness poll
- `report-periodic-audio` — cron minute="*/5" — Report audio periodic replay
- `morning-report-play` — cron hour=9, minute=5 — Morning report play
- `day-reports-morning` — cron hour=9, minute=10 — Morning slot reports
- `midday-report` — cron hour=12, minute=0 — Midday status (12:00)
- `midday-report-play` — cron hour=12, minute=5 — Midday report play
- `day-reports-midday` — cron hour=13, minute=0 — Midday slot reports
- `daily-reports-catchup` — cron hour=14, minute=0 — Daily reports catch-up
- `day-reports-evening` — cron hour=18, minute=0 — Evening slot reports
- `late-report` — cron hour=21, minute=0 — Late report (21:00)
- `late-report-play` — cron hour=21, minute=8 — Late report play
- `late-final-report` — cron hour=23, minute=30 — Final report (23:30)
- `merged-morning-summary` — cron hour=10, minute=20 — Merged morning summary
- `cursor-fallback` — cron hour="10,16", minute=22 — Cursor report fallback
- `governance-daily` — cron hour=10, minute=23 — RootRecord governance daily
- `api-prices` — cron hour=10, minute=25 — Public API price catalog
- `code-review` — cron hour="11,17", minute=20 — Write review pack (no apply)
- `governance-self-update` — interval 
                      hours=1,
                      start_date=datetime.now( — Governance self-update after boot grace
- `economy-brief` — cron hour=15, minute=0 — Economy brief
- `adsense-eod` — cron hour=21, minute=0 — AdSense end-of-day report
- `admob-eod` — cron hour=21, minute=5 — AdMob end-of-day report
- `overnight-relay` — cron hour=22, minute=20 — Late-night relay
- `minecraft-live` — interval seconds=45 — Minecraft in-game detect
- `hurricane-fetch` — cron hour="5,9,12,16,20", minute=40 — Hurricane fetch NHC/RAMMB/JTWC
- `hurricane-desk` — cron hour="5,9,12,20", minute=50 — Hurricane desk text + WAV
- `hurricane-desk-evening` — cron hour=16, minute=55 — Hurricane desk evening build
- `hurricane-radio-am` — cron hour=6, minute=35 — Hurricane desk on radio (06:35)
- `hurricane-radio-mid` — cron hour=13, minute=12 — Hurricane desk on radio (13:12)
- `hurricane-radio-pm` — cron hour=17, minute=2 — Hurricane desk on radio (17:02)
- `ecoflow-quota` — interval minutes=2 — EcoFlow quota refresh
- `drive-automation` — interval minutes=30 — River car DC drive session
- `fs-index` — interval minutes=15 — Live directory index
- `host-sample` — interval minutes=1 — Host CPU/RAM sample
- `log-cleanup` — cron hour=4, minute=20 — Delete log files older than 7 days
- `user-qrcodes` — interval hours=6 — User QR backfill
- `account-import` — interval hours=6 — Identity + membership import
- `d1-sync` — interval hours=6 — MySQL → D1 Minecraft cache
- `inbox-drain` — interval minutes=5 — CF offline inbox → local
- `stripe-poll` — interval minutes=30 — Stripe finance snapshot
- `ltc-pending` — interval minutes=30 — unMineable LTC pending withdraw
- `vercel-builds` — interval minutes=5 — Vercel build logs → docs

