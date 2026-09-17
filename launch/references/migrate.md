# Migrate `launch`

Status: **moved**.

| | |
| --- | --- |
| From | `Ava-Core/scripts/launch.sh` + `autostart-launch.sh` |
| To | `~/.ollama/skills/launch/scripts/` |
| Autostart | desktop still Exec= Ava-Core autostart shim → this autostart |
| Shim | Ava-Core `scripts/launch.sh` execs this file |
| Night sleep | Autostart skips AVA Console; BLE may still run |
| Also | `scripts/install.sh` — one-shot venv/systemd; Ava-Core `scripts/install.sh` execs this. `AVA_ROOT` is the Ava-Core tree. |

Do not restore a second full copy under Ava-Core/scripts.
