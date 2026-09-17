# Scheduler and clocks — generated

Generated 2026-09-17T01:41:42-10:00. Do not edit by hand.

Ops desk: [INDEX.md](INDEX.md) · runners under `desk/`.

## Scheduler jobs (HST)

- `heartbeat` — CF heartbeat writer — interval seconds=60
- `rr-noaa` — NOAA weather — interval minutes=60 → `noaa.run`
- `radar-archive` — NWS Hawaii radar archive — interval minutes=10 → `radar_archive.run`
- `official-weather-media` — Official NHC/NWS media — interval minutes=10 → `official_weather_media.run`
- `nws-hawaii-counties` — NWS Hawaii by county — cron minute="7,22,37,52" → `nws_hawaii.run`
- `rr-kilauea` — Kīlauea — interval minutes=60 → `kilauea.run`
- `time-chime` — Time chime (:00/:30) — cron minute="0,30" → `hourly_chime.run`
- `remaining-tasks` — Remaining tasks (:32, 1h + failed due) — cron minute=32 → `remaining_tasks.run`
- `morning-boot-replay` — Morning boot MP3 replay (:32 until noon) — cron minute=32 → `morning_boot_replay.run`
- `hourly-clip-prebuild` — Prebuild hourly clip reports — cron minute=55
- `hourly-clip-reports` — Play hourly clip reports — cron minute=2 → `hourly_clip_reports.run`
- `earthquake-hourly` — Earthquake hourly local WAV — cron minute=8 → `earthquake_hourly.run`
- `earthquake-m2-poll` — Earthquake local M≥2 poll — interval minutes=10
- `council-quake` — Ava USGS quake Telegram — interval minutes=2 → `council_quake.run`
- `council-bruce-stats` — Bruce measured desk sample — cron hour="7,15,21", minute=18 → `council_bruce_stats.run`
- `hourly-solar-weather` — Hourly solar+weather — cron minute=4 → `solar_weather.run`
- `solar-notes-quarter-hour` — Solar Notes EcoFlow status (every 30 minutes) — interval minutes=30
- `hybrid-charge-status` — Hybrid charge status (every 30 minutes) — interval minutes=30
- `system-performance` — System performance — cron minute=6 → `system_perf.run`
- `player-economy-report` — Player economy — interval minutes=60 → `player_economy.run`
- `morning-report` — Morning report — cron hour=9, minute=0 → `morning_report.run`
- `report-readiness` — Report readiness poll — cron minute="*/5" → `report_readiness.run`
- `report-periodic-audio` — Report audio periodic replay — cron minute="*/5" → `report_periodic_audio.run`
- `morning-report-play` — Morning report play — cron hour=9, minute=5 → `morning_report_play.run`
- `day-reports-morning` — Morning slot reports — cron hour=9, minute=10 → `day_reports_morning.run`
- `midday-report` — Midday status (12:00) — cron hour=12, minute=0 → `midday_report.run`
- `midday-report-play` — Midday report play — cron hour=12, minute=5 → `midday_report_play.run`
- `day-reports-midday` — Midday slot reports — cron hour=13, minute=0 → `day_reports_midday.run`
- `daily-reports-catchup` — Daily reports catch-up — cron hour=14, minute=0 → `daily_reports_catchup.run`
- `day-reports-evening` — Evening slot reports — cron hour=18, minute=0 → `day_reports_evening.run`
- `late-report` — Late report (21:00) — cron hour=21, minute=0 → `late_report.run`
- `late-report-play` — Late report play — cron hour=21, minute=8 → `late_report_play.run`
- `late-final-report` — Final report (23:30) — cron hour=23, minute=30 → `late_report.run`
- `merged-morning-summary` — Merged morning summary — cron hour=10, minute=20 → `merged_morning.run`
- `cursor-fallback` — Cursor report fallback — cron hour="10,16", minute=22 → `cursor_fallback.run`
- `governance-daily` — RootRecord governance daily — cron hour=10, minute=23 → `governance_daily.run`
- `api-prices` — Public API price catalog — cron hour=10, minute=25 → `api_prices.run`
- `code-review` — Write review pack (no apply) — cron hour="11,17", minute=20 → `code_review.run`
- `governance-self-update` — Governance self-update after boot grace — interval 
                      hours=1,
                      start_date=datetime.now( → `governance_self_update.run`
- `economy-brief` — Economy brief — cron hour=15, minute=0 → `economy_brief.run`
- `adsense-eod` — AdSense end-of-day report — cron hour=21, minute=0
- `admob-eod` — AdMob end-of-day report — cron hour=21, minute=5
- `overnight-relay` — Late-night relay — cron hour=22, minute=20 → `overnight.run`
- `minecraft-live` — Minecraft in-game detect — interval seconds=45 → `minecraft_live.run`
- `hurricane-fetch` — Hurricane fetch NHC/RAMMB/JTWC — cron hour="5,9,12,16,20", minute=40 → `hurricane_fetch.run`
- `hurricane-desk` — Hurricane desk text + WAV — cron hour="5,9,12,20", minute=50 → `hurricane_desk.run`
- `hurricane-desk-evening` — Hurricane desk evening build — cron hour=16, minute=55 → `hurricane_desk.run`
- `hurricane-radio-am` — Hurricane desk on radio (06:35) — cron hour=6, minute=35 → `hurricane_radio.run`
- `hurricane-radio-mid` — Hurricane desk on radio (13:12) — cron hour=13, minute=12 → `hurricane_radio.run`
- `hurricane-radio-pm` — Hurricane desk on radio (17:02) — cron hour=17, minute=2 → `hurricane_radio.run`
- `ecoflow-quota` — EcoFlow quota refresh — interval minutes=2 → `ecoflow_quota.run`
- `drive-automation` — River car DC drive session — interval minutes=30 → `drive_automation.run`
- `fs-index` — Live directory index — interval minutes=15
- `host-sample` — Host CPU/RAM sample — interval minutes=1
- `log-cleanup` — Delete log files older than 7 days — cron hour=4, minute=20 → `log_cleanup.run`
- `user-qrcodes` — User QR backfill — interval hours=6 → `user_qrcodes.run`
- `account-import` — Identity + membership import — interval hours=6 → `account_import.run`
- `d1-sync` — MySQL → D1 Minecraft cache — interval hours=6 → `d1_sync.run`
- `inbox-drain` — CF offline inbox → local — interval minutes=5 → `inbox_drain.run`
- `stripe-poll` — Stripe finance snapshot — interval minutes=30 → `stripe_poll.run`
- `ltc-pending` — unMineable LTC pending withdraw — interval minutes=30 → `ltc_pending.run`
- `vercel-builds` — Vercel build logs → docs — interval minutes=5 → `vercel_builds.run`

## Source files + top-level functions

- `apps/core/scheduler.py` — missing
- `apps/core/crons` — missing
- `apps/core/services/day_board.py` — missing
- `apps/core/services/schedule_clock.py` — missing

