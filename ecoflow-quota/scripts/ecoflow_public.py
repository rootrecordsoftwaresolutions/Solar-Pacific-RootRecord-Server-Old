"""Sanitized EcoFlow live file + rising-edge generator status.

No serials, tokens, or leftover 1% SOC presented as live. Council reads
Core Ops ``Ecoflow/state/ecoflow-live.json`` the same way desk-read does.
"""
from __future__ import annotations

import json
import logging
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

from apps.core import config

log = logging.getLogger("ava.ecoflow.public")
HST = ZoneInfo("Pacific/Honolulu")
LIVE_NAME = "ecoflow-live.json"
PRIOR_NAME = "generator-announce.json"
# Operator ceiling on generator AC-in. Not a charge setpoint. Lower is allowed.
DELTA_GEN_CEILING_W = 750.0
RIVER_GEN_CEILING_W = 800.0


def live_path() -> Path:
    from apps.core.services.data_layout import ecoflow_state_dir

    return ecoflow_state_dir() / LIVE_NAME


def prior_path() -> Path:
    p = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store" / "state"
    p.mkdir(parents=True, exist_ok=True)
    return p / PRIOR_NAME


def _num(v: Any) -> float | None:
    try:
        if v is None or v == "":
            return None
        return float(v)
    except (TypeError, ValueError):
        return None


def _soc_live(soc: float | None, *, generator: bool, pv_w: float | None) -> float | None:
    """Drop leftover 1% freeze — never present it as a live pack reading."""
    if soc is None:
        return None
    if generator:
        return soc
    if soc <= 1.5 and (pv_w is None or pv_w < 1):
        return None
    return soc


def _detail(*, generator: bool, transfer: bool, raw: str) -> str:
    text = (raw or "").strip() or "idle"
    if generator or transfer:
        return text
    if text in {"need_both_fresh", "sticky_until_fresh", "idle", "ble_gone"}:
        return text
    return "idle"


def snapshot(*, gate: dict, gen: dict) -> dict[str, Any]:
    gen = gen if isinstance(gen, dict) else {}
    gate = gate if isinstance(gate, dict) else {}
    now = datetime.now(HST)
    delta_ac = _num(gen.get("delta_ac_in_w") or gate.get("delta_ac_in_w"))
    river_ac = _num(gen.get("river_ac_in_w"))
    generator = bool(gen.get("generator") or gate.get("generator_detected"))
    transfer = bool(gen.get("transfer") or gate.get("transfer"))
    source = str(gate.get("quota_source") or gen.get("source") or "")
    ble = source.lower() == "ble"
    pv_w = _num(gate.get("total_pv_w"))
    over = False
    if delta_ac is not None and delta_ac > DELTA_GEN_CEILING_W:
        over = True
    if river_ac is not None and river_ac > RIVER_GEN_CEILING_W:
        over = True
    detail = _detail(
        generator=generator,
        transfer=transfer,
        raw=str(gen.get("detail") or gate.get("generator_detail") or ""),
    )
    if not ble and not generator and not transfer and detail == "idle":
        detail = "ble_gone"
    payload = {
        "at": now.isoformat(timespec="seconds"),
        "generator": generator,
        "transfer": transfer,
        "detail": detail,
        "ble": ble,
        "ble_gone": not ble,
        "delta": {
            "label": "DELTA 2",
            "soc": _soc_live(_num(gate.get("delta_soc") or gate.get("last_soc")), generator=generator, pv_w=pv_w),
            "ac_in_w": delta_ac,
            "pv_w": pv_w,
            "source": source,
            "ceiling_w": DELTA_GEN_CEILING_W,
        },
        "river": {
            "label": "RIVER 2 Pro",
            "ac_in_w": river_ac,
            "ceiling_w": RIVER_GEN_CEILING_W,
        },
        "gate": {
            "decision": gate.get("last_decision"),
            "reason": gate.get("decision_reason"),
        },
        "caps": {
            "kind": "ceiling",
            "delta_ac_in_max_w": DELTA_GEN_CEILING_W,
            "river_ac_in_max_w": RIVER_GEN_CEILING_W,
        },
        "over_ceiling": over,
    }
    payload["card"] = desk_card(payload)
    return payload


def desk_card(payload: dict[str, Any]) -> str:
    """Facts-only generator line for Ava Ops / desk. No Settings toggle."""
    delta = payload.get("delta") if isinstance(payload.get("delta"), dict) else {}
    ac_in = delta.get("ac_in_w")
    ac_txt = f"{ac_in:.0f} W" if isinstance(ac_in, (int, float)) else "Unknown"
    if payload.get("over_ceiling"):
        return format_status(payload)
    if payload.get("transfer"):
        return "Transfer (Delta↔River). Not generator."
    if payload.get("generator"):
        return format_status(payload)
    if payload.get("ble_gone") or payload.get("detail") in {"ble_gone", "need_both_fresh", "sticky_until_fresh"}:
        return f"Generator status: BLE disconnected, current status unknown. AC-in: {ac_txt}."
    return "Generator off."


def write_live(payload: dict[str, Any]) -> Path:
    path = live_path()
    tmp = path.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(path)
    return path


