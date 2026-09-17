"""DELTA 2 USB-C vs site solar (MPPT fight with RIVER 2).

Gate on total fresh PV across both packs. Starlink stays on Delta AC —
this gate never toggles AC on either pack. USB-C on Delta feeds River
USB-C (~100 W DC) when solar is under 400 W. River AC stays on.

  - total fresh PV ≥ 400 W → Delta USB OFF
  - total fresh PV < 400 W → Delta USB ON (DC couple into River)
  - generator mode → hold
  - night sleep → enabled false; USB gate holds
"""

from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
import logging
import os
import time
from pathlib import Path
from typing import Any
from urllib import error, request

from apps.core import config
from apps.core.services.data_layout import ecoflow_dir, ensure_data_layout
from apps.core.services.load_categories import pack_power, watts

log = logging.getLogger("ava.ecoflow.ac_solar_gate")

# Desktop notify phrases (Media/public/audio/words/ecoflow/).
# Do not play the old Starlink-AC-off clip; USB-off is not Starlink.
PHRASE_USB_ON = "phrase_ecoflow_ac_on_solar_low"
PHRASE_USB_FAIL = "phrase_ecoflow_ac_change_failed"
PHRASE_GATE_ARMED = "phrase_ecoflow_ac_gate_armed"
PHRASE_GATE_DISABLED = "phrase_ecoflow_ac_gate_disabled"
VOICE_COOLDOWN_S = 90

DELTA_SN = "R331ZAB5SG6S2858"
RIVER_SN = "R621ZA16XH6K1155"
STARLINK_SN = DELTA_SN
DEFAULT_BASE = "https://api-a.ecoflow.com"

OFF_AT_W = 400.0
ON_AT_W = 400.0

# Prefer not flapping around the 2-minute quota cadence.
DEFAULT_COOLDOWN_S = 180
# Match solar_weather ECO_STALE_S — refuse action on stale disk quota.
MAX_QUOTA_AGE_S = 180

# Battery overlay (operator): high SOC keeps AC on for Starlink / house path.
SOC_KEEP_AC_ON_ABOVE = 80.0  # SOC > this → force AC ON
SOC_USUAL_RULES_BELOW = 90.0  # documented band; usual watt rules when SOC ≤ keep threshold

STATE_NAME = "ecoflow-ac-solar-gate.json"
# Primary gate field (evidence in ecoflow-ble-poller/store/quota/{DELTA}.json).
GATE_KEY = "mppt.inWatts"
TOTAL_IN_KEYS = ("pd.wattsInSum", "pd.inWatts")
AC_KEYS = ("pd.acEnabled",)
USB_KEYS = ("pd.dcOutState",)


def state_path() -> Path:
    return ecoflow_dir() / "state" / STATE_NAME


def _env_enabled_override() -> bool | None:
    raw = (os.getenv("AVA_ECOFLOW_AC_SOLAR_GATE") or "").strip().lower()
    if not raw:
        return None
    if raw in {"0", "false", "off", "no", "disabled"}:
        return False
    if raw in {"1", "true", "on", "yes", "enabled"}:
        return True
    return None


def load_state() -> dict[str, Any]:
    path = state_path()
    base: dict[str, Any] = {
        "enabled": True,
        "cooldown_s": DEFAULT_COOLDOWN_S,
        "off_at_w": OFF_AT_W,
        "on_at_w": ON_AT_W,
        "soc_keep_ac_on": False,
        "soc_keep_ac_on_above": SOC_KEEP_AC_ON_ABOVE,
        "soc_usual_rules_below": SOC_USUAL_RULES_BELOW,
        "gate_key": GATE_KEY,
        "sn": DELTA_SN,
        "starlink_sn": STARLINK_SN,
        "last_input_w": None,
        "last_total_in_w": None,
        "last_ac_enabled": None,
        "last_usb_enabled": None,
        "desired_ac": None,
        "desired_usb": None,
        "last_decision": None,
        "last_decision_at": None,
        "last_action": None,
        "last_action_at": None,
        "last_skip_reason": None,
        "starlink_risk_noted": True,
        "note": (
            "DELTA 2 USB-C solar gate. Never switches Delta AC (Starlink) or River AC. "
            "enabled=false disables. Env AVA_ECOFLOW_AC_SOLAR_GATE=0 also disables."
        ),
    }
    if not path.is_file():
        return base
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        log.warning("ac-solar-gate state read failed: %s", e)
        return base
    if isinstance(raw, dict):
        base.update(raw)
    return base


