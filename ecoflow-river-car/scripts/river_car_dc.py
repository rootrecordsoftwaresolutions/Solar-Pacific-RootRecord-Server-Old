#!/usr/bin/env python3
"""River 2 Pro car/12V DC for external drives. Never switches AC. No backup job."""
from __future__ import annotations

import hashlib
import hmac
import json
import logging
import os
import sys
import time
from pathlib import Path
from typing import Any
from urllib import error, request

OPS = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store"
BLE_SCRIPTS = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "scripts"
AVA = Path.home() / "RootRecord" / "Ava-Core"
sys.path.insert(0, str(OPS))
sys.path.insert(0, str(BLE_SCRIPTS))
sys.path.insert(0, str(AVA))

from ecoflow_ble_store import RIVER_SN  # noqa: E402

log = logging.getLogger("ava.ecoflow.river_car")
DEFAULT_BASE = "https://api-a.ecoflow.com"
STATE_NAME = "river-car-dc.json"
CAR_KEYS = ("pd.carState", "mppt.carState")
PURPOSE = "external-drives"


def _load_env() -> None:
    env = AVA / ".env"
    if not env.is_file():
        return
    for line in env.read_text(encoding="utf-8", errors="replace").splitlines():
        s = line.strip()
        if not s or s.startswith("#") or "=" not in s:
            continue
        k, _, v = s.partition("=")
        k, v = k.strip(), v.strip().strip('"').strip("'")
        if k and k not in os.environ:
            os.environ[k] = v


def state_path() -> Path:
    return OPS / "state" / STATE_NAME


def load_state() -> dict[str, Any]:
    base: dict[str, Any] = {
        "purpose": PURPOSE,
        "wanted": False,
        "last_car_on": None,
        "last_action": None,
        "last_action_at": None,
        "note": "External drives on River car DC. Default off. No backup job yet.",
    }
    path = state_path()
    if not path.is_file():
        return base
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return base
    if isinstance(raw, dict):
        base.update(raw)
    return base


def save_state(state: dict[str, Any]) -> None:
    path = state_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)


def car_command(turn_on: bool) -> dict[str, Any]:
    return {
        "sn": RIVER_SN,
        "moduleType": 5,
        "operateType": "mpptCar",
        "params": {"enabled": 1 if turn_on else 0},
    }


def car_state(data: dict[str, Any]) -> tuple[bool | None, str | None]:
    for key in CAR_KEYS:
        if data.get(key) is None:
            continue
        try:
            return bool(int(data[key])), key
        except (TypeError, ValueError):
            continue
    return None, None


def _flatten(obj: Any, prefix: str = "") -> dict[str, str]:
    out: dict[str, str] = {}
    if isinstance(obj, dict):
        for k, v in obj.items():
            key = f"{prefix}.{k}" if prefix else str(k)
            out.update(_flatten(v, key))
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
    return headers


def _quota_all() -> dict[str, Any]:
    access = (os.getenv("AVA_ECOFLOW_ACCESS_KEY") or "").strip()
    secret = (os.getenv("AVA_ECOFLOW_SECRET_KEY") or "").strip()
    if not access or not secret:
        return {"ok": False, "error": "missing_ecoflow_keys"}
    base = (os.getenv("AVA_ECOFLOW_BASE_URL") or DEFAULT_BASE).strip() or DEFAULT_BASE
    params = {"sn": RIVER_SN}
    nonce = str(int(100000 + (time.time() * 1000) % 900000))
    timestamp = str(int(time.time() * 1000))
    sign_headers = {"accessKey": access, "nonce": nonce, "timestamp": timestamp}
    query = _qstring(params)
    sign_str = f"{query}&{_qstring(sign_headers)}"
    sign = hmac.new(secret.encode(), sign_str.encode(), hashlib.sha256).hexdigest()
    headers = {**sign_headers, "sign": sign, "Accept": "application/json"}
    url = f"{base.rstrip('/')}/iot-open/sign/device/quota/all?{query}"
    req = request.Request(url, headers=headers, method="GET")
    try:
        with request.urlopen(req, timeout=20) as resp:
            parsed = json.loads(resp.read().decode("utf-8", errors="replace") or "{}")
        data = parsed.get("data") or {}
        return {
            "ok": str(parsed.get("code") or "") in {"0", "None", ""} and isinstance(data, dict),
            "data": data,
        }
    except error.HTTPError as e:
        return {"ok": False, "http_status": e.code, "error": str(e)}
    except Exception as e:
        return {"ok": False, "error": str(e)}


def put_car(turn_on: bool) -> dict[str, Any]:
    access = (os.getenv("AVA_ECOFLOW_ACCESS_KEY") or "").strip()
    secret = (os.getenv("AVA_ECOFLOW_SECRET_KEY") or "").strip()
    if not access or not secret:
        return {"ok": False, "error": "missing_ecoflow_keys"}
    base = (os.getenv("AVA_ECOFLOW_BASE_URL") or DEFAULT_BASE).strip() or DEFAULT_BASE
    body = car_command(turn_on)
    if body.get("operateType") != "mpptCar":
        return {"ok": False, "error": "refused_non_car"}
    headers = _sign_headers(access, secret, body)
    raw = json.dumps(body, separators=(",", ":")).encode("utf-8")
    hdrs = dict(headers)
    hdrs["Content-Type"] = "application/json"
    req = request.Request(
        f"{base.rstrip('/')}/iot-open/sign/device/quota",
        data=raw,
        headers=hdrs,
        method="PUT",
    )
    try:
        with request.urlopen(req, timeout=20) as resp:
            parsed = json.loads(resp.read().decode("utf-8", errors="replace") or "{}")
        code = str(parsed.get("code") or "")
        return {
            "ok": code in {"0", "None", ""},
            "code": code,
            "message": parsed.get("message"),
            "action": "on" if turn_on else "off",
        }
    except Exception as e:
        return {"ok": False, "error": str(e), "action": "on" if turn_on else "off"}


