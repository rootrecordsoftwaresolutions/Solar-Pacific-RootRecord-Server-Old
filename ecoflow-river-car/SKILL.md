---
name: ecoflow-river-car
description: >-
  River 2 Pro 12V car DC drive automation. Use when an LLM needs external
  USB/SATA disks, or the operator asks to turn drive power on or off. Default
  off. Never toggles AC. Copy/backup jobs are a later hook.
---

# ecoflow-river-car

This folder **is** the switch. Do not dump serials.

External drives ride **River 2 Pro car / 12V DC** only. Default is **off**. Starlink stays on Delta AC. River AC stays on for the laptop. Do not call AC APIs from this skill.

## Executable automation

`scripts/drive_automation.py` is the job. Scheduler id `drive-automation` every **30 min**, night-sleep gated. Tick **skips** unless `auto=true` **and** an enabled copy job exists **and** copy is implemented. Copy is still a stub — so the cron will not spin disks yet.

Live PUT (car DC only):

```bash
/home/rootrecord/.ollama/skills/origin/.venv/bin/python \
  ~/.ollama/skills/ecoflow-river-car/scripts/drive_automation.py --on --execute
.../drive_automation.py --off --execute
.../drive_automation.py --session --execute   # on, copy stub, off
.../drive_automation.py --status
```

Ops (LAN/localhost): `GET`/`POST /api/ops/drive-automation` `{action: status|on|off|session, execute: true}`. Desk `/ops` section **0e**.

Telegram `disk-session`: owner “turn on the drives” or `/skillrun disk-session --prepare --execute`.

State: Core Ops `Ecoflow/state/drive-automation.json` + `river-car-dc.json`. Later auto-copy: add `copy_jobs` entries (`id`, `enabled`, `src`, `dst`) then implement `run_copy_jobs`. Do not invent an rsync schedule.

## When an LLM needs the disks

1. `--on --execute` (or `--session --execute --hold`).
2. Do the disk work.
3. `--off --execute`. Do not leave car DC on.

## Low-level switch

`river_car_dc.py` / `disk_session.py` — `mpptCar` only. Never `acOutCfg`.

Topic: `ecoflow-automations`.
