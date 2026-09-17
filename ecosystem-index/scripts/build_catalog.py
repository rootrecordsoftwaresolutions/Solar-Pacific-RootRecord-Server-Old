#!/usr/bin/env python3
"""Rebuild topic skills + CURRENT.md from live code and local (non-personal) docs."""
from __future__ import annotations

import ast
import json
import os
import re
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

HST = ZoneInfo("Pacific/Honolulu")
HERE = Path(__file__).resolve().parent
import sys

if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))

from rr_home import AVA, HOME, MEDIA, OLLAMA_HOME, OLLAMA_SKILLS, ECOFLOW, OPS, REPORTS, SKILLS  # noqa: E402

DOCS = HOME / "Documents"

PERSONAL_RE = re.compile(
    r"(life-story|profiling|conversation-summaries|wildecho|storeyalexander|"
    r"family-name|ALEXRS94|credentials|\.env|private/|Media\.bak|"
    r"grok_wildecho|grok_storey)",
    re.I,
)

TOPICS: list[dict] = [
    {
        "name": "scheduler-clock",
        "title": "Scheduler and clocks",
        "description": (
            "Maps Ava-Core APScheduler jobs, cronologicals buckets, night-sleep gating, "
            "and HST clocks. Use when asking when something fires, cron waves, remaining "
            "tasks, or day-board."
        ),
        "roots": ["apps/core/scheduler.py", "apps/core/crons", "apps/core/services/day_board.py", "apps/core/services/schedule_clock.py"],
        "job_any": True,
    },
    {
        "name": "boot-idle-origin",
        "title": "Boot, idle, origin",
        "description": (
            "Maps AVA Console launch, idle-stop, origin :8787, Ollama, feature toggles, "
            "and systemd units. Use when asked about boot, desk idle, recycle origin, or "
            "what starts at login."
        ),
        "roots": [
            "scripts/launch.sh",
            "scripts/idle-stop.sh",
            "scripts/recycle-origin.sh",
            "scripts/ensure-ava-runtime.sh",
            "scripts/autostart-launch.sh",
            "scripts/ollama-env.sh",
            "scripts/start-ava-companions.sh",
            "scripts/systemd",
            "tests/test_launch_script_repo_root.py",
            "tests/test_idle_stop.py",
            "tests/test_ops_features.py",
            "tests/test_ops_contract.py",
            "tests/test_ollama_lifecycle.py",
        ],
        "job_ids": ["heartbeat", "log-cleanup"],
        "skill_extra": """
## Function desks already moved

`launch`, `idle-stop`, `log-cleanup`, `logs`, `state`, `database`, `code-review`, `boot-prelims`, `day-board-boot`, `recycle-origin`, `ollama-env`, `companions`, `ensure-ava-runtime`, `ollama-lifecycle`, `ollama-client`, `uptime-log`. Origin FastAPI is the `origin` skill (`ns/apps` import shims).
""",
    },
    {
        "name": "reports-voice",
        "title": "Reports and voice",
        "description": (
            "Maps morning/midday/late reports, clip packs, chimes, OBS/radio toggles, "
            "and spoken audio. Use when asked about reports, voice, chimes, or MP3 play."
        ),
        "roots": [
            "apps/core/services/boot_report.py",
            "apps/core/services/midday_report.py",
            "apps/core/services/reports.py",
            "apps/core/services/report_generation.py",
            "apps/core/services/report_periodic_audio.py",
            "apps/core/services/daily_report_board.py",
            "apps/core/services/day_reports.py",
            "apps/core/services/synth.py",
            "apps/core/services/startup_voice.py",
            "apps/core/services/voice_events.py",
            "apps/core/services/broadcast.py",
            "apps/core/crons/on_time",
            "apps/core/crons/since_last_fire/remaining_tasks.py",
            "apps/core/crons/since_last_fire/hourly_chime.py",
            "apps/core/crons/since_last_fire/hourly_clip_reports.py",
            "apps/core/crons/since_last_fire/morning_boot_replay.py",
            "apps/core/crons/always_on/broadcast_loop.py",
            "apps/core/crons/on_time/daily_reports_catchup.py",
            "apps/core/crons/on_time/cursor_fallback.py",
            "apps/voice",
            "apps/core/routes/obs.py",
            "apps/core/routes/radio.py",
            "tests/test_report_freshness.py",
            "tests/test_catchup_no_morning_afternoon.py",
            "tests/test_voice_cooldown.py",
            "tests/test_music_bed_cleanup.py",
            "tests/test_obs_buildout.py",
        ],
        "job_ids": [
            "morning-report",
            "morning-report-play",
            "midday-report",
            "midday-report-play",
            "late-report",
            "late-report-play",
            "late-final-report",
            "time-chime",
            "hourly-clip-reports",
            "hourly-clip-prebuild",
            "remaining-tasks",
            "morning-boot-replay",
            "report-readiness",
            "report-periodic-audio",
            "day-reports-morning",
            "day-reports-midday",
            "day-reports-evening",
            "merged-morning-summary",
            "overnight-relay",
            "daily-reports-catchup",
            "cursor-fallback",
        ],
        "skill_extra": """
## Function desks already moved

`morning-report`, `morning-report-play`, `merged-morning`, `midday-report`, `midday-report-play`, `late-report`, `late-report-play`, `hourly-chime`, `remaining-tasks`, `morning-boot-replay`, `hourly-clip-reports`, `report-readiness`, `report-periodic-audio`, `day-reports-morning`, `day-reports-midday`, `day-reports-evening`, `overnight-relay`, `daily-reports-catchup`, `cursor-fallback`, `report-generation`, `reports`, `daily-report-board`, `startup-voice`, `report-audio-manual`, `report-blog`, `voice-events`, `synth`, `kokoro`, `xai`, `model-pick`. Generate/play libraries live in those desks.
""",
    },
    {
        "name": "weather-kilauea",
        "title": "Weather, Kīlauea, storms",
        "description": (
            "Maps NWS/NOAA, county stitch, Kīlauea, earthquakes, hurricane desk. Use when "
            "asked about weather, volcano, USGS, NHC, or storm radio."
        ),
        "roots": [
            "apps/core/services/nws_hawaii.py",
            "apps/core/services/kilauea.py",
            "apps/core/services/kilauea_cams.py",
            "apps/core/services/live_wx.py",
            "apps/core/services/weather.py",
            "apps/core/services/hurricane_desk.py",
            "apps/core/services/hurricane_tracker.py",
            "apps/core/services/radar_archive.py",
            "apps/core/services/official_weather_media.py",
            "apps/core/services/nhc_media.py",
            "apps/core/services/earthquake_hourly.py",
            "apps/core/services/earthquake_hourly_processor.py",
            "apps/core/services/geography.py",
            "apps/core/services/sun_times.py",
            "apps/council/quake_watch.py",
            "tests/test_hazard_daily_paths.py",
            "tests/test_weather_daily_paths.py",
            "tests/test_earthquake_hourly.py",
            "tests/test_nws_tropical_replay.py",
            "tests/council/test_quake_watch.py",
        ],
        "job_ids": [
            "rr-noaa",
            "rr-kilauea",
            "nws-hawaii-counties",
            "radar-archive",
            "official-weather-media",
            "earthquake-hourly",
            "earthquake-m2-poll",
            "council-quake",
            "hurricane-fetch",
            "hurricane-desk",
            "hurricane-desk-evening",
            "hurricane-radio-am",
            "hurricane-radio-mid",
            "hurricane-radio-pm",
            "hurricane-obs",
            "solar-notes-quarter-hour",
        ],
        "skill_extra": """
## Function desks already moved

`rr-kilauea`, `rr-noaa`, `nws-hawaii`, `earthquake-hourly`, `radar-archive`, `official-weather-media`, `hurricane-tracker`, `hurricane-desk`, `hurricane-fetch`, `hurricane-radio`, `hurricane-obs`, `nhc-media`, `kilauea-cams`, `council-quake`, `live-wx`, `geography`, `kilauea-alerts` — runners live in those skills. This topic is the grouping.

## Hybrid daily lines

Every 30 minutes (`solar-notes-quarter-hour`) the hybrid notebook gets the same `> ◇ **HHMM** —` stamps used for charge/power. Weather one-liners, weather-window sections, and Kīlauea status go there — not a separate jsonl.

Read [DAILY.md](DAILY.md) (sliced from today's hybrid file). Live copy is `~/.ollama/skills/hybrid-reports/store/Reports/.../hybrid-manual-daily-report-YYYY-MM-DD.md`. Current files: `Media/documents/reports/nws-hawaii-counties-current.md`, `kilauea-current.md`.
""",
        "abs_roots": [
            str(SKILLS / "rr-noaa" / "scripts" / "weather.py"),
            str(SKILLS / "nws-hawaii" / "scripts" / "nws_hawaii.py"),
            str(SKILLS / "earthquake-hourly" / "scripts" / "earthquake_hourly.py"),
            str(SKILLS / "rr-kilauea" / "scripts" / "kilauea.py"),
            str(SKILLS / "radar-archive" / "scripts" / "radar_archive.py"),
            str(SKILLS / "official-weather-media" / "scripts" / "official_weather_media.py"),
            str(SKILLS / "hurricane-tracker" / "scripts" / "hurricane_tracker.py"),
            str(SKILLS / "hurricane-desk" / "scripts" / "hurricane_desk.py"),
            str(SKILLS / "hurricane-fetch" / "scripts" / "job.py"),
            str(SKILLS / "hurricane-radio" / "scripts" / "job.py"),
            str(SKILLS / "hurricane-obs" / "scripts" / "job.py"),
            str(SKILLS / "nhc-media" / "scripts" / "job.py"),
            str(SKILLS / "nhc-media" / "scripts" / "nhc_media.py"),
            str(SKILLS / "kilauea-cams" / "scripts" / "kilauea_cams.py"),
            str(SKILLS / "council-quake" / "scripts" / "quake_watch.py"),
        ],
    },
    {
        "name": "council-telegram",
        "title": "Council and Telegram",
        "description": (
            "Maps Telegram council, personas, trust, desk-read, proposals, Ollama persona "
            "chat. Use when asked about Ava/Bruce Telegram, council skills, or /approve."
        ),
        "roots": ["apps/council", "tests/council"],
        "job_ids": ["council-bruce-stats", "governance-daily", "governance-self-update"],
        "skill_extra": """
## Function desks already moved

`ava-ivy`, `bruce-monitor`, `carly-mal`, `council-quake`, `council-bruce-stats`, `governance-daily`, `governance-self-update`, `governance-boot`. Shared Telegram long-poll is this desk.
""",
    },
    {
        "name": "ava-ivy",
        "title": "Ava Ivy",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Ava Ivy Telegram agent (@avaivy_bot). Public voice, brand, community, design. "
            "Use when posting or speaking as Ava."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "ava-ivy" / "SKILL.md"),
            str(SKILLS / "ava-ivy" / "prompt.md"),
            str(SKILLS / "ava-ivy" / "scripts" / "post.sh"),
        ],
    },
    {
        "name": "bruce-monitor",
        "title": "Bruce Monitor",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Bruce Monitor Telegram agent (@brucemonitor_bot). Ops, philosophy, academic "
            "discussion. Use when posting or speaking as Bruce."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "bruce-monitor" / "SKILL.md"),
            str(SKILLS / "bruce-monitor" / "prompt.md"),
            str(SKILLS / "bruce-monitor" / "scripts" / "post.sh"),
        ],
    },
    {
        "name": "carly-mal",
        "title": "Carly Mal",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Carly Mal Telegram agent (@carlymal_bot). Cybersecurity, safety, defence, "
            "strategy. Use when posting or speaking as Carly. Defensive only."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "carly-mal" / "SKILL.md"),
            str(SKILLS / "carly-mal" / "prompt.md"),
            str(SKILLS / "carly-mal" / "scripts" / "post.sh"),
        ],
    },
    {
        "name": "ava-ops",
        "title": "Ava Ops and Bluetooth",
        "description": (
            "Maps Ava Ops Android, Bluetooth RFCOMM bridge, ops API, desktop desk. Use "
            "when asked about the phone app, BT, mobile-dashboard, or Idle desk."
        ),
        "roots": [
            "apps/core/routes/ops.py",
            "apps/core/routes/desktop.py",
            "scripts/ava-ops.sh",
            "tests/test_ops_features.py",
            "tests/test_ops_contract.py",
            "tests/test_idle_stop.py",
        ],
        "job_ids": ["system-performance", "host-sample"],
        "skill_extra": """
## Function desks already moved

`android-sdk`, `kilauea-alerts`, `rootmc-android`, `system-perf`, `broadcast-loop`, `broadcast`, `feature-toggles`, `python-drop-runner`, `xmrig`, `net-gate`, `obs-studio`, `radio`. Phone Gradle root is this skill `android/`. Origin ops routes still Ava-Core.
""",
        "abs_roots": [
            str(SKILLS / "ava-ops" / "android" / "settings.gradle.kts"),
            str(SKILLS / "ava-ops" / "android" / "app" / "build.gradle.kts"),
            str(SKILLS / "ava-ops" / "android" / "app" / "src"),
            str(SKILLS / "ava-ops" / "scripts" / "ava-ops.sh"),
            str(SKILLS / "ava-ops" / "scripts" / "ava_bt_bridge.py"),
        ],
    },
    {
        "name": "android-sdk",
        "title": "Android SDK",
        "write_skill": False,
        "write_current": False,
        "description": (
            "OmniBook Android command-line SDK (ANDROID_HOME). Apps live in "
            "`ava-ops`, `kilauea-alerts`, and `rootmc-android` skills. Use when "
            "exploring sdkmanager, adb, compileSdk, or ANDROID_HOME."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "android-sdk" / "SKILL.md"),
            str(SKILLS / "android-sdk" / "scripts" / "sdk_status.py"),
            str(SKILLS / "android-sdk" / "scripts" / "android-env.sh"),
            str(SKILLS / "android-sdk" / "references" / "migrate.md"),
            str(SKILLS / "ava-ops" / "android" / "settings.gradle.kts"),
            str(SKILLS / "kilauea-alerts" / "android" / "settings.gradle.kts"),
            str(SKILLS / "rootmc-android" / "android" / "settings.gradle.kts"),
        ],
    },
    {
        "name": "android-build",
        "title": "Android build",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Linux Android build helpers (bump, assemble, stage). Use when building "
            "Ava Ops, Kīlauea Alerts, or RootMC APKs on this OmniBook."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "android-build" / "SKILL.md"),
            str(SKILLS / "android-build" / "scripts" / "assemble.sh"),
            str(SKILLS / "android-build" / "scripts" / "bump_mobile_version.py"),
            str(SKILLS / "android-build" / "scripts" / "stage_release_artifacts.py"),
            str(SKILLS / "android-build" / "scripts" / "android-env.sh"),
            str(SKILLS / "android-build" / "scripts" / "apps.json"),
        ],
    },
    {
        "name": "kilauea-alerts",
        "title": "Kīlauea Alerts Android",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Kīlauea Alerts Android app. Use when editing the volcano phone app "
            "or assembling that APK."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "kilauea-alerts" / "SKILL.md"),
            str(SKILLS / "kilauea-alerts" / "android" / "settings.gradle.kts"),
            str(SKILLS / "kilauea-alerts" / "android" / "app" / "build.gradle.kts"),
            str(SKILLS / "kilauea-alerts" / "android" / "app" / "src"),
        ],
    },
    {
        "name": "rootmc-android",
        "title": "RootMC Android",
        "write_skill": False,
        "write_current": False,
        "description": (
            "RootMC Android app. Use when editing the Minecraft phone app or "
            "assembling that APK. Not the live game server."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "rootmc-android" / "SKILL.md"),
            str(SKILLS / "rootmc-android" / "android" / "settings.gradle.kts"),
            str(SKILLS / "rootmc-android" / "android" / "app" / "build.gradle.kts"),
            str(SKILLS / "rootmc-android" / "android" / "app" / "src"),
        ],
    },
    {
        "name": "rootmc-mobile-web",
        "title": "RootMC mobile web",
        "write_skill": False,
        "write_current": False,
        "description": (
            "RootMC React mobile web (rootmc-mobile). Use when editing the "
            "phone-shaped web app, not the native Kotlin app."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "rootmc-mobile-web" / "SKILL.md"),
            str(SKILLS / "rootmc-mobile-web" / "site" / "package.json"),
        ],
    },
    {
        "name": "rootmc",
        "title": "RootMC",
        "description": (
            "Maps Minecraft live detect, player economy (Gold), D1 cache, plugins. Use "
            "when asked about RootMC, play.rootmc.net, or in-game detect."
        ),
        "roots": [
            "apps/core/routes/minecraft.py",
            "apps/core/crons/always_on/minecraft_live.py",
            "apps/core/crons/since_last_fire/player_economy.py",
            "apps/core/crons/always_on/d1_sync.py",
            "apps/core/services/minecraft_live.py",
            "apps/core/services/rootmc_economy.py",
            "apps/core/services/d1.py",
            "apps/core/services/rcon.py",
            "apps/core/services/mysql.py",
            "scripts/publish-rootmc.sh",
        ],
        "job_ids": ["minecraft-live", "player-economy-report", "d1-sync", "user-qrcodes", "account-import"],
        "skill_extra": """
## Function desks already moved

`minecraft-live`, `player-economy`, `d1-sync`, `user-qrcodes`, `account-import`, `rootmc-economy`, `rootmc-android`, `rootmc-mobile-web`. Routes, RCON, MySQL helpers still Ava-Core.
""",
    },
    {
        "name": "public-edge",
        "title": "Public edge and workers",
        "description": (
            "Maps Cloudflare workers, public site routes, inbox drain, Vercel builds, "
            "Stripe snapshot. Use when asked about rootrecord.cloud, tunnel, or workers."
        ),
        "roots": [
            "workers/src",
            "workers/kilauea-worker.ts",
            "workers/wrangler.ava-api.toml",
            "workers/wrangler.rootrecord-cloud.toml",
            "sites/avaivy-cloud",
            "sites/rootrecord-online",
            "sites/alexrs94-site",
            "sites/holding",
            "apps/core/routes/public_site.py",
            "apps/core/routes/local_site.py",
            "apps/core/heartbeat.py",
            "apps/core/crons/always_on/inbox_drain.py",
            "apps/core/crons/always_on/vercel_builds.py",
            "apps/core/crons/always_on/stripe_poll.py",
            "apps/core/services/offline_inbox.py",
            "apps/core/services/vercel_builds.py",
            "apps/core/services/stripe_poll.py",
            "apps/core/services/public_finance.py",
            "apps/core/services/finance_desk.py",
            "apps/core/services/adsense.py",
            "apps/core/services/admob.py",
            "apps/core/services/site_ops.py",
            "sites/avaivy-cloud/package.json",
            "sites/rootrecord-online/package.json",
        ],
        "job_ids": ["heartbeat", "inbox-drain", "vercel-builds", "stripe-poll", "api-prices", "adsense-eod", "admob-eod", "economy-brief"],
        "skill_extra": """
## Function desks already moved

`inbox-drain`, `vercel-builds`, `stripe-poll`, `api-prices`, `economy-brief`, `adsense-eod`, `admob-eod`, `heartbeat`, `finance-desk`, `public-finance`, `avaivy-cloud`, `rootrecord-online`, `alexrs94-site`, `holding`, `clients`, `fern-forest`, `freeltc`, `cloudflare-workers`. Origin public routes stay until origin cutover. Web-dev gigs are `clients`, not memberships.
""",
    },
    {
        "name": "media-hybrid",
        "title": "Media and hybrid reports",
        "description": (
            "Maps Media tree, hybrid daily reports, Core Ops report writers. Use when "
            "asked about Media paths, hybrid charge status, or Core Ops Reports."
        ),
        "roots": [
            "apps/core/services/hybrid_reports.py",
            "apps/core/services/media_library.py",
            "scripts/consolidate_media.sh",
            "scripts/convert_media_library.py",
            "tests/test_hybrid_report_format.py",
        ],
        "job_ids": ["solar-notes-quarter-hour", "hybrid-charge-status", "hourly-solar-weather"],
        "skill_extra": """
## Function desks already moved

`hybrid-night-poller`, `hybrid-reports`, `media-library`, `youtube-download`. Media files stay under `$HOME/Media`. Dated hybrid notebooks live in `hybrid-reports/store/Reports`.

## Daily stamps

Hybrid inserts are `> ◇ **HHMM** —` lines above `+++Automation Cut Off`, plus replaced Weather Forecast / Kilauea Prediction sections. Skills `weather-kilauea` and `ecoflow-automations` keep a DAILY.md slice of those lines.
""",
        "abs_roots": [
            str(SKILLS / "hybrid-reports" / "scripts" / "hybrid_reports.py"),
            str(REPORTS),
        ],
    },
    {
        "name": "ecoflow-automations",
        "title": "EcoFlow automations",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Maps RootRecord EcoFlow automations (Delta 2, River 2 Pro, Starlink AC, "
            "400 W PV gate, BLE poller, midnight/sunrise, quota cron)."
        ),
        "roots": [
            "apps/core/services/ecoflow_public.py",
            "apps/core/services/data_layout.py",
            "apps/core/services/energy.py",
            "apps/core/services/ecoflow_ac_solar_gate.py",
            "apps/core/crons/since_last_fire/solar_weather.py",
            "apps/core/scheduler.py",
            "scripts/systemd/ava-ecoflow-ble.service",
            "scripts/systemd/ava-hybrid-night.service",
            "tests/test_nightops_ble_store.py",
            "tests/test_ecoflow_public.py",
        ],
        "job_ids": ["ecoflow-quota", "drive-automation", "solar-notes-quarter-hour", "hybrid-charge-status", "hourly-solar-weather"],
        "abs_roots": [str(ECOFLOW)],
    },
    {
        "name": "desk-data-reader",
        "title": "Desk data reader",
        "write_skill": False,
        "write_current": False,
        "description": "How to read live Ava SQLite and MySQL in the database skill store.",
        "roots": [
            "apps/core/services/db_facts.py",
            "apps/core/services/mysql.py",
            "apps/core/services/identities.py",
            "apps/core/services/people.py",
            "apps/core/services/guests.py",
            "apps/core/services/governance.py",
            "apps/core/services/data_layout.py",
            "apps/core/services/api_ledger.py",
        ],
        "job_ids": [],
    },
    {
        "name": "live-directories",
        "title": "Live directories",
        "write_skill": False,
        "write_current": False,
        "description": "Topic index for fs-index (paths.txt).",
        "roots": [
            "apps/core/scheduler.py",
        ],
        "job_ids": ["fs-index"],
    },
    {
        "name": "clients",
        "title": "Web-dev clients",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Web-development gig customers, not RootMC/RootRecord memberships. "
            "Use when asked about clients, nibble.love, or paid site work."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "clients" / "SKILL.md"),
            str(SKILLS / "clients" / "references" / "gigs.md"),
            str(SKILLS / "clients" / "gigs" / "nibble.love" / "README.md"),
        ],
    },
    {
        "name": "fern-forest",
        "title": "Fern Forest site",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Fern Forest off-grid site on Hawaiʻi island (Leila Road TMKs). "
            "Use when asked about Fern Forest parcels or qPublic reports."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "fern-forest" / "SKILL.md"),
            str(SKILLS / "fern-forest" / "references" / "migrate.md"),
        ],
    },
    {
        "name": "freeltc",
        "title": "FreeLTC product",
        "write_skill": False,
        "write_current": False,
        "description": (
            "FreeLTC public product in development (freeltc.site). Use when asked "
            "about FreeLTC. Not the Litecoin node and not operator xmrig."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "freeltc" / "SKILL.md"),
            str(SKILLS / "freeltc" / "site" / "index.html"),
        ],
    },
    {
        "name": "bible-prayers",
        "title": "Bible and prayers",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Bible study, scripture lookup, and prayers. Use when a user asks for a verse, "
            "passage, devotion, Bible study, prayer, blessing, or to pray with them."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "bible-prayers" / "SKILL.md"),
            str(SKILLS / "bible-prayers" / "references" / "passages.md"),
            str(SKILLS / "bible-prayers" / "references" / "prayers.md"),
            str(SKILLS / "bible-prayers" / "store" / "SOURCE.md"),
            str(SKILLS / "bible-prayers" / "scripts" / "lookup.py"),
            str(SKILLS / "bible-prayers" / "scripts" / "learn.py"),
            str(SKILLS / "bible-prayers" / "scripts" / "fetch-web.sh"),
        ],
    },
    {
        "name": "jesus",
        "title": "Jesus The Christ",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Jesus The Christ Telegram agent (@bigguyinthesky_bot). Gospel morals, "
            "public-domain Scripture including Hebrew, Greek, and 1 Enoch. Use when "
            "posting or speaking as Jesus."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "jesus" / "SKILL.md"),
            str(SKILLS / "jesus" / "prompt.md"),
            str(SKILLS / "jesus" / "bot.py"),
        ],
    },
    {
        "name": "cooking",
        "title": "Cooking and recipes",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Recipes desk: save any shared recipe as user-submitted, cheap seed pots, "
            "and cook-from-pantry matching. Use when someone shares a recipe or asks "
            "what to cook. Not the nutrition database."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "cooking" / "SKILL.md"),
            str(SKILLS / "cooking" / "scripts" / "recipes.py"),
            str(SKILLS / "cooking" / "store" / "recipes.json"),
        ],
    },
    {
        "name": "nutrition",
        "title": "Nutrition foods",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Nutrient-dense food database. Use when asked about nutrition, vitamins, "
            "or high-value foods. Not recipes and not pantry stock."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "nutrition" / "SKILL.md"),
            str(SKILLS / "nutrition" / "scripts" / "foods.py"),
            str(SKILLS / "nutrition" / "store" / "foods.json"),
        ],
    },
    {
        "name": "pantry",
        "title": "Pantry stock",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Household food stock: what is on the shelf and which recipes that covers. "
            "Use when asked about the pantry or what we can cook from stock."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "pantry" / "SKILL.md"),
            str(SKILLS / "pantry" / "scripts" / "pantry.py"),
            str(SKILLS / "pantry" / "store" / "stock.json"),
        ],
    },
    {
        "name": "gardening",
        "title": "Gardening and polyculture",
        "write_skill": False,
        "write_current": False,
        "description": (
            "USDA-zone gardening specialized in polyculture and agroforestry. "
            "Hawaiʻi Island first: look up rain and winter min, do not treat the "
            "island as one climate. Use when asked what to plant, Big Island, "
            "hardiness zones, loʻi, or ulu."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "gardening" / "SKILL.md"),
            str(SKILLS / "gardening" / "scripts" / "garden.py"),
            str(SKILLS / "gardening" / "store" / "zones.json"),
            str(SKILLS / "gardening" / "store" / "guilds.json"),
            str(SKILLS / "gardening" / "references" / "official-databases.md"),
            str(SKILLS / "gardening" / "references" / "polyculture.md"),
            str(SKILLS / "gardening" / "references" / "hawaii.md"),
            str(SKILLS / "gardening" / "references" / "big-island.md"),
            str(SKILLS / "gardening" / "store" / "climates.json"),
            str(SKILLS / "gardening" / "zones" / "hawaii" / "OVERLAY.md"),
        ],
    },
    {
        "name": "root-record-registry",
        "title": "Root Record Registry",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Root Record Registry: UH ScholarSpace and OA papers tagged onto "
            "gardening, nutrition, history, geography. Use when Bruce needs a "
            "thesis, CTAHR report, or OA PDF. Not paywalled journals."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "root-record-registry" / "SKILL.md"),
            str(SKILLS / "root-record-registry" / "scripts" / "research.py"),
            str(SKILLS / "root-record-registry" / "store" / "catalog.json"),
            str(SKILLS / "root-record-registry" / "references" / "sources.md"),
            str(SKILLS / "root-record-registry" / "topics" / "gardening" / "SKILL.md"),
        ],
    },
    {
        "name": "research-oa",
        "title": "Open-access research (stub)",
        "write_skill": False,
        "write_current": False,
        "description": (
            "Stub. Use root-record-registry (Root Record Registry)."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "research-oa" / "SKILL.md"),
        ],
    },
    {
        "name": "history",
        "title": "American and Hawaiian history",
        "write_skill": False,
        "write_current": False,
        "description": (
            "American and Hawaiian history desk. Use when asked about US history, "
            "Hawaiʻi history, the Hawaiian Kingdom, overthrow, annexation, or "
            "statehood. Not ecosystem-history."
        ),
        "roots": [],
        "job_ids": [],
        "abs_roots": [
            str(SKILLS / "history" / "SKILL.md"),
            str(SKILLS / "history" / "references" / "american.md"),
            str(SKILLS / "history" / "references" / "hawaiian.md"),
            str(SKILLS / "history" / "store" / "SOURCE.md"),
            str(SKILLS / "history" / "scripts" / "lookup.py"),
        ],
    },
    {
        "name": "ecosystem-index",
        "title": "Ecosystem index",
        "write_skill": False,
        "write_current": False,
        "description": "Index of Ava-Core / RootRecord topic skills.",
        "roots": [
            ".cursor/skills/ecosystem-index/scripts",
            "apps/core/scheduler.py",
            ".cursor/rules/ecosystem-skills.mdc",
            ".cursor/rules/ecosystem-index.mdc",
        ],
        "job_any": True,
    },
    {
        "name": "ecosystem-history",
        "title": "Ecosystem history",
        "write_skill": False,
        "description": (
            "Condensed timeline of RootRecord/Ava evolution from local non-personal docs. "
            "Use when asked how we got here, old OptiPlex/Windows paths, or document history."
        ),
        "roots": ["AGENTS.md", ".cursor/skills/ecosystem-history/NARRATIVE.md"],
        "job_ids": [],
    },
]


