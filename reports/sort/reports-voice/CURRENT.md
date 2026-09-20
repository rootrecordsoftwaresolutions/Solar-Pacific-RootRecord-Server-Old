# Reports and voice — generated

Generated 2026-09-17T01:41:42-10:00. Do not edit by hand.

Ops desk: [INDEX.md](INDEX.md) · runners under `desk/`.

## Scheduler jobs (HST)

- `time-chime` — Time chime (:00/:30) — cron minute="0,30" → `hourly_chime.run`
- `remaining-tasks` — Remaining tasks (:32, 1h + failed due) — cron minute=32 → `remaining_tasks.run`
- `morning-boot-replay` — Morning boot MP3 replay (:32 until noon) — cron minute=32 → `morning_boot_replay.run`
- `hourly-clip-prebuild` — Prebuild hourly clip reports — cron minute=55
- `hourly-clip-reports` — Play hourly clip reports — cron minute=2 → `hourly_clip_reports.run`
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
- `overnight-relay` — Late-night relay — cron hour=22, minute=20 → `overnight.run`

## Source files + top-level functions

- `apps/core/services/boot_report.py` — missing
- `apps/core/services/midday_report.py` — missing
- `apps/core/services/reports.py` — missing
- `apps/core/services/report_generation.py` — missing
- `apps/core/services/report_periodic_audio.py` — missing
- `apps/core/services/daily_report_board.py` — missing
- `apps/core/services/day_reports.py` — missing
- `apps/core/services/synth.py` — missing
- `apps/core/services/startup_voice.py` — missing
- `apps/core/services/voice_events.py` — missing
- `apps/core/services/broadcast.py` — missing
- `apps/core/crons/on_time` — missing
- `apps/core/crons/since_last_fire/remaining_tasks.py` — missing
- `apps/core/crons/since_last_fire/hourly_chime.py` — missing
- `apps/core/crons/since_last_fire/hourly_clip_reports.py` — missing
- `apps/core/crons/since_last_fire/morning_boot_replay.py` — missing
- `apps/core/crons/always_on/broadcast_loop.py` — missing
- `apps/core/crons/on_time/daily_reports_catchup.py` — missing
- `apps/core/crons/on_time/cursor_fallback.py` — missing
- `apps/voice` — missing
- `apps/core/routes/obs.py` — missing
- `apps/core/routes/radio.py` — missing
### `tests/test_report_freshness.py`

- `_write_json`
- `test_report_metrics_freshness_checks_recent_state`
- `test_disabled_startup_voice_is_not_stale`
- `test_write_current_replaces_previous_generated_report`

### `tests/test_catchup_no_morning_afternoon.py`

- `test_catchup_window_morning_closes_at_noon`
- `test_afternoon_catchup_skips_failed_morning`
- `test_play_if_due_skips_evening`
- `test_play_if_due_skips_morning_after_noon`
- `test_run_morning_slot_skips_after_noon`

### `tests/test_voice_cooldown.py`

- `test_global_voice_cooldown_is_ten_minutes`

### `tests/test_music_bed_cleanup.py`

- `test_windows_music_sweep_keeps_active_bed_and_kills_duplicates`
- `test_windows_bed_uses_python_executable_not_pythonw_launcher`

### `tests/test_obs_buildout.py`

- `test_first_existing_prefers_available_wav`
- `test_overlay_routes_are_idle_when_obs_is_closed`
- `test_official_rotation_defaults_to_ten_seconds`
- `test_rotation_config_persists_enabled_and_interval`
- `test_legacy_rotation_config_uses_daily_interval`
- `test_disabled_rotation_short_circuits_before_obs`
- `test_rotate_scene_ignores_missing_scene_names`
- `test_startup_voice_prefers_existing_wav`


