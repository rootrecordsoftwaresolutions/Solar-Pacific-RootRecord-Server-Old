---
name: ecoflow-automations
description: >-
  Maps RootRecord EcoFlow automations (Delta 2, River 2 Pro, Starlink on
  Delta AC, 400 W USB-C PV gate, BLE poller, midnight/sunrise, quota cron).
  Use when the user mentions EcoFlow, River 2 Pro, Delta 2, Starlink, solar
  gate, pack SOC, or night sleep power.
---

# EcoFlow automations

Serials, API keys, and `.env` stay out of public copy. If a number is not
in a live file or the generated map, say so.

## Keep this skill current

After **any** EcoFlow behavior change, run:

```bash
python3 .cursor/skills/ecoflow-automations/scripts/refresh.py
```

Then re-read [CURRENT.md](CURRENT.md) and [INDEX.md](INDEX.md). This folder is the EcoFlow desk: `desk/ops` and `desk/src` symlink every runner. `DAILY.md` is the hybrid stamps. Treat CURRENT.md as derived. The authoritative code still executes from:

- `/home/rootrecord/.ollama/skills/ecoflow-ble-poller/scripts/ecoflow_ble_poller.py`
- `/home/rootrecord/.ollama/skills/ecoflow-ble-poller/scripts/ecoflow_ble_store.py`
- `/home/rootrecord/.ollama/skills/ecoflow-ac-solar-gate/scripts/ecoflow_ac_solar_gate.py`
- `/home/rootrecord/.ollama/skills/ecoflow-river-car/scripts/drive_automation.py`
- `/home/rootrecord/.ollama/skills/ecoflow-river-car/scripts/river_car_dc.py`
- `apps/core/scheduler.py` (`night_sleeping`, `ecoflow-quota`)
- `~/.ollama/skills/ecoflow-quota/scripts/ecoflow_quota.py`
- `apps/core/services/ecoflow_public.py`
- `apps/core/services/data_layout.py`
- `scripts/systemd/ava-ecoflow-ble.service`

`apps/core/services/ecoflow_ac_solar_gate.py` is a thin `exec` of the skill
`ecoflow-ac-solar-gate` script. Edit that skill copy.

## Site wiring

Off-grid. No wall outlet.

| Load | Pack | Who switches |
| --- | --- | --- |
| Starlink | **Delta 2 AC** | Nobody. Permanent. Never PUT/BLE AC off. |
| River USB-C in (~100 W DC) | **Delta 2 USB** | 400 W total-PV gate (day only) |
| External drives + Rear Shed panels cam | **River 2 Pro car 12V** | `ecoflow-river-car` (drives) + `panels-cam` (15‑min still / “show me the panels”). Default off. |
| Laptop | **River 2 Pro AC** | Nobody. Leave on (~1 W idle). |

`STARLINK_SN` must equal `DELTA_SN` in `ecoflow_ble_store.py`.

Public packs only: Delta `R331ZAB5SG6S2858`, River `R621ZA16XH6K1155`. Hidden
third pack is denied in `data_layout.py`.

## Clock (all HST, `Pacific/Honolulu`)

Read CURRENT.md for the numeric intervals extracted from code.

1. **`midnight_off` → sunrise** — `in_starlink_sleep()` true (name is historical).
   Default start is `00:00`. Override with `night-mode.json` `midnight_off` or
   `AVA_ECOFLOW_SLEEP_START` (`HH:MM`).
   - Starlink stays on Delta AC.
   - USB **off** after total PV is fully stopped for 5 minutes (dusk), unless
     operator is present (Cursor / inhibit). Starlink stays on Delta AC.
   - BLE writes every `NIGHT_WRITE_S` (5 min). Tick still `TICK_S` (10 s).
   - AVA scheduler jobs **skip** while `state/night-mode.json` `sleeping` is true.
   - First rising edge of sleep: `_run_midnight_seq` unless `midnight_seq_date` is today.
2. **Midnight sequence** (`_run_midnight_seq`): gate sleep on, mute sink, idle-stop
   Ava stack, power-saver, stamp date, **reboot**. Does **not** switch Delta AC or River AC.