def _load_prior() -> dict[str, Any]:
    path = prior_path()
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _save_prior(row: dict[str, Any]) -> None:
    prior_path().write_text(json.dumps(row, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def load_live() -> dict[str, Any]:
    path = live_path()
    if not path.is_file():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def overlay_board(snap: dict[str, Any]) -> dict[str, Any]:
    """Attach generator facts onto the ops/solar board payload."""
    live = dict(load_live())
    if not live:
        snap.setdefault("generator", False)
        snap.setdefault("generator_card", "Generator: No data")
        return snap
    generator = bool(live.get("generator"))
    transfer = bool(live.get("transfer"))
    source = ""
    delta = live.get("delta") if isinstance(live.get("delta"), dict) else {}
    source = str(delta.get("source") or live.get("ble") or "")
    ble = source.lower() == "ble" or live.get("ble") is True
    detail = _detail(generator=generator, transfer=transfer, raw=str(live.get("detail") or ""))
    if not ble and not generator and not transfer and detail == "idle":
        detail = "ble_gone"
    live["generator"] = generator
    live["transfer"] = transfer
    live["detail"] = detail
    live["ble"] = ble
    live["ble_gone"] = not ble
    live["card"] = desk_card(live)
    snap["generator"] = generator
    snap["transfer"] = transfer
    snap["generator_detail"] = detail
    snap["generator_card"] = live["card"]
    snap["over_ceiling"] = bool(live.get("over_ceiling"))
    snap["caps"] = live.get("caps") if isinstance(live.get("caps"), dict) else snap.get("caps")
    if delta.get("ac_in_w") is not None:
        snap["ac_in_w"] = delta.get("ac_in_w")
    snap["ble_gone"] = not ble
    return snap


def write_ops_guide() -> Path:
    """Dated facts sheet for handoff zips. No serials, no secrets."""
    live = load_live()
    day = datetime.now(HST).strftime("%Y%m%d")
    path = live_path().parent / f"ops-guide-{day}.md"
    card = live.get("card") or desk_card(live) if live else "Generator: No data"
    lines = [
        f"# Ops guide {datetime.now(HST).strftime('%Y-%m-%d')} HST",
        "",
        "Facts only. Not a Settings toggle. Generator charge ceilings are safety nets, not setpoints.",
        "",
        f"- Desk card: {card}",
        f"- DELTA 2 generator AC-in ceiling: {DELTA_GEN_CEILING_W:.0f} W (never higher)",
        f"- RIVER 2 Pro generator AC-in ceiling: {RIVER_GEN_CEILING_W:.0f} W (never higher)",
        "- Lower charge rate is allowed. Starlink stays on Delta AC. 400 W gate switches Delta USB-C only.",
        "- Hybrid GENERATOR insert: once per activation, keyed off generator_detected, not transfer.",
        "- Dual-pack: one fresh pack with unmatched AC-in still classifies generator.",
        "- Public reports are council-published. Owner `/approve` is Cursor code only.",
        "- Discord/Slack live hooks are not wired.",
        "",
    ]
    if live:
        lines.append("```json")
        lines.append(json.dumps(live, indent=2, sort_keys=True))
        lines.append("```")
        lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")
    return path


def format_status(payload: dict[str, Any]) -> str:
    delta = payload.get("delta") if isinstance(payload.get("delta"), dict) else {}
    soc = delta.get("soc")
    ac_in = delta.get("ac_in_w")
    soc_txt = f"{soc:.0f}%" if isinstance(soc, (int, float)) else "No data"
    ac_txt = f"{ac_in:.0f} W AC-in" if isinstance(ac_in, (int, float)) else "No data AC-in"
    if payload.get("over_ceiling"):
        return (
            f"GENERATOR FAULT — AC-in over ceiling. {ac_txt}. "
            "Delta 2 max 750 W, River 2 Pro max 800 W. Lower is allowed. Not a charge target."
        )
    if payload.get("generator"):
        return (
            f"GENERATOR ON — DELTA 2 {soc_txt}, {ac_txt}. "
            "Not transfer. 400 W USB gate holding. Starlink stays on Delta AC. "
            "Ceiling 750 W Delta / 800 W River — not a setpoint."
        )
    return f"Generator off. DELTA 2 {soc_txt}."


def maybe_announce(payload: dict[str, Any]) -> dict[str, Any]:
    """Rising edge only. Repeat after generator drops, then comes back."""
    prior = _load_prior()
    now_on = bool(payload.get("generator"))
    over = bool(payload.get("over_ceiling"))
    detail = str(payload.get("detail") or "")
    if not now_on and detail in {"need_both_fresh", "sticky_until_fresh"}:
        return {"ok": True, "sent": False, "reason": "stale_keep"}
    prior_over = bool(prior.get("over_ceiling"))
    was_on = bool(prior.get("generator"))
    _save_prior({"generator": now_on, "over_ceiling": over, "at": payload.get("at")})
    if over and not prior_over:
        text = format_status(payload)
        sent = False
        try:
            from apps.council import notify

            out = notify.post("bruce", text)
            sent = bool(out.get("ok"))
        except Exception as e:
            log.info("generator ceiling telegram skip: %s", e)
        return {"ok": True, "sent": sent, "reason": "over_ceiling"}
    if not now_on or was_on:
        return {"ok": True, "sent": False, "reason": "not_rising_edge"}
    text = format_status(payload)
    sent = False
    try:
        from apps.council import notify

        out = notify.post("bruce", text)
        sent = bool(out.get("ok"))
        if not sent:
            log.info("generator telegram skip: %s", out.get("detail"))
    except Exception as e:
        log.info("generator telegram skip: %s", e)
    try:
        from apps.core.services.hybrid_reports import update_hybrid_daily_report

        update_hybrid_daily_report()
    except Exception as e:
        log.info("generator hybrid skip: %s", e)
    return {"ok": True, "sent": sent, "reason": "activated"}


def on_gate_update(*, gate: dict, gen: dict) -> dict[str, Any]:
    payload = snapshot(gate=gate, gen=gen)
    write_live(payload)
    ann = maybe_announce(payload)
    return {"ok": True, "live": str(live_path()), **ann}
