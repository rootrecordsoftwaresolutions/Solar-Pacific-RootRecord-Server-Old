# Weather, Kīlauea, storms — generated

Generated 2026-09-17T01:41:42-10:00. Do not edit by hand.

Ops desk: [INDEX.md](INDEX.md) · runners under `desk/`.

## Scheduler jobs (HST)

- `rr-noaa` — NOAA weather — interval minutes=60 → `noaa.run`
- `radar-archive` — NWS Hawaii radar archive — interval minutes=10 → `radar_archive.run`
- `official-weather-media` — Official NHC/NWS media — interval minutes=10 → `official_weather_media.run`
- `nws-hawaii-counties` — NWS Hawaii by county — cron minute="7,22,37,52" → `nws_hawaii.run`
- `rr-kilauea` — Kīlauea — interval minutes=60 → `kilauea.run`
- `earthquake-hourly` — Earthquake hourly local WAV — cron minute=8 → `earthquake_hourly.run`
- `earthquake-m2-poll` — Earthquake local M≥2 poll — interval minutes=10
- `council-quake` — Ava USGS quake Telegram — interval minutes=2 → `council_quake.run`
- `solar-notes-quarter-hour` — Solar Notes EcoFlow status (every 30 minutes) — interval minutes=30
- `hurricane-fetch` — Hurricane fetch NHC/RAMMB/JTWC — cron hour="5,9,12,16,20", minute=40 → `hurricane_fetch.run`
- `hurricane-desk` — Hurricane desk text + WAV — cron hour="5,9,12,20", minute=50 → `hurricane_desk.run`
- `hurricane-desk-evening` — Hurricane desk evening build — cron hour=16, minute=55 → `hurricane_desk.run`
- `hurricane-radio-am` — Hurricane desk on radio (06:35) — cron hour=6, minute=35 → `hurricane_radio.run`
- `hurricane-radio-mid` — Hurricane desk on radio (13:12) — cron hour=13, minute=12 → `hurricane_radio.run`
- `hurricane-radio-pm` — Hurricane desk on radio (17:02) — cron hour=17, minute=2 → `hurricane_radio.run`

## Source files + top-level functions

- `apps/core/services/nws_hawaii.py` — missing
- `apps/core/services/kilauea.py` — missing
- `apps/core/services/kilauea_cams.py` — missing
- `apps/core/services/live_wx.py` — missing
- `apps/core/services/weather.py` — missing
- `apps/core/services/hurricane_desk.py` — missing
- `apps/core/services/hurricane_tracker.py` — missing
- `apps/core/services/radar_archive.py` — missing
- `apps/core/services/official_weather_media.py` — missing
- `apps/core/services/nhc_media.py` — missing
- `apps/core/services/earthquake_hourly.py` — missing
- `apps/core/services/earthquake_hourly_processor.py` — missing
- `apps/core/services/geography.py` — missing
- `apps/core/services/sun_times.py` — missing
- `apps/council/quake_watch.py` — missing
### `tests/test_hazard_daily_paths.py`

- `test_kilauea_daily_path_uses_report_root`
- `test_earthquake_daily_path_uses_report_root`

### `tests/test_weather_daily_paths.py`

- `test_weather_uses_human_readable_daily_folder`
- `test_weather_daily_folder_handles_ordinal_suffixes`

### `tests/test_earthquake_hourly.py`

- `test_earthquake_report_lists_only_unseen_events`
- `test_earthquake_report_keeps_magnitude_2_5_24_hour_sum`

### `tests/test_nws_tropical_replay.py`

- `test_hawaii_county_tropical_watch_or_warning_triggers_replay`
- `test_tropical_replay_ignores_other_counties_and_events`
- `test_quiet_spoken_has_no_wall_clock`

### `tests/council/test_quake_watch.py`

- `test_format_quake_uses_usgs_fields`
- `test_spoken_quake_expands_for_voice`
- `test_process_feed_seeds_without_posting`

### `/home/rootrecord/.ollama/skills/rr-noaa/scripts/weather.py`

- `_ordinal`
- `daily_report_dir`
- `weather_report_path`
- `_weather_content`
- `run`

### `/home/rootrecord/.ollama/skills/nws-hawaii/scripts/nws_hawaii.py`

- `state_path`
- `load_state`
- `save_state`
- `_county_keys_for_alert`
- `_normalize_alerts`
- `_by_county`
- `hawaii_county_tropical_watch_warning`
- `_seconds_since`
- `build_spoken`
- `_parse_nws_time`
- `product_as_of`
- `_hst_clock_label`
- `fingerprint`
- `fetch_alerts`
- `write_reports`
- `_event_to_clip`
- `build_clip_script`
- `stitch_and_play_local`
- `refresh`
- `facts_lines`
- `spoken_section_for_boot`
- `run`

