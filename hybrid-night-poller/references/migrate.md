# Migrate `hybrid-night-poller`

Status: **moved**.

| | |
| --- | --- |
| From | `RootRecord-Core-Ops/Hybrid Tracking Reports/hybrid_night_poller.py` |
| To | `~/.ollama/skills/hybrid-night-poller/scripts/hybrid_night_poller.py` |
| Systemd | `ava-hybrid-night.service` ExecStart → skill script |
| Shim | Core Ops file execs this script |

Do not move Media or the hybrid markdown notebooks into the skill.