def save_state(state: dict[str, Any]) -> None:
    ensure_data_layout()
    path = state_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)


def _overlay_operator_flags(state: dict[str, Any]) -> None:
    """Keep Desk toggles if quota evaluate started before a save."""
    path = state_path()
    if not path.is_file():
        return
    try:
        disk = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return
    if not isinstance(disk, dict):
        return
    if "enabled" in disk:
        state["enabled"] = bool(disk["enabled"])
    if "soc_keep_ac_on" in disk:
        state["soc_keep_ac_on"] = bool(disk["soc_keep_ac_on"])


def _save_eval(state: dict[str, Any]) -> None:
    _overlay_operator_flags(state)
    save_state(state)
    try:
        from apps.core.services.ecoflow_public import on_gate_update

        on_gate_update(
            gate=state,
            gen={
                "generator": bool(state.get("generator_detected")),
                "transfer": bool(state.get("transfer")),
                "detail": state.get("generator_detail"),
                "delta_ac_in_w": state.get("delta_ac_in_w"),
            },
        )
    except Exception:
        pass


def _quota_path(sn: str = DELTA_SN) -> Path:
    return ecoflow_dir() / "quota" / f"{sn}.json"


def read_delta_quota() -> dict[str, Any]:
    """Load Delta quota from disk. Returns measured fields + age."""
    path = _quota_path()
    if not path.is_file():
        return {"ok": False, "error": "quota_missing", "path": str(path)}
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        return {"ok": False, "error": str(e), "path": str(path)}
    at = int(raw.get("at") or 0)
    at_s = at / 1000.0 if at > 1_000_000_000_000 else float(at or 0)
    age_s = int(time.time() - at_s) if at_s else None
    data = (raw.get("body") or {}).get("data") or {}
    if not isinstance(data, dict):
        data = {}
    pwr = pack_power(data)
    # Gate = PV into Delta MPPT (mppt.inWatts). Same as pack_power pv_w.
    input_w = float(pwr.get("pv_w") or 0.0)
    total_in = watts(data.get("pd.wattsInSum"))
    if total_in <= 0:
        total_in = watts(data.get("pd.inWatts"))
    ac_raw = None
    for k in AC_KEYS:
        if data.get(k) is not None:
            ac_raw = data.get(k)
            break
    try:
        ac_on = bool(int(ac_raw)) if ac_raw is not None else None
    except (TypeError, ValueError):
        ac_on = None
    usb_raw = data.get("pd.dcOutState")
    try:
        usb_on = bool(int(usb_raw)) if usb_raw is not None else None
    except (TypeError, ValueError):
        usb_on = None
    return {
        "ok": True,
        "path": str(path),
        "age_s": age_s,
        "fresh": age_s is not None and age_s <= MAX_QUOTA_AGE_S,
        "gate_key": GATE_KEY,
        "input_w": input_w,
        "mppt.inWatts": data.get("mppt.inWatts"),
        "total_in_w": total_in,
        "pd.wattsInSum": data.get("pd.wattsInSum"),
        "pd.inWatts": data.get("pd.inWatts"),
        "ac_on": ac_on,
        "usb_on": usb_on,
        "ac_raw": ac_raw,
        "inv.cfgAcEnabled": data.get("inv.cfgAcEnabled"),
        "pd.acEnabled": data.get("pd.acEnabled"),
        "pd.dcOutState": data.get("pd.dcOutState"),
        "soc": data.get("bms_bmsStatus.soc") or data.get("pd.soc"),
    }


def decide(input_w: float, *, off_at: float = OFF_AT_W, on_at: float = ON_AT_W) -> str | None:
    """Return ``on``, ``off``, or None (dead band). Watt hysteresis only."""
    w = float(input_w or 0.0)
    if w >= float(off_at):
        return "off"
    if w <= float(on_at):
        return "on"
    return None


