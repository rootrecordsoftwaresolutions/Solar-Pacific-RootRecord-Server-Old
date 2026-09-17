# Migrate `ava-ops` into this skill

Status: **moved** 2026-09-16 HST.

Phone Gradle root: `android/` in this skill (from
`Ava-Core/workstations/android/ava-ops`). Ava-Core path is a symlink shim.

`scripts/ava-ops.sh` is the terminal menu. Ava-Core `scripts/ava-ops.sh` is a
shim. Bluetooth bridge: `scripts/ava_bt_bridge.py`. Origin ops routes still
origin.

Do not copy `.env` or keystores into chat. Do not invent watts, SOC, or player counts.
