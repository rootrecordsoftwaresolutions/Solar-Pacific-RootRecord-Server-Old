---
name: ecoflow-quota
description: >-
  EcoFlow quota refresh cron (every 2 min when origin is up). Use when asked
  how ecoflow-quota runs, after-quota USB gate kick, or the quota job id.
---

# ecoflow-quota

This folder **is** the runtime. Quota JSON and history stay in Core Ops Ecoflow/. Do not copy serial files here. Do not invent watts or SOC.

## How it fires

- Scheduler job id `ecoflow-quota`, every **2 minutes** HST.
- **Skipped in night sleep** (BLE poller may still run).
- Origin `127.0.0.1:8787` must be up.

```bash
# same body the scheduler imports
python3 -c "import asyncio, sys; sys.path.insert(0,'/home/rootrecord/.ollama/skills/origin'); from importlib.util import spec_from_file_location, module_from_spec; p='/home/rootrecord/.ollama/skills/ecoflow-quota/scripts/ecoflow_quota.py'; s=spec_from_file_location('ecoflow_quota', p); m=module_from_spec(s); s.loader.exec_module(m); asyncio.run(m.run())"
```

After a snapshot it calls the USB solar gate (`run_after_quota`). Starlink stays on Delta AC — this job never PUTs AC.

## Live outputs (not in this skill)

Core Ops `Ecoflow/quota/`, `Ecoflow/state/ecoflow-ac-solar-gate.json`, `Ecoflow/state/ecoflow-live.json`.

Topic index: `ecoflow-automations`.