### `/home/rootrecord/.ollama/skills/earthquake-hourly/scripts/earthquake_hourly.py`

- `_daily_report_dir`
- `_load_state`
- `_save_state`
- `_place_token`
- `_mag_token`
- `_mag_buckets`
- `_bucket_bits`
- `_fetch`
- `fetch_bundle`
- `facts_fingerprint`
- `_magnitude_at_least`
- `_new_events`
- `_event_report_lines`
- `_twenty_four_hour_lines`
- `build_spoken`
- `build_clip_script`
- `new_local_m2`
- `build_and_maybe_play`
- `run`

### `/home/rootrecord/.ollama/skills/rr-kilauea/scripts/kilauea.py`

- `_daily_report_dir`
- `_publish_path`
- `_publish_fingerprint`
- `_load_publish`
- `remember_publish`
- `decide_publish`
- `_fetch_hvo`
- `run`
- `_infer_alert_level`
- `_headline`
- `_write_alert_state`
- `get_multiplier`

### `/home/rootrecord/.ollama/skills/radar-archive/scripts/radar_archive.py`

- `_save_state`
- `fetch_and_archive`
- `run`

### `/home/rootrecord/.ollama/skills/official-weather-media/scripts/official_weather_media.py`

- `_current`
- `_save_state`
- `_clean_hls`
- `_looks_like_product`
- `_nws_latest_product`
- `_official_statement`
- `_hls_statement`
- `_archive`
- `_write_audio`
- `run`
- `apply_obs_scenes`

### `/home/rootrecord/.ollama/skills/hurricane-tracker/scripts/hurricane_tracker.py`

- `_storm_track_mod`
- `_mode_path`
- `current_mode`
- `write_mode`
- `hurricane_scene_pool`
- `load_storms`
- `_get`
- `_haversine_km`
- `_nm`
- `_parse_latlon`
- `_class_label`
- `_basin_name`
- `_nws_radar`
- `_windy`
- `_enrich`
- `_from_nhc`
- `_to_int`
- `_from_rammb`
- `_rammb_ir`
- `_from_jtwc`
- `_merge`
- `refresh_storms`
- `desk_payload`
- `_browser`
- `apply_hurricane_kit`
- `set_mode`
- `ensure_mode_collection`

### `/home/rootrecord/.ollama/skills/hurricane-desk/scripts/hurricane_desk.py`

- `hawaii_is_local_threat`
- `_reports_dir`
- `load`
- `save`
- `stage_busy`
- `acquire_stage`
- `release_stage`
- `_bearing`
- `_compass`
- `_nws_hawaii`
- `_tropical_nws`
- `nearest_hawaii`
- `_storm_latlon`
- `hawaii_block`
- `global_block`
- `_slug`
- `_have`
- `_push`
- `_name_tokens`
- `_class_before`
- `_class_slot`
- `_watch_products`
- `_statement_products`
- `_county_watch_clips`
- `_hazard_clips`
- `clip_script`
- `build`
- `public_payload`
- `play_on_radio`

### `/home/rootrecord/.ollama/skills/hurricane-fetch/scripts/job.py`

- `run`

### `/home/rootrecord/.ollama/skills/hurricane-radio/scripts/job.py`

- `run`

### `/home/rootrecord/.ollama/skills/hurricane-obs/scripts/job.py`

- `run`

### `/home/rootrecord/.ollama/skills/nhc-media/scripts/job.py`

- `run`

### `/home/rootrecord/.ollama/skills/nhc-media/scripts/nhc_media.py`

- `media_current`
- `media_archive`
- `data_root`
- `manifest_path`
- `load_manifest`
- `current_files`
- `live_url`
- `nhc_outlook_scenes`
- `_abs`
- `_ok_media`
- `_get`
- `_urls_from_html`
- `_fullsize_guess`
- `_stable_name`
- `_save_bytes`
- `_download_image`
- `_pull_gis`
- `_pull_text`
- `ingest`
- `apply_nhc_obs_scenes`
- `_first`
- `nhc_scene_names`

### `/home/rootrecord/.ollama/skills/kilauea-cams/scripts/kilauea_cams.py`

- `_get`
- `_embed`
- `obs_cam_url`
- `_obs_url_for_cam`
- `_still`
- `_video_id_from_watch`
- `_resolve_youtube`
- `refresh_catalog`
- `load_catalog`
- `kilauea_scene_pool`
- `_browser`
- `apply_kilauea_kit`
- `push_embeds_to_current_collection`

### `/home/rootrecord/.ollama/skills/council-quake/scripts/quake_watch.py`

- `_now`
- `_load`
- `_save`
- `_fmt_time_ms`
- `_mag_s`
- `format_quake`
- `spoken_quake`
- `deliver_quake`
- `_wanted`
- `process_feed`
- `should_fetch`
- `tick_async`
- `tick`


