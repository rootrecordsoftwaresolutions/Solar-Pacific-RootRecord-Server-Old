# Migrate `obs-studio`

Status: **moved**.

From apps/core/services/obs_*.py.

Quake overlay cache: `store/obs-quake-feed.json` (not `state/store`, not Ava-Core `data/`).

Also moved: workstation overlay helpers `new.py`, `solar_monitor.py`, `solar_obs_server.py`. Ava-Core `workstations/obs/` copies are shims.

Do not restore the old body.