3. **Operator present** (Cursor `pgrep`, `night-reboot-inhibit`, or
   `AVA_SKIP_NIGHT_REBOOT`): delay reboot. Still do not cut Starlink/Delta AC.
4. **Sunrise** (Open-Meteo cached in `state/sun-times.json`, fallback 06:08):
   `_run_sunrise_seq` — wait for internet, performance profile, unmute, gate sleep
   off, start AVA Console. Does not toggle AC.
5. **Sunrise → `midnight_off`** — day: BLE writes every `DAY_WRITE_S` (10 s). Delta USB
   follows total fresh PV.

Sun fetch: Open-Meteo Pāhala coords in the poller. After 18:00 also fills
`next_sunrise`.

## 400 W Delta USB gate

`want_delta_usb_on` / `evaluate`:

- Need **fresh** Delta + River PV samples (`is_recordable`, 180 s).
- `total_pv < 400` and Delta SOC ≥ 3% → Delta USB **on** (DC couple into River).
- `total_pv >= 400` → Delta USB **off**.
- Total PV fully stopped (`< 1 W`) for **300 s** → Delta USB **off** until PV returns.
- Operator present (Cursor / `night-reboot-inhibit` / `AVA_SKIP_NIGHT_REBOOT`) → **hold** that night USB-off.
- Midnight sleep window → USB **off** (same hold if operator present).
- SOC `< 3%` → Delta USB **off**.
- Missing SOC, generator, or night → **hold**.
- Generator: unmatched AC-in. USB couple: Delta USB-C ↔ River in within 50 W.
- BLE owning a fresh Delta quota (`source=ble` ≤ 90 s) skips the cloud PUT gate.
- Honor `manual_usb_off`. Cooldown 180 s. Env `AVA_ECOFLOW_AC_SOLAR_GATE=0` disables.

AVA `ecoflow_quota` every **2 min** (when origin is up and not night-sleeping)
calls `live_snapshot()` then `run_after_quota(execute=True)` (USB only).

## BLE poller service

User unit `ava-ecoflow-ble.service` runs `ecoflow_ble_poller.py` from this skill
with Ava-Core `.venv`. Working directory is `ecoflow-ble-poller/store`. Two supervisors + `_night_orchestrator`. Cloud fallback
is `maybe_cloud_persist` (must exist; do not merge it into `_quota_ac_in`).

Phone EcoFlow app off while this laptop owns GATT.

## Hybrid daily lines

Same job as weather: `solar-notes-quarter-hour` (30 min). EcoFlow 15-minute In/Out and POWER AUTOMATION / GENERATOR lines land in the hybrid notebook as `> ◇ **HHMM** —` stamps. Read [DAILY.md](DAILY.md). Do not invent watts or SOC if the line says Waiting / n/a.

## Live files

| Path | Role |
| --- | --- |
| `ecoflow-ble-poller/store/quota/{SN}.json` | Last BLE/cloud quota |
| `ecoflow-ble-poller/store/history/{SN}.jsonl` | Public history |
| `ecoflow-ble-poller/store/state/night-mode.json` | Sleep + Starlink on Delta AC |
| `ecoflow-ble-poller/store/state/ecoflow-ac-solar-gate.json` | USB gate decision |
| `ecoflow-ble-poller/store/state/river-car-dc.json` | River car DC (external drives + panels cam) |
| `ecoflow-ble-poller/store/state/panels-cam.json` | Panels still auto (15 min) |
| `ecoflow-ble-poller/store/state/drive-automation.json` | Drive automation auto/copy-job flags |
| `ecoflow-ble-poller/store/state/ble-poller.own` | Poller PID |
| `ecoflow-ble-poller/store/state/ecoflow-live.json` | Sanitized generator card |

## When changing Starlink night cut

Do not put Starlink back on River AC. Do not add Delta AC off. Refresh this skill.
Restart `systemctl --user restart ava-ecoflow-ble` after poller edits.

## Answers

Lead with what the code does now (after CURRENT.md + the files above). Do not
quote leftover comments that say Starlink is on River AC.
