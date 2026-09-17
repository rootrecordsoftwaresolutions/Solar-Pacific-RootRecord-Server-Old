# Solar mining multiplier (Gold G)

**Date:** 2026-08-02 (host-off lock 2026-08-02 night)  
**Owner:** Ava Ivy  
**Status:** shipped (API + Ava UI + Root-Economy hook)

## Formula (locked)

`multiplier = 1 + (batteryPercent / 100)`

- Clamp battery **0–100**
- Up to **3 decimal** places on multiplier (e.g. 37.5% → **1.375×**)
- Examples: 10% → **1.1×**, 20% → **1.2×**, 100% → **2.0×**
- Uses **aggregate bank SOC** (on-circuit EcoFlow average already on the solar dashboard) — **not** a sum of packs (three 100% packs still max **2×**)

## Online hours

**Online** = host device on + fresh host-site telemetry (≤15m) **and** EcoFlow not offline/stale **and** on-circuit bank `%` present.

### Host / device off → **normal Gold (1.0×)** (locked)

If the Root Server host is **off** (Ava asleep / power-down / `hostOnline: false` / `ecoStatus: host_off`) → multiplier **1.0×** immediately — do **not** keep the last battery boost through the stale window.

Same for EcoFlow feed offline / stale / off-circuit-only / device disconnected → **1.0×**.

## Surfaces

| Surface | Path / touch |
|---------|----------------|
| API | `GET https://api.rootmc.net/api/rootmc/solar-mining-multiplier` → `{ ok, battery_percent, multiplier, online, source, updated_at, detail }` (`detail: host_device_off` when host off) |
| Ava push | `hostSite.mjs` sets `hostOnline` + `ecoStatus: host_off` on sleep/power-down; enriches eco flags on telemetry POST |
| Ava solar page | KPI **Gold mine** next to bank %; banner pill |
| Plugin | `Root-Economy` — `SolarMiningMultiplierService` polls API; `GoldFoundListener` scales ore/block drops; loan ore repayment uses same mult |

## Deploy / jars

- Deploy: `powershell -File "Web Files\rootmc-api\deploy.ps1"` (picks up `host_device_off` detail; pre-deploy Workers already honor `ecoOffline`/`host_off` via Ava push)
- Stage **Root-Economy** jar to Claims + Towny handoffs after `publishPlugins` — **do not force Shockbyte restart**
- No boats jar

## Player copy

Currency is **Gold (G)**. Numbers only from live API / EcoFlow — never invent client-side.
