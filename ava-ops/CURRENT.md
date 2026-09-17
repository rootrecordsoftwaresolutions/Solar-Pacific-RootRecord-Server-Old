# Ava Ops and Bluetooth — generated

Generated 2026-09-17T01:41:42-10:00. Do not edit by hand.

Ops desk: [INDEX.md](INDEX.md) · runners under `desk/`.

## Scheduler jobs (HST)

- `system-performance` — System performance — cron minute=6 → `system_perf.run`
- `host-sample` — Host CPU/RAM sample — interval minutes=1

## Source files + top-level functions

- `apps/core/routes/ops.py` — missing
- `apps/core/routes/desktop.py` — missing
- `scripts/ava-ops.sh` — missing
### `tests/test_ops_features.py`

- `test_ops_features_roundtrip`
- `test_obs_work_allowed_false_when_night`
- `test_obs_work_allowed_stays_off_when_process_mocked`
- `test_obs_rotator_not_scheduled_until_toggle`
- `test_obs_defaults_off_even_if_env_enable`
- `test_obs_auto_switch_defaults_off`

### `tests/test_ops_contract.py`

- `test_local_ops_contract_exposes_servers_and_actions`
- `test_mobile_dashboard_fits_rfcomm`

### `tests/test_idle_stop.py`

- `test_idle_stop_dry_run_discovers_services_audio_and_ports`

### `/home/rootrecord/.ollama/skills/ava-ops/scripts/ava_bt_bridge.py`

- `json_error`
- `read_json_line`
- `validate_request`
- `forward_local_api`
- `_trim_dashboard_for_rfcomm`
- `handle_message`
- `session_loop`
- `register_sdp_profile`
- `serve_bound_rfcomm`
- `serve`


