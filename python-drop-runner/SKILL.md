---
name: python-drop-runner
description: >-
  Drop .py into timed folders (every 5 min clock slots + Every 5/15/30 Mins /
  Every Hour) or always-on root. Runs while AVA Console / origin is up.
---

# python-drop-runner

Drop folder (live):

`~/.ollama/skills/python-drop-runner/drop`

## Timed folders (Hawaiian Standard Time)

| Path | When it runs |
|------|----------------|
| `on-time/HH:MM/` | Once at that clock time (folders every 5 minutes, `00:00` … `23:55`) |
| `Every 5 Mins/` | On the 5-minute marks |
| `Every 15 minutes/` | `:00` `:15` `:30` `:45` |
| `Every 30 minutes/` | `:00` `:30` |
| `Every Hour/` | On the hour |

Drop any `*.py` into a folder. Timed jobs run **once per slot**, headless; logs in `drop/logs/`.

## Always-on

`drop/*.py` at the root still auto-start in a terminal and restart on exit (same as before).

## Bootstrap / status

```bash
~/.ollama/skills/origin/.venv/bin/python \
  ~/.ollama/skills/python-drop-runner/scripts/python_drop_runner.py --bootstrap

curl -s http://127.0.0.1:8787/api/ops/python-drop/status
```

Origin starts the runner with the console. Recycle origin after updating the runner so the timed folders are live.