def disk_car_on() -> bool | None:
    path = OPS / "quota" / f"{RIVER_SN}.json"
    if not path.is_file():
        return None
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None
    data = ((raw.get("body") or {}).get("data") or raw.get("data") or {})
    if not isinstance(data, dict):
        return None
    on, _ = car_state(data)
    return on


def status(*, live: bool = False) -> dict[str, Any]:
    st = load_state()
    out: dict[str, Any] = {
        "ok": True,
        "purpose": PURPOSE,
        "wanted": bool(st.get("wanted")),
        "disk_car_on": disk_car_on(),
        "state_path": str(state_path()),
        "backup_job": False,
    }
    if live:
        _load_env()
        q = _quota_all()
        data = q.get("data") if q.get("ok") else {}
        on, key = car_state(data if isinstance(data, dict) else {})
        out["live_ok"] = bool(q.get("ok"))
        out["live_car_on"] = on
        out["live_key"] = key
        if on is not None:
            st["last_car_on"] = on
            save_state(st)
    return out


def car_already_on() -> bool | None:
    """True if River car 12V is already on (quota or live). None if unknown."""
    on = disk_car_on()
    if on is True:
        return True
    try:
        _load_env()
        q = _quota_all()
        data = q.get("data") if q.get("ok") else {}
        live, _ = car_state(data if isinstance(data, dict) else {})
        if live is True:
            return True
        if live is False:
            return False
    except Exception:
        pass
    return on


def set_car(*, want_on: bool, execute: bool = False, force: bool = False) -> dict[str, Any]:
    """Turn River car DC. Never AC. execute=False is dry-run.

    Manual always-on: if want_on is False and car was already on and force is
    False, leave it on (camera / drives left powered by the operator).
    """
    _load_env()
    st = load_state()
    already = car_already_on()
    report: dict[str, Any] = {
        "ok": True,
        "wanted": want_on,
        "execute": bool(execute),
        "would": "car_on" if want_on else "car_off",
        "backup_job": False,
        "already_on": already,
    }
    if not want_on and already is True and not force:
        st["wanted"] = True
        st["last_action"] = "leave_on_manual"
        st["last_action_at"] = time.time()
        save_state(st)
        report["action"] = "leave_on_manual"
        report["left_on"] = True
        report["would"] = "leave_on"
        log.info("river car DC leave ON (was already on)")
        return report
    st["wanted"] = bool(want_on)
    st["purpose"] = PURPOSE
    if not execute:
        st["last_action"] = f"dry_run_{'on' if want_on else 'off'}"
        save_state(st)
        report["action"] = st["last_action"]
        return report
    put = put_car(want_on)
    report["put"] = {k: put.get(k) for k in ("ok", "code", "message", "error", "action")}
    if not put.get("ok"):
        report["ok"] = False
        st["last_action"] = f"put_failed_{'on' if want_on else 'off'}"
        st["last_skip_reason"] = put.get("error") or put.get("message")
        save_state(st)
        return report
    time.sleep(2)
    q = _quota_all()
    data = q.get("data") if q.get("ok") else {}
    on, key = car_state(data if isinstance(data, dict) else {})
    report["verify_car_on"] = on
    report["verify_key"] = key
    if on is not None and bool(on) != bool(want_on):
        report["ok"] = False
        report["skipped"] = "car_state_not_verified"
        st["last_action"] = "put_unverified"
        save_state(st)
        return report
    st["last_action"] = "on" if want_on else "off"
    st["last_action_at"] = time.time()
    st["last_car_on"] = on if on is not None else want_on
    st["last_skip_reason"] = None
    save_state(st)
    report["action"] = st["last_action"]
    log.info("river car DC %s (drives/panels)", "ON" if want_on else "OFF")
    return report


def main(argv: list[str] | None = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description="River 2 Pro car DC for external drives (dry-run default).")
    parser.add_argument("--on", action="store_true", help="Want car DC on (drives).")
    parser.add_argument("--off", action="store_true", help="Want car DC off (drives idle).")
    parser.add_argument("--status", action="store_true", help="Read state / quota car flag.")
    parser.add_argument("--live", action="store_true", help="With --status, GET live quota.")
    parser.add_argument("--execute", action="store_true", help="PUT mpptCar. Never PUT AC.")
    args = parser.parse_args(argv)
    if args.on and args.off:
        print(json.dumps({"ok": False, "error": "on_and_off"}))
        return 2
    if args.status or (not args.on and not args.off):
        print(json.dumps(status(live=bool(args.live)), indent=2))
        return 0
    report = set_car(want_on=bool(args.on), execute=bool(args.execute))
    print(json.dumps(report, indent=2))
    return 0 if report.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
