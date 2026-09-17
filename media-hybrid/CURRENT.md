# Media and hybrid reports — generated

Generated 2026-09-17T01:41:42-10:00. Do not edit by hand.

Ops desk: [INDEX.md](INDEX.md) · runners under `desk/`.

## Scheduler jobs (HST)

- `hourly-solar-weather` — Hourly solar+weather — cron minute=4 → `solar_weather.run`
- `solar-notes-quarter-hour` — Solar Notes EcoFlow status (every 30 minutes) — interval minutes=30
- `hybrid-charge-status` — Hybrid charge status (every 30 minutes) — interval minutes=30

## Source files + top-level functions

- `apps/core/services/hybrid_reports.py` — missing
- `apps/core/services/media_library.py` — missing
- `scripts/consolidate_media.sh` — missing
- `scripts/convert_media_library.py` — missing
### `tests/test_hybrid_report_format.py`

- `test_update_solar_notes_preserves_existing_text_and_appends`
- `test_update_solar_notes_keeps_manual_content_and_adds_power_automation`
- `test_update_solar_notes_ignores_shared_delta_ac_output_as_river_input`
- `test_ecoflow_history_keeps_delta_ac_output`
- `test_charge_status_insert_detects_plug_change_only`
- `test_update_hybrid_charge_status_inserts_at_current_minute`
- `test_hybrid_daily_report_path_uses_report_daily_folders`
- `test_new_hybrid_report_template_is_complete_and_laptop_safe`
- `test_prediction_sections_format_multiline_content_once`
- `test_append_hybrid_lifecycle_event_is_append_only_and_deduplicated`
- `test_update_solar_notes_replaces_prediction_sections_in_place`
- `test_update_solar_notes_mirrors_hybrid_lines_to_skills`

### `/home/rootrecord/.ollama/skills/hybrid-reports/scripts/hybrid_reports.py`

- `_daily_report_dir`
- `_automated_lines`
- `_automated_section`
- `_hybrid_report_template`
- `hybrid_daily_report_path`
- `ensure_hybrid_daily_report`
- `_ecoflow_roots`
- `_parse_history_at_ms`
- `_iter_history_rows`
- `_charge_status_insert`
- `_power_automation_insert`
- `_generator_insert`
- `_strip_legacy_automation_lines`
- `_append_report_inserts`
- `append_hybrid_lifecycle_event`
- `append_ecoflow_automation_event`
- `_media_current`
- `_first_prose`
- `_weather_headline`
- `_kilauea_headline`
- `_hybrid_prediction_inserts`
- `_replace_hybrid_prediction_sections`
- `_remove_legacy_prediction_inserts`
- `_mirror_hybrid_lines_to_skills`
- `update_solar_notes`
- `update_hybrid_daily_report`
- `update_hybrid_charge_status`

- extra root `/home/rootrecord/.ollama/skills/hybrid-reports/store/Reports` — ok (dated reports, not listed)

