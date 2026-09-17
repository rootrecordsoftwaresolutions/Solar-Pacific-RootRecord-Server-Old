#!/usr/bin/env python3
"""Rebuild CURRENT.md from live EcoFlow / AVA source. No invented watts."""
from __future__ import annotations

import ast
import re
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

HST = ZoneInfo("Pacific/Honolulu")
SKILL = Path(__file__).resolve().parent.parent
HERE = Path(__file__).resolve().parent
import sys

if str(HERE.parents[1] / "ecosystem-index" / "scripts") not in sys.path:
    sys.path.insert(0, str(HERE.parents[1] / "ecosystem-index" / "scripts"))
try:
    from rr_home import AVA, ECOFLOW
except ImportError:
    AVA = Path.home() / "RootRecord" / "Ava-Core"
    ECOFLOW = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store"
OPS = ECOFLOW
OUT = SKILL / "CURRENT.md"

FILES = {
    "poller": Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "scripts" / "ecoflow_ble_poller.py",
    "store": Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "scripts" / "ecoflow_ble_store.py",
    "gate": Path.home() / ".ollama" / "skills" / "ecoflow-ac-solar-gate" / "scripts" / "ecoflow_ac_solar_gate.py",
    "quota_cron": Path.home() / ".ollama" / "skills" / "ecoflow-quota" / "scripts" / "ecoflow_quota.py",
    "scheduler": Path.home() / ".ollama" / "skills" / "scheduler-clock" / "scripts" / "scheduler.py",
    "public": Path.home() / ".ollama" / "skills" / "ecoflow-quota" / "scripts" / "ecoflow_public.py",
    "layout": AVA / "apps" / "core" / "services" / "data_layout.py",
    "solar": Path.home() / ".ollama" / "skills" / "hourly-solar-weather" / "scripts" / "job.py",
    "energy": Path.home() / ".ollama" / "skills" / "ecoflow-quota" / "scripts" / "energy.py",
    "ble_unit": AVA / "scripts" / "systemd" / "ava-ecoflow-ble.service",
    "river_car": Path.home() / ".ollama" / "skills" / "ecoflow-river-car" / "scripts" / "river_car_dc.py",
    "drive_automation": Path.home() / ".ollama" / "skills" / "ecoflow-river-car" / "scripts" / "drive_automation.py",
    "delta2_test": Path.home() / ".ollama" / "skills" / "ecoflow-automations" / "scripts" / "ecoflow_delta2_power_test.py",
}


def _mod(path: Path) -> ast.Module | None:
    if not path.is_file():
        return None
    return ast.parse(path.read_text(encoding="utf-8"), filename=str(path))


def _eval(node):
    if isinstance(node, ast.Name):
        return ("alias", node.id)
    try:
        return ast.literal_eval(node)
    except Exception:
        return None


def _const_assigns(mod: ast.Module) -> dict[str, object]:
    raw: dict[str, object] = {}
    for node in mod.body:
        target = None
        value = None
        if isinstance(node, ast.Assign) and len(node.targets) == 1:
            target = node.targets[0]
            value = node.value
        elif isinstance(node, ast.AnnAssign) and isinstance(node.target, ast.Name) and node.value is not None:
            target = node.target
            value = node.value
        if not isinstance(target, ast.Name) or value is None:
            continue
        raw[target.id] = _eval(value)
    out: dict[str, object] = {}
    for k, v in raw.items():
        if isinstance(v, tuple) and v and v[0] == "alias":
            other = v[1]
            if other in raw and not (isinstance(raw[other], tuple) and raw[other][0] == "alias"):
                out[k] = raw[other]
            elif other in out:
                out[k] = out[other]
            continue
        if v is not None:
            out[k] = v
    return out


def _funcs(mod: ast.Module) -> list[str]:
    names: list[str] = []
    for node in mod.body:
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            names.append(node.name)
    return names


def _fmt(v) -> str:
    if isinstance(v, float) and v == int(v):
        return str(int(v))
    return repr(v)


def _scheduler_jobs(text: str) -> list[str]:
    rows = []
    chunks = re.split(r"\n\s*s\.add_job\(", text)
    for chunk in chunks[1:]:
        jid_m = re.search(r'id="([^"]+)"', chunk)
        name_m = re.search(r'name="([^"]*)"', chunk)
        if not jid_m:
            continue
        jid = jid_m.group(1)
        name = name_m.group(1) if name_m else jid
        if not any(x in jid for x in ("ecoflow", "solar", "host-sample", "drive")):
            continue
        head = chunk.split("id=", 1)[0]
        trig = "see scheduler.py"
        im = re.search(r"IntervalTrigger\(([^)]*)\)", head)
        cm = re.search(r"CronTrigger\(([^)]*)\)", head)
        if im:
            trig = f"interval {im.group(1)}"
        elif cm:
            trig = f"cron {cm.group(1)}"
        rows.append(f"- `{jid}` — {name} — {trig}")
    return rows