def _soc_float(raw: Any) -> float | None:
    try:
        if raw is None or raw == "":
            return None
        return float(raw)
    except (TypeError, ValueError):
        return None


def decide_with_soc(
    input_w: float,
    soc: float | None,
    *,
    off_at: float = OFF_AT_W,
    on_at: float = ON_AT_W,
    soc_keep_above: float = SOC_KEEP_AC_ON_ABOVE,
    soc_keep_on: bool = True,
) -> tuple[str | None, str | None]:
    """Watt decide, then SOC overlay.

    Returns (desired, reason). reason is ``soc_keep_on`` when battery forces ON.
    When SOC is over ``soc_keep_above``, USB stays ON (overrides watt OFF / dead band).
    When SOC is at or below that band (and under the documented 90% usual line),
    watt rules alone apply.
    """
    watt = decide(input_w, off_at=off_at, on_at=on_at)
    if soc_keep_on and soc is not None and soc > float(soc_keep_above):
        return "on", "soc_keep_on"
    return watt, None


def phrase_for_action(action: str | None) -> str | None:
    """Map a gate action to a whole-phrase clip id (or None = silence)."""
    if action == "put_usb_on":
        return PHRASE_USB_ON
    if isinstance(action, str) and action.startswith("put_failed_"):
        return PHRASE_USB_FAIL
    return None


def _enqueue_announce(phrase: str) -> None:
    """Fire-and-forget REPORT announce so the quota cron is not held on audio."""
    name = (phrase or "").strip()
    if not name:
        return

    async def _run() -> None:
        try:
            from apps.core.services import voice_events

            await voice_events.announce(name, cooldown_s=VOICE_COOLDOWN_S, priority="REPORT")
        except Exception as e:
            log.debug("ac-solar-gate voice skip: %s", e)

    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        # CLI / no loop — skip desk play; cron path always has a loop.
        log.debug("ac-solar-gate voice skip (no event loop): %s", name)
        return
    loop.create_task(_run())


def _flatten(obj: Any, prefix: str = "") -> dict[str, str]:
    out: dict[str, str] = {}
    if isinstance(obj, dict):
        for k, v in obj.items():
            key = f"{prefix}.{k}" if prefix else str(k)
            out.update(_flatten(v, key))
    elif isinstance(obj, list):
        for i, item in enumerate(obj):
            key = f"{prefix}[{i}]"
            out.update(_flatten(item, key))
    else:
        out[prefix] = str(obj)
    return out


def _qstring(params: dict[str, str]) -> str:
    return "&".join(f"{k}={params[k]}" for k in sorted(params))


def _sign_headers(access_key: str, secret_key: str, body: dict | None) -> dict[str, str]:
    nonce = str(int(100000 + (time.time() * 1000) % 900000))
    timestamp = str(int(time.time() * 1000))
    headers = {"accessKey": access_key, "nonce": nonce, "timestamp": timestamp}
    flat = _flatten(body) if body else {}
    sign_str = (_qstring(flat) + "&" if flat else "") + _qstring(headers)
    sig = hmac.new(secret_key.encode(), sign_str.encode(), hashlib.sha256).hexdigest()
    headers["sign"] = sig
    headers["Accept"] = "application/json"
    headers["User-Agent"] = "AvaIvy/2.0 (EcoFlow AC solar gate)"
    return headers