def now_iso() -> str:
    return datetime.now(HST).isoformat(timespec="seconds")


def is_personal(path: Path | str) -> bool:
    return bool(PERSONAL_RE.search(str(path)))


def parse_jobs(scheduler: Path) -> list[dict]:
    if not scheduler.is_file():
        return []
    text = scheduler.read_text(encoding="utf-8", errors="replace")
    rows = []
    for chunk in re.split(r"\n\s*s\.add_job\(", text)[1:]:
        jid_m = re.search(r'id="([^"]+)"', chunk)
        if not jid_m:
            continue
        jid = jid_m.group(1)
        name_m = re.search(r'name="([^"]*)"', chunk)
        head = chunk.split("id=", 1)[0]
        trig = "see scheduler.py"
        im = re.search(r"IntervalTrigger\(([^)]*)\)", head)
        cm = re.search(r"CronTrigger\(([^)]*)\)", head)
        if im:
            trig = f"interval {im.group(1)}"
        elif cm:
            trig = f"cron {cm.group(1)}"
        run_m = re.search(r'_run\("([^"]+)"\)', head)
        rows.append(
            {
                "id": jid,
                "name": name_m.group(1) if name_m else jid,
                "when": trig,
                "cron_module": run_m.group(1) if run_m else "",
            }
        )
    return rows


