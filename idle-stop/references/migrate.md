# Migrate `idle-stop`

Status: **moved**.

| | |
| --- | --- |
| From | `Ava-Core/scripts/idle-stop.sh` |
| To | `~/.ollama/skills/idle-stop/scripts/idle-stop.sh` |
| Shim | Ava-Core `scripts/idle-stop.sh` execs this file |
| Callers | launch trap, `POST /api/ops/idle-stop`, BLE midnight, council ollama_ctl |

Do not restore a second full copy under Ava-Core/scripts.
