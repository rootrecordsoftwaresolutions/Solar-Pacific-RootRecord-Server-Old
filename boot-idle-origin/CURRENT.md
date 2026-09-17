# Boot, idle, origin — generated

Generated 2026-09-17T01:41:42-10:00. Do not edit by hand.

Ops desk: [INDEX.md](INDEX.md) · runners under `desk/`.

## Scheduler jobs (HST)

- `heartbeat` — CF heartbeat writer — interval seconds=60
- `log-cleanup` — Delete log files older than 7 days — cron hour=4, minute=20 → `log_cleanup.run`

## Source files + top-level functions

- `scripts/launch.sh` — missing
- `scripts/idle-stop.sh` — missing
- `scripts/recycle-origin.sh` — missing
- `scripts/ensure-ava-runtime.sh` — missing
- `scripts/autostart-launch.sh` — missing
- `scripts/ollama-env.sh` — missing
- `scripts/start-ava-companions.sh` — missing
- `scripts/systemd` — missing
### `tests/test_launch_script_repo_root.py`

- `test_launch_script_runs_with_project_root_as_cwd`
- `test_launch_script_has_origin_recycle_loop_with_cap`

### `tests/test_idle_stop.py`

- `test_idle_stop_dry_run_discovers_services_audio_and_ports`

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

### `tests/test_ollama_lifecycle.py`

- `_no_flm`
- `test_idle_does_not_stop_when_recent`
- `test_idle_stops_after_fifteen_minutes`
- `test_idle_keeps_flm_mapped_when_ollama_already_down`
- `test_message_starts_npu_not_gguf`
- `test_idle_stays_up_if_council_just_spoke`
- `test_idle_stays_up_during_brainstorm`