def _get_live_quota() -> dict[str, Any]:
    access = (os.getenv("AVA_ECOFLOW_ACCESS_KEY") or "").strip()
    secret = (os.getenv("AVA_ECOFLOW_SECRET_KEY") or "").strip()
    if not access or not secret:
        return {"ok": False, "error": "missing_ecoflow_keys"}
    base = (os.getenv("AVA_ECOFLOW_BASE_URL") or DEFAULT_BASE).strip() or DEFAULT_BASE
    params = {"sn": DELTA_SN}
    nonce = str(int(100000 + (time.time() * 1000) % 900000))
    timestamp = str(int(time.time() * 1000))
    sign_headers = {"accessKey": access, "nonce": nonce, "timestamp": timestamp}
    query = _qstring(params)
    sign_str = f"{query}&{_qstring(sign_headers)}"
    sign = hmac.new(secret.encode(), sign_str.encode(), hashlib.sha256).hexdigest()
    headers = {
        **sign_headers,
        "sign": sign,
        "Accept": "application/json",
        "User-Agent": "AvaIvy/2.0 (EcoFlow AC state verify)",
    }
    url = f"{base.rstrip('/')}/iot-open/sign/device/quota/all?{query}"
    req = request.Request(url, headers=headers, method="GET")
    try:
        with request.urlopen(req, timeout=20) as resp:
            parsed = json.loads(resp.read().decode("utf-8", errors="replace") or "{}")
        code = str(parsed.get("code") or "")
        data = parsed.get("data") or {}
        return {
            "ok": code in {"0", "None", ""} and isinstance(data, dict),
            "http_status": getattr(resp, "status", 200),
            "code": code,
            "data": data,
        }
    except error.HTTPError as e:
        return {"ok": False, "http_status": e.code, "error": str(e)}
    except Exception as e:
        return {"ok": False, "error": str(e)}


def _usb_state(data: dict[str, Any]) -> tuple[bool | None, str | None]:
    for key in USB_KEYS:
        if data.get(key) is None:
            continue
        try:
            return bool(int(data[key])), key
        except (TypeError, ValueError):
            continue
    return None, None


def _verify_usb_state(want_on: bool, *, attempts: int = 3, delay_s: float = 2.0) -> dict[str, Any]:
    observations: list[dict[str, Any]] = []
    for attempt in range(attempts):
        live = _get_live_quota()
        data = live.get("data") or {}
        usb_on, usb_key = _usb_state(data)
        observations.append({"attempt": attempt + 1, "usb_on": usb_on, "usb_key": usb_key})
        if usb_on is not None and usb_on == want_on:
            return {"ok": True, "usb_on": usb_on, "usb_key": usb_key, "observations": observations}
        if attempt + 1 < attempts:
            time.sleep(delay_s)
    return {"ok": False, "error": "usb_state_not_verified", "observations": observations}


def _usb_command(turn_on: bool) -> dict[str, Any]:
    return {
        "sn": DELTA_SN,
        "moduleType": 1,
        "operateType": "dcOutCfg",
        "params": {"enabled": 1 if turn_on else 0},
    }


def _put_usb(turn_on: bool) -> dict[str, Any]:
    access = (os.getenv("AVA_ECOFLOW_ACCESS_KEY") or "").strip()
    secret = (os.getenv("AVA_ECOFLOW_SECRET_KEY") or "").strip()
    if not access or not secret:
        return {"ok": False, "error": "missing_ecoflow_keys"}
    base = (os.getenv("AVA_ECOFLOW_BASE_URL") or DEFAULT_BASE).strip() or DEFAULT_BASE
    url = f"{base.rstrip('/')}/iot-open/sign/device/quota"
    body = _usb_command(turn_on)
    headers = _sign_headers(access, secret, body)
    data = json.dumps(body, separators=(",", ":")).encode("utf-8")
    hdrs = dict(headers)
    hdrs["Content-Type"] = "application/json"
    req = request.Request(url, data=data, headers=hdrs, method="PUT")
    try:
        with request.urlopen(req, timeout=20) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            try:
                parsed = json.loads(raw) if raw else {}
            except json.JSONDecodeError:
                parsed = {"_raw": raw[:500]}
            code = str(parsed.get("code") or "")
            ok = code in {"0", "None", ""}
            return {
                "ok": ok,
                "http_status": getattr(resp, "status", 200),
                "code": code,
                "message": parsed.get("message"),
                "action": "on" if turn_on else "off",
            }
    except error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"_raw": raw[:500]}
        return {
            "ok": False,
            "http_status": e.code,
            "error": str(e),
            "message": parsed.get("message"),
            "action": "on" if turn_on else "off",
        }
    except Exception as e:
        return {"ok": False, "error": str(e), "action": "on" if turn_on else "off"}