def main() -> int:
    missing = [k for k, p in FILES.items() if not p.is_file()]
    mods = {k: _mod(p) for k, p in FILES.items() if p.suffix == ".py" and p.is_file()}
    poller_c = _const_assigns(mods["poller"]) if mods.get("poller") else {}
    store_c = _const_assigns(mods["store"]) if mods.get("store") else {}
    gate_c = _const_assigns(mods["gate"]) if mods.get("gate") else {}
    pub_c = _const_assigns(mods["public"]) if mods.get("public") else {}
    energy_c = _const_assigns(mods["energy"]) if mods.get("energy") else {}

    def c(*ds, key, default="?"):
        for d in ds:
            if key in d:
                return _fmt(d[key])
        return default

    sched = FILES["scheduler"].read_text(encoding="utf-8") if FILES["scheduler"].is_file() else ""
    lines = [
        "# EcoFlow automations — generated",
        "",
        f"Generated {datetime.now(HST).isoformat(timespec='seconds')}.",
        "Do not edit by hand. Run `scripts/refresh.py` after code changes.",
        "",
    ]
    if missing:
        lines += ["## Missing files", ""] + [f"- {k}: `{FILES[k]}`" for k in missing] + [""]
    lines += [
        "## Hardware / serials (from code)",
        "",
        f"- Delta SN: `{c(store_c, poller_c, key='DELTA_SN')}`",
        f"- River SN: `{c(store_c, poller_c, key='RIVER_SN')}`",
        f"- Starlink SN: `{c(store_c, poller_c, key='STARLINK_SN')}` (must equal Delta)",
        f"- Delta BLE MAC default: `{c(poller_c, key='DEFAULT_DELTA_MAC')}`",
        f"- River BLE MAC default: `{c(poller_c, key='DEFAULT_RIVER_MAC')}`",
        "",
        "## Clocks (HST)",
        "",
        f"- Starlink sleep window: `night-mode.json` `midnight_off` (default `00:00`, env `AVA_ECOFLOW_SLEEP_START`) through today's sunrise (`in_starlink_sleep` / `sleep_start_hhmm`). Fallback sunrise `{c(poller_c, key='SUN_FALLBACK')}`.",
        "- Midnight sequence: first tick after sleep start when `sleeping` rises, unless already stamped `midnight_seq_date` today.",
        "- Sunrise sequence: first tick after sunrise when sleep ends; stamps `sunrise_seq_date`.",
        f"- BLE write interval day: `{c(poller_c, key='DAY_WRITE_S')}` s",
        f"- BLE write interval night: `{c(poller_c, key='NIGHT_WRITE_S')}` s",
        f"- BLE loop tick: `{c(poller_c, key='TICK_S')}` s",
        f"- BLE scan: `{c(poller_c, key='SCAN_S')}` s, cache `{c(poller_c, key='SCAN_CACHE_S')}` s",
        f"- Quota stale (store/gate): `{c(store_c, key='STALE_S')}` / `{c(gate_c, key='MAX_QUOTA_AGE_S')}` s",
        f"- BLE fresh (store): `{c(store_c, key='BLE_FRESH_S')}` s",
        f"- Gate cooldown: `{c(gate_c, key='DEFAULT_COOLDOWN_S')}` s",
        f"- Gate voice cooldown: `{c(gate_c, key='VOICE_COOLDOWN_S')}` s",
        "",
        "## Thresholds",
        "",
        f"- Total fresh PV gate: `{c(store_c, key='PV_GATE_W')}` W (Delta USB on if below, off if at/above)",
        f"- USB night-off after zero PV: `{c(store_c, key='USB_NIGHT_OFF_S')}` s (stopped below `{c(store_c, key='PV_STOPPED_W')}` W)",
        f"- Delta USB min SOC: `{c(store_c, key='DELTA_USB_MIN_SOC')}` %",
        f"- Generator/transfer match band: `{c(store_c, key='GEN_MATCH_W')}` W",
        f"- Delta generator AC-in ceiling (desk): `{c(pub_c, key='DELTA_GEN_CEILING_W')}` W",
        f"- River generator AC-in ceiling (desk): `{c(pub_c, key='RIVER_GEN_CEILING_W')}` W",
        f"- Nameplate Wh: `{c(energy_c, key='PACK_WH')}`",
        "",
        "## AVA scheduler jobs that touch EcoFlow",
        "",
        "*Night sleep (`night-mode.json` sleeping=true) skips these via `_WaveScheduler`.*",
        "",
    ]
    jobs = _scheduler_jobs(sched)
    lines += (jobs or ["- (parser miss — read `apps/core/scheduler.py`)"]) + [""]
    lines += [
        "## Functions (top-level)",
        "",
    ]
    for key, title in (
        ("poller", "ecoflow_ble_poller.py"),
        ("store", "ecoflow_ble_store.py"),
        ("gate", "ecoflow_ac_solar_gate.py"),
        ("public", "ecoflow_public.py"),
        ("quota_cron", "ecoflow_quota.py"),
        ("river_car", "river_car_dc.py"),
        ("drive_automation", "drive_automation.py"),
    ):
        mod = mods.get(key)
        lines.append(f"### {title}")
        lines.append("")
        if not mod:
            lines.append("Missing.")
        else:
            for name in _funcs(mod):
                lines.append(f"- `{name}`")
        lines.append("")
    lines += [
        "## Source paths",
        "",
    ]
    for k, p in FILES.items():
        mark = "ok" if p.is_file() else "MISSING"
        lines.append(f"- {k}: `{p}` ({mark})")
    lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