def py_funcs(path: Path, limit: int = 80) -> list[str]:
    try:
        tree = ast.parse(path.read_text(encoding="utf-8", errors="replace"))
    except Exception:
        return []
    names = [
        n.name
        for n in tree.body
        if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))
    ]
    if len(names) > limit:
        return names[:limit] + [f"… {len(names) - limit} more"]
    return names


def collect_py(root: Path, cap_files: int = 250) -> list[Path]:
    if root.is_file() and root.suffix == ".py":
        return [root]
    if not root.is_dir():
        return []
    out: list[Path] = []
    for p in sorted(root.rglob("*.py")):
        if is_personal(p):
            continue
        if any(x in p.parts for x in ("vendor", "node_modules", ".venv", "__pycache__", "pb", "build")):
            continue
        out.append(p)
        if len(out) >= cap_files:
            break
    return out


def resolve_root(rel: str) -> Path:
    if rel.startswith("/"):
        return Path(rel)
    return AVA / rel


def write_skill(topic: dict) -> None:
    dest = SKILLS / topic["name"]
    dest.mkdir(parents=True, exist_ok=True)
    body = f"""---
name: {topic["name"]}
description: {topic["description"]}
---

# {topic["title"]}

Carla. Stay in this folder. Lead with what the live code does now.

## Keep current

After changing this topic, from Ava-Core run:

```bash
python3 .cursor/skills/ecosystem-index/scripts/refresh-all.py
```

This skill folder is the ops desk (`~/.ollama/skills`). Open it first. `desk/src` and `desk/ops` are
maps to related files. Runners live in `~/.ollama/skills/<fn>/scripts/`. `DAILY.md` is the processed day. `INDEX.md`
lists the live paths. `references/migrate.md` is the move checklist. `CURRENT.md` is the generated map.

Ava-Core / Core Ops copies are shims.

## How to answer

1. Follow `desk/` instead of hunting the repo.
2. Clock times are HST unless a file says UTC.
3. Night sleep (`Ecoflow/state/night-mode.json` `sleeping`) skips Ava scheduler jobs.
4. Historical Windows/OptiPlex paths are history only — see `ecosystem-history`.
"""
    extra = (topic.get("skill_extra") or "").strip()
    if extra:
        body = body.rstrip() + "\n\n" + extra + "\n"
    (dest / "SKILL.md").write_text(body, encoding="utf-8")


