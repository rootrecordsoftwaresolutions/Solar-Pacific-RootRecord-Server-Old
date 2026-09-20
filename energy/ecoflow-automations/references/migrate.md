# Migrate `ecoflow-automations` into this skill

Status: **mapped topic**. Function runners live in sibling desks. Vendor protobuf + quota JSON live in `ecoflow-ble-poller/store`. Do not copy `.env`.

When we cut this topic over:

1. Put runners in `scripts/` (Ollama skill shape).
2. Keep `SKILL.md` as the model instructions.
3. Point scheduler/systemd at the new scripts.
4. Leave Media, `.env`, sqlite, and jsonl history where they are.
5. Refresh: `python3 ~/.ollama/skills/ecosystem-index/scripts/refresh-all.py`

2026-09-16: Core Ops Ecoflow/ is gone. Store is `~/.ollama/skills/ecoflow-ble-poller/store`.
