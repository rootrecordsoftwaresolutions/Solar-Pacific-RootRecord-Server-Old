# EcoFlow automations — generated

Generated 2026-09-17T01:41:42-10:00.
Do not edit by hand. Run `scripts/refresh.py` after code changes.

## Missing files

- layout: `/home/rootrecord/.ollama/skills/origin/apps/core/services/data_layout.py`
- ble_unit: `/home/rootrecord/.ollama/skills/origin/scripts/systemd/ava-ecoflow-ble.service`

## Hardware / serials (from code)

- Delta SN: `'R331ZAB5SG6S2858'`
- River SN: `'R621ZA16XH6K1155'`
- Starlink SN: `'R331ZAB5SG6S2858'` (must equal Delta)
- Delta BLE MAC default: `'24:58:7C:20:92:61'`
- River BLE MAC default: `'DC:06:75:56:AC:1D'`

## Clocks (HST)

- Starlink sleep window: `night-mode.json` `midnight_off` (default `00:00`, env `AVA_ECOFLOW_SLEEP_START`) through today's sunrise (`in_starlink_sleep` / `sleep_start_hhmm`). Fallback sunrise `(6, 8)`.
- Midnight sequence: first tick after sleep start when `sleeping` rises, unless already stamped `midnight_seq_date` today.
- Sunrise sequence: first tick after sunrise when sleep ends; stamps `sunrise_seq_date`.
- BLE write interval day: `10` s
- BLE write interval night: `300` s
- BLE loop tick: `10` s
- BLE scan: `8` s, cache `12` s
- Quota stale (store/gate): `180` / `180` s
- BLE fresh (store): `60` s
- Gate cooldown: `180` s
- Gate voice cooldown: `90` s

## Thresholds

- Total fresh PV gate: `400` W (Delta USB on if below, off if at/above)
- USB night-off after zero PV: `300` s (stopped below `1` W)
- Delta USB min SOC: `3` %
- Generator/transfer match band: `50` W
- Delta generator AC-in ceiling (desk): `750` W
- River generator AC-in ceiling (desk): `800` W
- Nameplate Wh: `{'DELTA 2': 1024.0, 'RIVER 2 Pro': 768.0}`

## AVA scheduler jobs that touch EcoFlow

*Night sleep (`night-mode.json` sleeping=true) skips these via `_WaveScheduler`.*

- `hourly-solar-weather` — Hourly solar+weather — cron minute=4
- `solar-notes-quarter-hour` — Solar Notes EcoFlow status (every 30 minutes) — interval minutes=30
- `ecoflow-quota` — EcoFlow quota refresh — interval minutes=2
- `drive-automation` — River car DC drive session — interval minutes=30
- `host-sample` — Host CPU/RAM sample — interval minutes=1

## Functions (top-level)

### ecoflow_ble_poller.py

- `_load_ava_env`
- `_now`
- `_hhmm`
- `_read_json`
- `_write_json`
- `_state_path`
- `_sun_cache_path`
- `_gate_path`
- `_inhibit_path`
- `_sunrise_flag_path`
- `_merge_sun`
- `refresh_sun`
- `sunrise_hhmm`
- `sleep_start_hhmm`
- `in_starlink_sleep`
- `internet_up`
- `set_power_profile`
- `operator_present`
- `_g`
- `_map_pack`
- `set_gate_sleep`
- `set_audio_silent`
- `write_night_state`
- `_idle_stop`
- `_start_ava_console`
- `_reboot`
- `maybe_apply_usb`
- `maybe_apply_ac`
- `_cloud_sign_get`
- `cloud_fetch_quota`
- `_put_ac_api`
- `_put_usb_api`
- `_read_quota_raw`
- `_quota_ac_in`
- `_starlink_ac`
- `maybe_cloud_persist`
- `apply_day_gate_from_disk`
- `_target_macs`
- `_scan_seen`
- `_device_from_sn`
- `_connect_pack`
- `_pack_supervisor`
- `_run_midnight_seq`
- `_run_sunrise_seq`
- `_night_orchestrator`
- `main`

