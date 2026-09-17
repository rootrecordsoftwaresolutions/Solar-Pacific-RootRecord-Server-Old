---
name: rootrecord-home
description: >-
  RootRecord lives under ~/.ollama: models in models/, topic ops in skills/.
  Use when asking where skills go, Ollama home, or before moving a function
  into a skill.
---

# RootRecord home

Carla. Open this skill first when placing files.

Canonical skills: `~/.ollama/skills/<name>/`. Function desks hold runners in
`scripts/`. Topic skills are groupings. Cursor sees the same tree via
`Ava-Core/.cursor/skills` (symlink). Ollama bundled `skill-creator` stays here;
do not overwrite it.

Moved: `fs-index`, `ecoflow-quota`, `rr-kilauea`, `rr-noaa`, `nws-hawaii`, `earthquake-hourly`, `radar-archive`, `official-weather-media`, `hurricane-tracker`, `hurricane-desk`, `hurricane-fetch`, `hurricane-radio`, `hurricane-obs`, `nhc-media`, `kilauea-cams`, `council-quake`, `morning-report`, `morning-report-play`, `merged-morning`, `midday-report`, `midday-report-play`, `late-report`, `late-report-play`, `hourly-chime`, `remaining-tasks`, `morning-boot-replay`, `hourly-clip-reports`, `report-readiness`, `report-periodic-audio`, `day-reports-morning`, `day-reports-midday`, `day-reports-evening`, `day-reports`, `overnight-relay`, `daily-reports-catchup`, `cursor-fallback`, `inbox-drain`, `vercel-builds`, `stripe-poll`, `api-prices`, `economy-brief`, `adsense-eod`, `admob-eod`, `heartbeat`, `minecraft-live`, `player-economy`, `d1-sync`, `user-qrcodes`, `account-import`, `council-bruce-stats`, `governance-daily`, `governance-self-update`, `governance-boot`, `log-cleanup`, `system-perf`, `hourly-solar-weather`, `code-review`, `broadcast-loop`, `boot-prelims`, `day-board-boot`, `recycle-origin`, `ollama-env`, `companions`, `ensure-ava-runtime`, `evening-report`, `evening-report-play`, `evening-report-audio`, `api-prices-boot`, `launch`, `idle-stop`, `ecoflow-ble-poller`, `ecoflow-ac-solar-gate`, `hybrid-night-poller`, `hybrid-reports`, `sunrise-restore`, `report-generation`, `reports`, `daily-report-board`, `startup-voice`, `report-audio-manual`, `report-blog`, `media-library`, `voice-events`, `synth`, `broadcast`, `ollama-lifecycle`, `ollama-client`, `feature-toggles`, `python-drop-runner`, `net-gate`, `uptime-log`, `obs-studio`, `radio`, `xai`, `model-pick`, `live-wx`, `geography`, `load-categories`, `youtube-download`, `finance-desk`, `public-finance`, `rootmc-economy`, `discord`, `slack`, `telegram`, `identities`, `people`, `guests`, `membership`, `subscribers`, `public-chat`, `goals`, `persona`, `look`, `reply-feedback`, `d1`, `mysql`, `rcon`, `db-facts`, `data-layout`, `site-backgrounds`, `site-ops`, `avaivy-cloud`, `rootrecord-online`, `alexrs94-site`, `holding`, `clients`, `fern-forest`, `freeltc`, `cloudflare-workers`, `live-data-pages`, `ops-banner`, `origin-session`, `host-metrics`, `broadcast-render`, `mp4-converter`, `inbox`, `voice`, `origin`, `android-sdk`, `android-build`, `kilauea-alerts`, `rootmc-android`, `rootmc-mobile-web`.

Do not move Media, `.env`, sqlite dumps, or model blobs into a skill.

Read `~/.ollama/ROOTRECORD.md` and `references/layout.json`.