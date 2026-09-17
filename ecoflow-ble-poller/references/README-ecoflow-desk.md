# EcoFlow Local Desk Operation

EcoFlow is a local RootRecord Core Ops operation. Live data, databases,
quota cache, history, load series, and AC solar-gate state stay here:

```text
/home/rootrecord/.ollama/skills/ecoflow-ble-poller/store
```

## Contents

- `ecoflow_ble_poller.py` — BLE sessions for Delta 2 and River 2 Pro, night
  idle sequences, 10 s day / 5 min night writes. Starlink is Delta AC (not switched).
- `ecoflow_ble_store.py` — persist, PV total, generator vs transfer, Delta USB want.
- `ecoflow_ac_solar_gate.py` — Delta USB PUT path when BLE does not own Delta.
  AVA imports this file via `apps/core/services/ecoflow_ac_solar_gate.py`.
- `state/ecoflow-ac-solar-gate.json` — last Delta USB decision.
- `state/night-mode.json` — sleeping, sunrise, midnight stamp.
- `quota/` — signed or BLE quota snapshots.
- `history/` — public-pack JSONL.
- `ecoflow-10s.db` — local EcoFlow snapshots.
- `loads/` — measured load-category series.

AVA scheduler integration lives under `Ava-Core/apps/core`. Paths resolve through
`apps.core.services.data_layout.ecoflow_dir()`.

The hidden third pack remains denied by the serial allowlist. Do not recreate
an `ava/data/ecoflow` tree.

Cursor skill: `Ava-Core/.cursor/skills/ecoflow-automations/`.