### ecoflow_ble_store.py

- `ops_root`
- `quota_path`
- `history_path`
- `db_path`
- `session_log_path`
- `_num`
- `quota_at_s`
- `quota_age_s`
- `quota_source`
- `quota_data`
- `is_fresh`
- `is_recordable`
- `pv_w`
- `ac_in_w`
- `ac_out_w`
- `usb_out_w`
- `usb_on`
- `soc_of`
- `history_row`
- `skip_cloud_stomp`
- `persist_live_snapshot`
- `append_sqlite`
- `unlock_quota`
- `session_event`
- `total_fresh_pv`
- `classify_generator`
- `want_delta_usb_on`
- `want_delta_ac_on`

### ecoflow_ac_solar_gate.py

- `state_path`
- `_env_enabled_override`
- `load_state`
- `save_state`
- `_overlay_operator_flags`
- `_save_eval`
- `_quota_path`
- `read_delta_quota`
- `decide`
- `_soc_float`
- `decide_with_soc`
- `phrase_for_action`
- `_enqueue_announce`
- `_flatten`
- `_qstring`
- `_sign_headers`
- `_get_live_quota`
- `_usb_state`
- `_verify_usb_state`
- `_usb_command`
- `_put_usb`
- `evaluate`
- `run_after_quota`
- `operator_status`
- `patch_operator`
- `main`

### ecoflow_public.py

- `live_path`
- `prior_path`
- `_num`
- `_soc_live`
- `_detail`
- `snapshot`
- `desk_card`
- `write_live`
- `_load_prior`
- `_save_prior`
- `load_live`
- `overlay_board`
- `write_ops_guide`
- `format_status`
- `maybe_announce`
- `on_gate_update`

### ecoflow_quota.py

- `run`

### river_car_dc.py

- `_load_env`
- `state_path`
- `load_state`
- `save_state`
- `car_command`
- `car_state`
- `_flatten`
- `_qstring`
- `_sign_headers`
- `_quota_all`
- `put_car`
- `disk_car_on`
- `status`
- `set_car`
- `main`

### drive_automation.py

- `state_path`
- `lock_path`
- `load_config`
- `save_config`
- `enabled_copy_jobs`
- `run_copy_jobs`
- `_try_lock`
- `status`
- `power_on`
- `power_off`
- `session`
- `tick`
- `run`
- `set_auto`
- `main`

## Source paths

- poller: `/home/rootrecord/.ollama/skills/ecoflow-ble-poller/scripts/ecoflow_ble_poller.py` (ok)
- store: `/home/rootrecord/.ollama/skills/ecoflow-ble-poller/scripts/ecoflow_ble_store.py` (ok)
- gate: `/home/rootrecord/.ollama/skills/ecoflow-ac-solar-gate/scripts/ecoflow_ac_solar_gate.py` (ok)
- quota_cron: `/home/rootrecord/.ollama/skills/ecoflow-quota/scripts/ecoflow_quota.py` (ok)
- scheduler: `/home/rootrecord/.ollama/skills/scheduler-clock/scripts/scheduler.py` (ok)
- public: `/home/rootrecord/.ollama/skills/ecoflow-quota/scripts/ecoflow_public.py` (ok)
- layout: `/home/rootrecord/.ollama/skills/origin/apps/core/services/data_layout.py` (MISSING)
- solar: `/home/rootrecord/.ollama/skills/hourly-solar-weather/scripts/job.py` (ok)
- energy: `/home/rootrecord/.ollama/skills/ecoflow-quota/scripts/energy.py` (ok)
- ble_unit: `/home/rootrecord/.ollama/skills/origin/scripts/systemd/ava-ecoflow-ble.service` (MISSING)
- river_car: `/home/rootrecord/.ollama/skills/ecoflow-river-car/scripts/river_car_dc.py` (ok)
- drive_automation: `/home/rootrecord/.ollama/skills/ecoflow-river-car/scripts/drive_automation.py` (ok)
- delta2_test: `/home/rootrecord/.ollama/skills/ecoflow-automations/scripts/ecoflow_delta2_power_test.py` (ok)