def write_current(topic: dict, jobs: list[dict]) -> None:
    dest = SKILLS / topic["name"]
    dest.mkdir(parents=True, exist_ok=True)
    want = set(topic.get("job_ids") or [])
    job_any = bool(topic.get("job_any"))
    picked = jobs if job_any else [j for j in jobs if j["id"] in want]
    lines = [
        f"# {topic['title']} — generated",
        "",
        f"Generated {now_iso()}. Do not edit by hand.",
        "",
        "Ops desk: [INDEX.md](INDEX.md) · runners under `desk/`.",
        "",
        "## Scheduler jobs (HST)",
        "",
    ]
    if picked:
        for j in picked:
            extra = f" → `{j['cron_module']}.run`" if j.get("cron_module") else ""
            lines.append(f"- `{j['id']}` — {j['name']} — {j['when']}{extra}")
    else:
        lines.append("- (none assigned)")
    lines += ["", "## Source files + top-level functions", ""]
    seen: set[Path] = set()
    for rel in topic.get("roots") or []:
        root = resolve_root(rel)
        if not root.exists():
            lines.append(f"- `{rel}` — missing")
            continue
        for py in collect_py(root):
            if py in seen:
                continue
            seen.add(py)
            rel_s = str(py.relative_to(AVA)) if str(py).startswith(str(AVA)) else str(py)
            fns = py_funcs(py)
            lines.append(f"### `{rel_s}`")
            lines.append("")
            if fns:
                for n in fns:
                    lines.append(f"- `{n}`")
            else:
                lines.append("- (no top-level functions)")
            lines.append("")
    for absr in topic.get("abs_roots") or []:
        p = Path(absr)
        if p.name == "Reports":
            lines.append(f"- extra root `{p}` — {'ok' if p.exists() else 'missing'} (dated reports, not listed)")
            continue
        if not p.exists():
            lines.append(f"- extra root `{p}` — missing")
            continue
        for py in collect_py(p, cap_files=250):
            if py in seen:
                continue
            seen.add(py)
            fns = py_funcs(py)
            lines.append(f"### `{py}`")
            lines.append("")
            for n in fns or ["(no top-level functions)"]:
                lines.append(f"- `{n}`")
            lines.append("")
    lines.append("")
    (dest / "CURRENT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def doc_date(path: Path) -> str:
    m = re.search(r"(20\d{2}-\d{2}-\d{2})", path.name)
    if m:
        return m.group(1)
    try:
        return datetime.fromtimestamp(path.stat().st_mtime, HST).strftime("%Y-%m-%d")
    except OSError:
        return "unknown"


def first_heading(path: Path) -> str:
    try:
        text = path.read_text(encoding="utf-8", errors="replace")[:4000]
    except OSError:
        return path.name
    for line in text.splitlines():
        s = line.strip()
        if s.startswith("# "):
            return s[2:].strip()[:140]
    return path.stem.replace("-", " ")[:140]


def scan_docs() -> list[dict]:
    roots = [
        DOCS / "council-deploy" / "Implemented",
        AVA / "AGENTS.md",
        AVA / "README.md",
        AVA / "operations",
        AVA / "apps" / "core" / "crons",
        OPS,
        MEDIA / "public" / "documents" / "notes",
        MEDIA / "public" / "documents" / "plans",
        DOCS / "Stale Root Reports" / "home" / "rootrecord" / "RootRecord",
        DOCS / "Stale Root Reports" / "mnt" / "Projects" / "RootRecord Core" / "Documents" / "AVA-CORE-CONTEXT",
    ]
    skip_dir = {
        "node_modules",
        ".git",
        ".venv",
        "vendor",
        "queue",
        "posts",
        "audio",
        "images",
        "pb",
    }
    rows: list[dict] = []
    for root in roots:
        if not root.exists():
            continue
        if root.is_file():
            paths = [root]
        else:
            paths = list(root.rglob("*"))
        for p in paths:
            if not p.is_file() or p.suffix.lower() not in {".md", ".txt"}:
                continue
            if is_personal(p):
                continue
            if any(part in skip_dir for part in p.parts):
                continue
            if re.match(r"job-ms", p.name, re.I):
                continue
            if "ava-persona" in str(p):
                continue
            if "/Reports/" in str(p) and "hybrid" not in p.name.lower():
                continue
            if p.stat().st_size > 400_000:
                continue
            name = p.name.lower()
            if not any(
                k in name or k in str(p).lower()
                for k in (
                    "plan",
                    "ecosystem",
                    "agents",
                    "readme",
                    "handoff",
                    "migration",
                    "nightops",
                    "ecoflow",
                    "avaops",
                    "media-home",
                    "stale-docs",
                    "session-work",
                    "drives",
                    "index.md",
                    "context",
                    "boot",
                    "telegram",
                    "council",
                    "hybrid",
                )
            ):
                continue
            rows.append(
                {
                    "date": doc_date(p),
                    "title": first_heading(p),
                    "path": str(p),
                }
            )
    rows.sort(key=lambda r: (r["date"], r["title"]))
    ranked = []
    for r in rows:
        path = r["path"]
        score = 3
        if "council-deploy/Implemented" in path:
            score = 0
        elif "/Media/public/documents" in path and "Stale" not in path:
            score = 1
        elif "AVA-CORE-CONTEXT" in path:
            score = 2
        ranked.append((score, r))
    ranked.sort(key=lambda x: (x[1]["title"].lower(), x[0], x[1]["date"]))
    seen_title: set[str] = set()
    out = []
    for score, r in ranked:
        key = r["title"].lower()
        if key in seen_title:
            continue
        seen_title.add(key)
        out.append(r)
    out.sort(key=lambda r: (r["date"], r["title"]))
    return out[-250:]


def scan_external() -> list[str]:
    if os.environ.get("AVA_SCAN_EXTERNAL", "").strip() not in {"1", "true", "yes"}:
        return [
            "External sweep skipped this pass (set AVA_SCAN_EXTERNAL=1).",
            "Local docs were indexed first.",
            "",
        ]
    lines: list[str] = []
    candidates = [
        Path("/media"),
        Path("/mnt"),
        Path("/run/media"),
        HOME / "mnt",
    ]
    key_names = {"readme.md", "agents.md", "index.md", "drives.md", "manifest.txt"}
    found_files = 0
    for base in candidates:
        if not base.exists():
            continue
        try:
            kids = [k for k in sorted(base.iterdir()) if k.is_dir() and not k.name.startswith(".")]
        except OSError:
            continue
        if not kids:
            continue
        lines.append(f"## {base}")
        lines.append("")
        for kid in kids:
            lines.append(f"### `{kid}`")
            lines.append("")
            try:
                top = sorted(kid.iterdir())
            except OSError as e:
                lines.append(f"- unreadable: {e}")
                lines.append("")
                continue
            dirs = [p.name + "/" for p in top if p.is_dir() and not p.name.startswith(".") and not is_personal(p)][:40]
            files = [p.name for p in top if p.is_file() and not p.name.startswith(".") and not is_personal(p)][:20]
            if dirs:
                lines.append("- top dirs: " + ", ".join(f"`{d}`" for d in dirs))
            if files:
                lines.append("- top files: " + ", ".join(f"`{f}`" for f in files))
            hits = []

            def walk(cur: Path, depth: int) -> None:
                if len(hits) >= 25 or depth > 3:
                    return
                try:
                    children = list(cur.iterdir())
                except OSError:
                    return
                for p in children:
                    if is_personal(p) or p.name.startswith("."):
                        continue
                    if p.is_file() and p.name.lower() in key_names:
                        hits.append(p)
                    elif p.is_dir() and p.name not in {"node_modules", ".git", ".venv"}:
                        walk(p, depth + 1)

            walk(kid, 0)
            for hit in hits:
                lines.append(f"- `{hit}` — {first_heading(hit)}")
                found_files += 1
            lines.append("")
    if found_files == 0:
        lines.append("No README/INDEX/DRIVES found under those mounts this pass.")
        lines.append("")
    return lines


def write_history(entries: list[dict], external: list[str]) -> None:
    dest = SKILLS / "ecosystem-history"
    dest.mkdir(parents=True, exist_ok=True)
    lines = [
        "# Ecosystem history — generated",
        "",
        f"Generated {now_iso()}. Non-personal local docs only.",
        "",
        "## Locked present (do not regress)",
        "",
        "- Host is HP OmniBook 5, user `rootrecord`, Ubuntu. Dell OptiPlex is dead.",
        "- Live tree: `/home/rootrecord/.ollama/skills/origin`. Origin `http://127.0.0.1:8787/`.",
        "- Media: `/home/rootrecord/Media` (`AVA_MEDIA_DIR`).",
        "- Public doors: rootrecord.cloud / avaivy.cloud. RootMC live: `play.rootmc.net`. Currency Gold.",
        "- Starlink is Delta 2 AC (never switched). 400 W gate owns Delta USB-C into River.",
        "- Do not use `C:\\Users\\rootr\\ava`, `/home/ava-core/ava`, or OptiPlex LAN as live.",
        "",
        "## Evolution (filename/mtime dates, not invented)",
        "",
    ]
    by: dict[str, list[dict]] = {}
    for e in entries:
        by.setdefault(e["date"][:7], []).append(e)
    for month in sorted(by):
        lines.append(f"### {month}")
        lines.append("")
        for e in by[month][:80]:
            lines.append(f"- **{e['date']}** — {e['title']}")
            lines.append(f"  `{e['path']}`")
        lines.append("")
    lines += ["## External sweep", ""] + external
    lines += [
        "## How to read attached archives",
        "",
        "- `/mnt/4tb` is `/dev/sda1` ext4 label `4tb` (fstab). CHECKPOINTS plus the old D: / coin trees. Not the NVMe.",
        "- `/mnt/Archives` and `/mnt/archives` had no readable top-level docs this pass.",
        "- Do not open `credentials`, `.env`, or database dumps in chat.",
        "- Live Ava does not read these mounts at runtime.",
        "",
        "## Narrative",
        "",
        "Hand-maintained story: `NARRATIVE.md` in this folder. Refresh does not overwrite it.",
        "",
    ]
    (dest / "CURRENT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (dest / "SKILL.md").write_text(
        """---
name: ecosystem-history
description: >-
  Condensed timeline of RootRecord/Ava evolution from local non-personal docs.
  Use when asked how we got here, old OptiPlex/Windows paths, archives, or
  whether old backups are still needed.
---

# Ecosystem history

Read [NARRATIVE.md](NARRATIVE.md) first (the story). Then [CURRENT.md](CURRENT.md)
and [INDEX.md](INDEX.md). `desk/` has the related files.

When the live story changes (new host, new Starlink pack, public door change),
edit NARRATIVE.md in the same turn, then run refresh-all.py so CURRENT.md
matches. OptiPlex and `C:\\\\Users\\\\rootr\\\\ava` are not live.
""",
        encoding="utf-8",
    )


def write_index(jobs: list[dict], topics: list[dict]) -> None:
    dest = SKILLS / "ecosystem-index"
    dest.mkdir(parents=True, exist_ok=True)
    (dest / "SKILL.md").write_text(
        f"""---
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
- History docs that say OptiPlex or `C:\\Users\\rootr\\ava` are not live.

See [CURRENT.md](CURRENT.md) and [INDEX.md](INDEX.md). Every topic folder is the ops desk (`desk/src`).
""",
        encoding="utf-8",
    )
    lines = [
        "# Ecosystem index — generated",
        "",
        f"Generated {now_iso()}.",
        "",
        f"- Scheduler jobs parsed: **{len(jobs)}**",
        f"- Topic skills: **{len(TOPICS)}**",
        "",
        "## All jobs",
        "",
    ]
    for j in jobs:
        lines.append(f"- `{j['id']}` — {j['when']} — {j['name']}")
    lines.append("")
    (dest / "CURRENT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    jobs = parse_jobs(SKILLS / "scheduler-clock" / "scripts" / "scheduler.py")
    for topic in TOPICS:
        if topic.get("write_skill", True) and topic["name"] != "ecosystem-history":
            write_skill(topic)
        if topic.get("write_current", True) and topic["name"] != "ecosystem-history":
            write_current(topic, jobs)
    eco = SKILLS / "ecoflow-automations" / "scripts" / "refresh.py"
    if eco.is_file():
        os.system(f"python3 {eco}")
    write_history(scan_docs(), scan_external())
    catalog_topics = [t for t in TOPICS if t.get("write_skill", True) and t["name"] != "ecosystem-history"]
    write_index(jobs, catalog_topics)
    manifest = {
        "at": now_iso(),
        "jobs": len(jobs),
        "topics": [t["name"] for t in TOPICS],
    }
    (SKILLS / "ecosystem-index" / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(manifest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