def evaluate(*, execute: bool = False) -> dict[str, Any]:
    """Apply hysteresis against the newest on-disk Delta quota.

    ``execute=False`` → would-do only (still updates decision fields in state).
    ``execute=True`` → PUT USB when a change is required and safety checks pass.
    Never PUT AC.
    """
    now = time.time()
    state = load_state()
    env_en = _env_enabled_override()
    enabled = bool(state.get("enabled", True)) if env_en is None else env_en
    off_at = float(state.get("off_at_w") or OFF_AT_W)
    on_at = float(state.get("on_at_w") or ON_AT_W)
    soc_keep = float(state.get("soc_keep_ac_on_above") or SOC_KEEP_AC_ON_ABOVE)
    soc_usual = float(state.get("soc_usual_rules_below") or SOC_USUAL_RULES_BELOW)
    cooldown_s = int(state.get("cooldown_s") or DEFAULT_COOLDOWN_S)

    quota = read_delta_quota()
    report: dict[str, Any] = {
        "ok": True,
        "enabled": enabled,
        "execute": bool(execute),
        "gate_key": GATE_KEY,
        "rules": {
            "off_at_w": off_at,
            "on_at_w": on_at,
            "dead_band": [on_at, off_at],
            "soc_keep_ac_on_above": soc_keep,
            "soc_usual_rules_below": soc_usual,
        },
        "cooldown_s": cooldown_s,
        "quota": {
            k: quota.get(k)
            for k in (
                "ok",
                "age_s",
                "fresh",
                "input_w",
                "total_in_w",
                "ac_on",
                "usb_on",
                "mppt.inWatts",
                "pd.wattsInSum",
                "soc",
            )
        },
        "decision": None,
        "decision_reason": None,
        "would": None,
        "action": None,
        "skipped": None,
        "put": None,
        "verify": None,
        "state_path": str(state_path()),
    }

    if not quota.get("ok"):
        report["ok"] = False
        report["skipped"] = quota.get("error") or "quota_unreadable"
        state["last_skip_reason"] = report["skipped"]
        state["last_decision"] = "error"
        state["last_decision_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
        _save_eval(state)
        return report

    input_w = float(quota.get("input_w") or 0.0)
    total_in = float(quota.get("total_in_w") or 0.0)
    ac_on = quota.get("ac_on")
    usb_on = quota.get("usb_on")
    soc = _soc_float(quota.get("soc"))
    store = None
    try:
        import sys

        ops = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store"
        if str(ops) not in sys.path:
            sys.path.insert(0, str(ops))
        import ecoflow_ble_store as store
    except Exception:
        store = None
    total_pv = input_w
    generator = False
    transfer = False
    if store is not None:
        d_raw = {}
        r_raw = {}
        try:
            d_raw = json.loads((ecoflow_dir() / "quota" / f"{DELTA_SN}.json").read_text(encoding="utf-8"))
        except Exception:
            d_raw = {}
        try:
            r_raw = json.loads((ecoflow_dir() / "quota" / f"{RIVER_SN}.json").read_text(encoding="utf-8"))
        except Exception:
            r_raw = {}
        pv = store.total_fresh_pv(d_raw, r_raw)
        gen = store.classify_generator(d_raw, r_raw)
        if pv.get("ok"):
            total_pv = float(pv.get("total_w") or 0.0)
        generator = bool(gen.get("generator"))
        transfer = bool(gen.get("transfer"))
        night = False
        try:
            nm = json.loads((ecoflow_dir() / "state" / "night-mode.json").read_text(encoding="utf-8"))
            night = bool(nm.get("sleeping"))
        except Exception:
            night = False
        hold = False
        try:
            inhibit = ecoflow_dir() / "state" / "night-reboot-inhibit"
            if inhibit.is_file():
                hold = True
            elif (os.getenv("AVA_SKIP_NIGHT_REBOOT") or "").strip().lower() in {
                "1",
                "true",
                "yes",
            }:
                hold = True
            else:
                import subprocess

                hold = subprocess.run(
                    ["pgrep", "-f", "cursor"],
                    capture_output=True,
                    timeout=3,
                ).returncode == 0
        except Exception:
            hold = False
        now_s = time.time()
        zero_s = 0.0
        pv_for_want = total_pv if pv.get("ok") else None
        if pv.get("ok") and pv_for_want is not None:
            stopped_w = float(getattr(store, "PV_STOPPED_W", 1.0))
            if float(pv_for_want) < stopped_w:
                since = state.get("pv_zero_since")
                try:
                    since_f = float(since) if since else now_s
                except (TypeError, ValueError):
                    since_f = now_s
                if not since:
                    since_f = now_s
                state["pv_zero_since"] = since_f
                zero_s = max(0.0, now_s - since_f)
            else:
                state["pv_zero_since"] = None
                zero_s = 0.0
        else:
            try:
                zero_s = float(state.get("pv_zero_for_s") or 0)
            except (TypeError, ValueError):
                zero_s = 0.0
        state["pv_zero_for_s"] = round(zero_s, 1)
        state["operator_hold"] = hold
        want = store.want_delta_usb_on(
            pv_for_want,
            sleeping=night,
            generator=generator,
            delta_soc=soc,
            pv_zero_for_s=zero_s,
            operator_hold=hold,
        )
        if want is True:
            desired, soc_reason = "on", "total_pv_lt_400"
        elif want is False:
            min_soc = float(getattr(store, "DELTA_USB_MIN_SOC", getattr(store, "DELTA_AC_MIN_SOC", 3.0)))
            night_off_s = float(getattr(store, "USB_NIGHT_OFF_S", 300.0))
            if night:
                desired, soc_reason = "off", "usb_night_sleep"
            elif zero_s >= night_off_s:
                desired, soc_reason = "off", "usb_night_off_pv_zero"
            elif soc is not None and float(soc) < min_soc:
                desired, soc_reason = "off", "delta_soc_lt_3"
            else:
                desired, soc_reason = "off", "total_pv_gte_400"
        else:
            if hold and (night or zero_s >= float(getattr(store, "USB_NIGHT_OFF_S", 300.0))):
                desired, soc_reason = None, "operator_hold_usb"
            else:
                desired, soc_reason = None, ("generator" if generator else "hold")
    else:
        desired, soc_reason = decide(total_pv, off_at=off_at, on_at=on_at), None

    prev_usb = state.get("last_usb_enabled")
    state["last_input_w"] = total_pv
    state["last_total_in_w"] = total_in
    state["last_soc"] = soc
    state["generator_detected"] = generator
    state["transfer"] = transfer
    state["starlink_sn"] = STARLINK_SN
    state["total_pv_w"] = total_pv
    state["last_ac_enabled"] = 1 if ac_on else (0 if ac_on is False else None)
    state["last_usb_enabled"] = 1 if usb_on else (0 if usb_on is False else None)
    if usb_on is True:
        state["manual_usb_off"] = False
    elif (
        prev_usb == 1
        and usb_on is False
        and str(state.get("last_action") or "") != "off"
    ):
        state["manual_usb_off"] = True
    state["desired_usb"] = desired
    state["desired_ac"] = None
    state["decision_reason"] = soc_reason or ("watt_" + (desired or "hold"))
    state["soc_keep_ac_on"] = False
    state["soc_keep_ac_on_above"] = soc_keep
    state["soc_usual_rules_below"] = soc_usual
    state["gate_key"] = GATE_KEY
    state["last_decision_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")

    report["decision"] = desired
    report["decision_reason"] = state["decision_reason"]
    report["soc"] = soc
    if desired is None:
        report["would"] = "hold_dead_band"
        state["last_decision"] = "hold_dead_band"
        state["last_skip_reason"] = None
        _save_eval(state)
        log.info(
            "ac-solar-gate hold dead-band input=%.0fW total_in=%.0fW soc=%s usb=%s ac=%s",
            input_w,
            total_in,
            soc,
            usb_on,
            ac_on,
        )
        return report

    want_on = desired == "on"
    report["would"] = f"usb_{desired}"

    if not enabled:
        report["skipped"] = "disabled"
        state["last_decision"] = f"would_{desired}_disabled"
        state["last_skip_reason"] = "disabled"
        _save_eval(state)
        log.info(
            "ac-solar-gate disabled would=%s input=%.0fW usb=%s",
            desired,
            input_w,
            usb_on,
        )
        return report

    ble_owns = False
    try:
        raw_d = json.loads((ecoflow_dir() / "quota" / f"{DELTA_SN}.json").read_text(encoding="utf-8"))
        src = str(raw_d.get("source") or "").lower()
        at = int(raw_d.get("at") or 0)
        at_s = at / 1000.0 if at > 1_000_000_000_000 else float(at or 0)
        ble_owns = src == "ble" and at_s and (time.time() - at_s) <= 90
    except Exception:
        ble_owns = False
    if ble_owns:
        report["skipped"] = "ble_owns_delta_usb"
        state["last_decision"] = f"would_{desired}_ble"
        state["last_skip_reason"] = report["skipped"]
        _save_eval(state)
        return report

    if not quota.get("fresh"):
        report["skipped"] = f"stale_quota_age_s={quota.get('age_s')}"
        state["last_decision"] = f"would_{desired}_stale"
        state["last_skip_reason"] = report["skipped"]
        _save_eval(state)
        log.info("ac-solar-gate skip stale age=%s would=%s", quota.get("age_s"), desired)
        return report

    if usb_on is None:
        report["skipped"] = "usb_state_unknown"
        state["last_decision"] = f"would_{desired}_unknown_usb"
        state["last_skip_reason"] = report["skipped"]
        _save_eval(state)
        return report

    if bool(usb_on) == want_on:
        report["would"] = f"already_usb_{desired}"
        report["action"] = "noop_satisfied"
        state["last_decision"] = f"satisfied_{desired}"
        state["last_skip_reason"] = None
        _save_eval(state)
        log.info(
            "ac-solar-gate satisfied usb=%s input=%.0fW (gate %s)",
            desired,
            input_w,
            GATE_KEY,
        )
        return report

    honor_off = want_on and not bool(usb_on) and bool(state.get("manual_usb_off"))
    if honor_off:
        report["skipped"] = "honor_usb_off"
        report["would"] = "usb_on"
        state["last_decision"] = "honor_usb_off"
        state["last_skip_reason"] = "honor_usb_off"
        _save_eval(state)
        log.info(
            "ac-solar-gate honor USB off reason=%s input=%.0fW soc=%s",
            soc_reason,
            input_w,
            soc,
        )
        return report

    last_at = state.get("last_action_at")
    if last_at:
        try:
            # ISO or epoch
            if isinstance(last_at, (int, float)):
                last_ts = float(last_at)
            else:
                # accept epoch-as-string or skip parse failures
                last_ts = float(last_at) if str(last_at).replace(".", "", 1).isdigit() else 0.0
                if last_ts <= 0 and isinstance(last_at, str) and "T" in last_at:
                    from datetime import datetime

                    last_ts = datetime.fromisoformat(last_at).timestamp()
        except Exception:
            last_ts = 0.0
        if last_ts and (now - last_ts) < cooldown_s:
            left = int(cooldown_s - (now - last_ts))
            report["skipped"] = f"cooldown_{left}s"
            state["last_decision"] = f"would_{desired}_cooldown"
            state["last_skip_reason"] = report["skipped"]
            _save_eval(state)
            log.info("ac-solar-gate cooldown %ss left would=%s", left, desired)
            return report

    if not execute:
        report["action"] = f"dry_run_would_usb_{desired}"
        state["last_decision"] = f"dry_run_{desired}"
        state["last_skip_reason"] = None
        _save_eval(state)
        log.info(
            "ac-solar-gate dry-run would USB %s input=%.0fW usb_now=%s",
            desired.upper(),
            input_w,
            usb_on,
        )
        return report

    _overlay_operator_flags(state)
    enabled = bool(state.get("enabled", True)) if env_en is None else env_en
    if not enabled:
        report["skipped"] = "disabled"
        report["enabled"] = False
        state["last_decision"] = f"would_{desired}_disabled"
        state["last_skip_reason"] = "disabled"
        _save_eval(state)
        log.info(
            "ac-solar-gate disabled before PUT would=%s input=%.0fW usb=%s",
            desired,
            input_w,
            usb_on,
        )
        return report

    # Live PUT — Delta USB-C only. Never Delta AC (Starlink) or River AC.
    put = _put_usb(want_on)
    report["put"] = {
        k: put.get(k)
        for k in ("ok", "http_status", "code", "message", "error", "action")
    }
    if put.get("ok"):
        verify = _verify_usb_state(want_on)
        report["verify"] = verify
        if not verify.get("ok"):
            report["ok"] = False
            report["action"] = f"put_unverified_{desired}"
            report["skipped"] = "usb_state_not_verified"
            state["last_decision"] = f"put_unverified_{desired}"
            state["last_skip_reason"] = "usb_state_not_verified"
            log.error(
                "ac-solar-gate PUT USB %s accepted but state was not verified: %s",
                desired.upper(),
                verify,
            )
        else:
            report["action"] = f"put_usb_{desired}"
            state["last_action"] = desired
            state["last_action_at"] = now
            state["last_decision"] = f"put_{desired}"
            state["last_skip_reason"] = None
            try:
                from apps.core.services.hybrid_reports import append_ecoflow_automation_event

                event = "TRANSFER STARTED" if desired == "on" else "TRANSFER STOPPED"
                report["report_event"] = append_ecoflow_automation_event(event)
            except Exception as e:
                log.warning("EcoFlow report event skipped: %s", e)
            log.warning(
                "ac-solar-gate PUT Delta USB %s verified input=%.0fW (Starlink stays on Delta AC)",
                desired.upper(),
                input_w,
            )
    else:
        report["ok"] = False
        report["action"] = f"put_failed_{desired}"
        report["skipped"] = put.get("error") or put.get("message") or "put_failed"
        state["last_decision"] = f"put_failed_{desired}"
        state["last_skip_reason"] = report["skipped"]
        log.error("ac-solar-gate PUT failed: %s", put)
    phrase = phrase_for_action(report.get("action"))
    report["announce"] = phrase
    if phrase:
        _enqueue_announce(phrase)
    _save_eval(state)
    return report


async def run_after_quota(*, execute: bool = True) -> dict[str, Any]:
    """Called from ecoflow-quota after a fresh live_snapshot write."""
    return evaluate(execute=execute)


def operator_status() -> dict[str, Any]:
    st = load_state()
    return {
        "ok": True,
        "enabled": bool(st.get("enabled", True)),
        "soc_keep_ac_on": bool(st.get("soc_keep_ac_on", True)),
        "soc_keep_ac_on_above": st.get("soc_keep_ac_on_above") or SOC_KEEP_AC_ON_ABOVE,
        "last_decision": st.get("last_decision"),
        "last_soc": st.get("last_soc"),
        "decision_reason": st.get("decision_reason"),
        "last_skip_reason": st.get("last_skip_reason"),
    }


def patch_operator(*, enabled: bool | None = None, soc_keep_ac_on: bool | None = None) -> dict[str, Any]:
    st = load_state()
    if enabled is not None:
        st["enabled"] = bool(enabled)
    if soc_keep_ac_on is not None:
        st["soc_keep_ac_on"] = bool(soc_keep_ac_on)
    save_state(st)
    return operator_status()


def main(argv: list[str] | None = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description="DELTA 2 USB-C solar gate (dry-run default).")
    parser.add_argument(
        "--execute",
        action="store_true",
        help="PUT USB when hysteresis requires a change (cron uses this). Never PUT AC.",
    )
    parser.add_argument(
        "--disable",
        action="store_true",
        help="Write enabled=false to state and exit.",
    )
    parser.add_argument(
        "--enable",
        action="store_true",
        help="Write enabled=true to state and exit.",
    )
    args = parser.parse_args(argv)
    if args.disable or args.enable:
        st = load_state()
        st["enabled"] = bool(args.enable) and not args.disable
        save_state(st)
        phrase = PHRASE_GATE_ARMED if st["enabled"] else PHRASE_GATE_DISABLED
        out = {
            "ok": True,
            "enabled": st["enabled"],
            "path": str(state_path()),
            "announce": phrase,
        }
        print(json.dumps(out, indent=2))
        return 0
    report = evaluate(execute=bool(args.execute))
    print(json.dumps(report, indent=2))
    return 0 if report.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
