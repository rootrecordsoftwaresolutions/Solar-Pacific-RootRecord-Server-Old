#!/usr/bin/env python3
"""Delta 2 + River 2 Pro BLE-primary quota, persist, 400 W USB-C gate.

Starlink stays on Delta AC (never switched). USB-C on Delta feeds River
USB-C (~100 W DC) when total fresh PV is under 400 W. River AC stays on
for the laptop. Overnight poll 5 min. Daytime 10 s.
Do not invent SOC/watts. No sample without a live BLE or API read.
"""
from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
import logging
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

OPS = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store"
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(OPS / "vendor"))
sys.path.insert(0, str(HERE))

from ecoflow_ble_store import (  # noqa: E402
    DELTA_SN,
    RIVER_SN,
    STARLINK_SN,
    DELTA_USB_MIN_SOC,
    USB_NIGHT_OFF_S,
    PV_STOPPED_W,
    persist_live_snapshot,
    session_event,
    total_fresh_pv,
    classify_generator,
    want_delta_usb_on,
    unlock_quota,
    quota_path,
    skip_cloud_stomp,
    soc_of,
    quota_data,
)

DAY_WRITE_S = 10.0
NIGHT_WRITE_S = 300.0
TICK_S = 10.0
SCAN_S = 8.0
HST = ZoneInfo("Pacific/Honolulu")
SUN_FALLBACK = (6, 8)
AVA_HOME = Path.home() / "RootRecord" / "Ava-Core"
STORE = Path.home() / ".ollama" / "skills" / "database" / "store"
AVA_SUN = Path.home() / ".ollama" / "skills" / "state" / "store" / "sun-times.json"
IDLE_STOP = Path.home() / ".ollama" / "skills" / "idle-stop" / "scripts" / "idle-stop.sh"
LAUNCH_SH = Path.home() / ".ollama" / "skills" / "launch" / "scripts" / "launch.sh"
DEFAULT_DELTA_MAC = "24:58:7C:20:92:61"
DEFAULT_RIVER_MAC = "DC:06:75:56:AC:1D"
OPEN_METEO = (
    "https://api.open-meteo.com/v1/forecast"
    "?latitude=19.43&longitude=-155.23"
    "&daily=sunrise,sunset&timezone=Pacific/Honolulu&forecast_days=2"
)

log = logging.getLogger("ecoflow-ble")
_live_devices: dict[str, object] = {}
_scan_lock = asyncio.Lock()
_connect_lock = asyncio.Lock()
_scan_cache: dict = {"at": 0.0, "seen": {}}
SCAN_CACHE_S = 12.0


def _load_ava_env() -> None:
    env = AVA_HOME / ".env"
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


def _now() -> datetime:
    return datetime.now(HST)


def _hhmm(value: str) -> tuple[int, int] | None:
    raw = str(value or "").strip()
    if ":" not in raw:
        return None
    hh, mm = raw.split(":", 1)
    try:
        h, m = int(hh), int(mm[:2])
    except ValueError:
        return None
    if not (0 <= h <= 23 and 0 <= m <= 59):
        return None
    return h, m


def _read_json(path: Path) -> dict:
    if not path.is_file():
        return {}
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return {}
    return raw if isinstance(raw, dict) else {}


def _write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    tmp.replace(path)


def _state_path() -> Path:
    p = OPS / "state"
    p.mkdir(parents=True, exist_ok=True)
    return p / "night-mode.json"


def _sun_cache_path() -> Path:
    p = OPS / "state"
    p.mkdir(parents=True, exist_ok=True)
    return p / "sun-times.json"


def _gate_path() -> Path:
    return OPS / "state" / "ecoflow-ac-solar-gate.json"


def _inhibit_path() -> Path:
    return OPS / "state" / "night-reboot-inhibit"


def _sunrise_flag_path() -> Path:
    return OPS / "state" / "sunrise-restore.json"


def _merge_sun(*docs: dict) -> dict:
    today = _now().strftime("%Y-%m-%d")
    for d in docs:
        if d.get("date") == today and d.get("sunrise"):
            return d
    for d in docs:
        if d.get("sunrise"):
            return d
    return {}


def refresh_sun(*, online_ok: bool = True) -> dict:
    cached = _read_json(_sun_cache_path())
    ava = _read_json(AVA_SUN)
    best = _merge_sun(ava, cached)
    today = _now().strftime("%Y-%m-%d")
    evening = _now().hour >= 18
    want_tomorrow = evening and str(best.get("next_date") or "") != (
        _now() + timedelta(days=1)
    ).strftime("%Y-%m-%d")
    need = best.get("date") != today or not best.get("sunrise")
    if (need or want_tomorrow) and online_ok:
        try:
            with urllib.request.urlopen(OPEN_METEO, timeout=12) as r:
                daily = (json.loads(r.read().decode()) or {}).get("daily") or {}
            rises = daily.get("sunrise") or []
            sets = daily.get("sunset") or []
            if rises:
                payload = {
                    "date": today,
                    "sunrise": str(rises[0]).split("T", 1)[-1][:5],
                    "sunset": str((sets[0] if sets else "")).split("T", 1)[-1][:5],
                    "sunrise_iso": rises[0],
                    "source": "open-meteo",
                }
                if len(rises) > 1:
                    payload["next_date"] = (_now() + timedelta(days=1)).strftime("%Y-%m-%d")
                    payload["next_sunrise"] = str(rises[1]).split("T", 1)[-1][:5]
                    payload["next_sunrise_iso"] = rises[1]
                _write_json(_sun_cache_path(), payload)
                try:
                    AVA_SUN.parent.mkdir(parents=True, exist_ok=True)
                    _write_json(AVA_SUN, payload)
                except OSError as e:
                    log.info("ava sun-times write skipped: %s", e)
                best = payload
        except Exception as e:
            log.info("sun fetch skipped: %s", e)
    if best:
        _write_json(_sun_cache_path(), {**cached, **best})
    return best


def sunrise_hhmm() -> tuple[int, int]:
    sun = refresh_sun(online_ok=True)
    today = _now().strftime("%Y-%m-%d")
    if sun.get("date") == today:
        parsed = _hhmm(str(sun.get("sunrise") or ""))
        if parsed:
            return parsed
    if sun.get("next_date") == today:
        parsed = _hhmm(str(sun.get("next_sunrise") or ""))
        if parsed:
            return parsed
    return SUN_FALLBACK


def sleep_start_hhmm() -> tuple[int, int]:
    env = (os.getenv("AVA_ECOFLOW_SLEEP_START") or "").strip()
    parsed = _hhmm(env)
    if parsed:
        return parsed
    parsed = _hhmm(str(_read_json(_state_path()).get("midnight_off") or ""))
    if parsed:
        return parsed
    return (0, 0)


def in_starlink_sleep(now: datetime | None = None) -> bool:
    now = now or _now()
    sh, sm = sleep_start_hhmm()
    h, m = sunrise_hhmm()
    rise = now.replace(hour=h, minute=m, second=0, microsecond=0)
    start = now.replace(hour=sh, minute=sm, second=0, microsecond=0)
    return start <= now < rise


def internet_up(*, timeout: float = 4.0) -> bool:
    try:
        urllib.request.urlopen("https://1.1.1.1", timeout=timeout)
        return True
    except Exception:
        try:
            urllib.request.urlopen(OPEN_METEO, timeout=timeout)
            return True
        except Exception:
            return False


def set_power_profile(name: str) -> None:
    try:
        subprocess.run(
            ["powerprofilesctl", "set", name],
            check=False,
            timeout=5,
            capture_output=True,
        )
        log.info("power profile %s", name)
    except Exception as e:
        log.info("power profile skip: %s", e)


def operator_present() -> bool:
    if _inhibit_path().is_file():
        return True
    if os.getenv("AVA_SKIP_NIGHT_REBOOT", "").strip() in {"1", "true", "yes"}:
        return True
    try:
        r = subprocess.run(
            ["pgrep", "-f", "cursor"],
            capture_output=True,
            timeout=3,
        )
        if r.returncode == 0:
            return True
    except Exception:
        pass
    return False


def _g(device, name, default=None):
    return getattr(device, name, default)


def _map_pack(device, *, model: str) -> dict:
    soc = _g(device, "battery_level")
    soc_main = _g(device, "battery_level_main")
    ac_on = bool(_g(device, "ac_ports"))
    usb_on = bool(_g(device, "usb_ports", True))
    car_on = bool(_g(device, "dc_12v_port"))
    out_w = _g(device, "output_power") or 0
    in_w = _g(device, "input_power") or 0
    ac_out = _g(device, "ac_output_power") or 0
    ac_in = _g(device, "ac_input_power") or 0
    usb_c = _g(device, "usbc_output_power") or 0
    usb_a = _g(device, "usba_output_power") or 0
    solar = _g(device, "xt60_input_power")
    if solar is None:
        solar = _g(device, "solar_input_power") or 0
    temp = _g(device, "cell_temperature")
    volts = _g(device, "battery_voltage")
    dsg = _g(device, "remaining_time_discharging")
    chg = _g(device, "remaining_time_charging")
    data = {
        "_ava_source": "ble",
        "_ava_model": model,
        "pd.soc": soc,
        "bms_bmsStatus.soc": soc,
        "bms_bmsStatus.f32ShowSoc": soc,
        "bms_emsStatus.f32LcdShowSoc": soc_main if soc_main is not None else soc,
        "pd.wattsOutSum": out_w,
        "pd.wattsInSum": in_w,
        "inv.outputWatts": ac_out,
        "inv.inputWatts": ac_in,
        "inv.cfgAcEnabled": 1 if ac_on else 0,
        "pd.acEnabled": 1 if ac_on else 0,
        "mppt.inWatts": solar,
        "pd.typec1Watts": usb_c,
        "pd.usb1Watts": usb_a,
        "pd.dcOutState": 1 if usb_on else 0,
        "pd.carState": 1 if car_on else 0,
        "bms_bmsStatus.temp": temp,
        "bms_emsStatus.dsgRemainTime": dsg,
        "bms_emsStatus.chgRemainTime": chg,
        "pd.remainTime": dsg,
    }
    if volts is not None:
        data["bms_bmsStatus.vol"] = round(float(volts) * 1000)
    return {k: v for k, v in data.items() if v is not None}


def set_gate_sleep(sleeping: bool) -> None:
    path = _gate_path()
    st = _read_json(path) or {}
    st["enabled"] = not sleeping
    st["night_starlink_sleep"] = bool(sleeping)
    st["starlink_sn"] = STARLINK_SN
    st["off_at_w"] = 400.0
    st["on_at_w"] = 400.0
    st["soc_keep_ac_on"] = False
    st["night_note"] = (
        "Night: USB off after dusk zero-PV (5 min). Starlink stays on Delta AC until midnight. River AC not switched. Operator hold skips USB-off."
        if sleeping
        else "Day: 400 W total-PV gate owns Delta USB-C only. Never Delta AC. USB off after 5 min of zero PV."
    )
    _write_json(path, st)


def set_audio_silent(silent: bool) -> None:
    pactl = "/usr/bin/pactl"
    if not Path(pactl).is_file():
        return
    try:
        subprocess.run(
            [pactl, "set-sink-mute", "@DEFAULT_SINK@", "1" if silent else "0"],
            check=False,
            timeout=3,
            capture_output=True,
        )
    except Exception as e:
        log.info("pulse mute skip: %s", e)


def write_night_state(*, sleeping: bool, river_ac: bool | None = True, sunrise: str, poll_s: float) -> None:
    prev = _read_json(_state_path())
    _write_json(
        _state_path(),
        {
            **prev,
            "sleeping": sleeping,
            "silent": sleeping,
            "ac_wanted": True,
            "starlink_ac_on": True,
            "starlink_sn": STARLINK_SN,
            "river_ac_left_on": True,
            "sunrise": sunrise,
            "midnight_off": f"{sleep_start_hhmm()[0]:02d}:{sleep_start_hhmm()[1]:02d}",
            "poll_s": poll_s,
            "other_pollers": "ecoflow+hybrid" if sleeping else "normal",
            "updated_at": _now().isoformat(timespec="seconds"),
        },
    )


def _idle_stop() -> None:
    if IDLE_STOP.is_file():
        try:
            subprocess.run(["bash", str(IDLE_STOP)], timeout=40, check=False)
        except Exception as e:
            log.warning("idle-stop skip: %s", e)


def _start_ava_console() -> None:
    if not LAUNCH_SH.is_file():
        return
    env = {**os.environ, "AVA_CONSOLE_IDLE_STOP": "1"}
    try:
        subprocess.Popen(
            ["gnome-terminal", "--", "bash", str(LAUNCH_SH)],
            start_new_session=True,
            env=env,
            cwd=str(AVA_HOME),
        )
        log.info("started AVA Console")
    except Exception as e:
        log.warning("AVA Console start skipped: %s", e)


def _reboot() -> None:
    log.warning("midnight reboot")
    try:
        subprocess.run(["systemctl", "reboot"], timeout=10, check=False)
    except Exception as e:
        log.warning("reboot failed: %s", e)


async def maybe_apply_usb(device, want_on: bool, last: bool | None, *, sn: str) -> bool | None:
    current = bool(getattr(device, "usb_ports", False))
    if last is not None and last == want_on and current == want_on:
        return current
    if current == want_on:
        return current
    try:
        await device.enable_usb_ports(want_on)
        session_event("usb_on" if want_on else "usb_off", sn=sn)
        log.info("%s USB ports %s", sn[-6:], "ON" if want_on else "OFF")
        await asyncio.sleep(1.5)
        return bool(getattr(device, "usb_ports", want_on))
    except Exception as e:
        log.warning("USB toggle failed sn=%s want_on=%s: %s", sn, want_on, e)
        return current


async def maybe_apply_ac(device, want_on: bool, last: bool | None, *, sn: str) -> bool | None:
    """Delta AC is Starlink — never switched. River AC is laptop — never switched."""
    current = bool(getattr(device, "ac_ports", False))
    log.info("skip AC toggle sn=%s want_on=%s current=%s (hardwired Starlink/laptop)", sn[-6:], want_on, current)
    return current


def _cloud_sign_get(access_key: str, secret_key: str, params: dict) -> dict[str, str]:
    nonce = str(int(100000 + (time.time() * 1000) % 900000))
    timestamp = str(int(time.time() * 1000))
    flat = {k: v for k, v in params.items() if v not in (None, "")}
    param_qs = "&".join(f"{k}={flat[k]}" for k in sorted(flat))
    header = {"accessKey": access_key, "nonce": nonce, "timestamp": timestamp}
    header_qs = "&".join(f"{k}={v}" for k, v in sorted(header.items()))
    sign_str = f"{param_qs}&{header_qs}" if param_qs else header_qs
    sig = hmac.new(secret_key.encode(), sign_str.encode(), hashlib.sha256).hexdigest()
    return {**header, "sign": sig, "Accept": "application/json"}


def cloud_fetch_quota(sn: str) -> dict | None:
    access = (os.getenv("AVA_ECOFLOW_ACCESS_KEY") or "").strip()
    secret = (os.getenv("AVA_ECOFLOW_SECRET_KEY") or "").strip()
    if not access or not secret:
        return None
    base = (os.getenv("AVA_ECOFLOW_BASE_URL") or "https://api-a.ecoflow.com").rstrip("/")
    params = {"sn": sn}
    headers = _cloud_sign_get(access, secret, params)
    url = f"{base}/iot-open/sign/device/quota/all?sn={sn}"
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            body = json.loads(resp.read().decode("utf-8", errors="replace") or "{}")
    except Exception as e:
        log.info("cloud quota miss %s: %s", sn[-6:], e)
        return None
    if str(body.get("code") or "0") not in {"0", "None"}:
        return None
    data = body.get("data")
    return data if isinstance(data, dict) and data else None


def _put_ac_api(sn: str, turn_on: bool) -> bool:
    """Disabled. Starlink is on Delta AC; River AC stays on. Never PUT AC."""
    log.warning("cloud AC PUT refused sn=%s want_on=%s", sn[-6:], turn_on)
    return False


def _put_usb_api(sn: str, turn_on: bool) -> bool:
    if sn != DELTA_SN:
        log.warning("USB PUT refused; only Delta USB is gated")
        return False
    access = (os.getenv("AVA_ECOFLOW_ACCESS_KEY") or "").strip()
    secret = (os.getenv("AVA_ECOFLOW_SECRET_KEY") or "").strip()
    if not access or not secret:
        return False
    base = (os.getenv("AVA_ECOFLOW_BASE_URL") or "https://api-a.ecoflow.com").rstrip("/")
    payload = {
        "sn": sn,
        "moduleType": 1,
        "operateType": "dcOutCfg",
        "params": {"enabled": 1 if turn_on else 0},
    }
    raw = json.dumps(payload, separators=(",", ":"))
    nonce = str(int(100000 + (time.time() * 1000) % 900000))
    timestamp = str(int(time.time() * 1000))
    headers = {
        "accessKey": access,
        "nonce": nonce,
        "timestamp": timestamp,
        "Content-Type": "application/json",
    }
    flat = {
        "sn": sn,
        "moduleType": "1",
        "operateType": "dcOutCfg",
        "params.enabled": str(1 if turn_on else 0),
    }
    param_qs = "&".join(f"{k}={flat[k]}" for k in sorted(flat))
    header_qs = "&".join(
        f"{k}={v}" for k, v in sorted(
            {"accessKey": access, "nonce": nonce, "timestamp": timestamp}.items()
        )
    )
    sign_str = f"{param_qs}&{header_qs}"
    headers["sign"] = hmac.new(secret.encode(), sign_str.encode(), hashlib.sha256).hexdigest()
    req = urllib.request.Request(
        f"{base}/iot-open/sign/device/quota",
        data=raw.encode("utf-8"),
        headers=headers,
        method="PUT",
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            parsed = json.loads(resp.read().decode("utf-8", errors="replace") or "{}")
        return str(parsed.get("code") or "") in {"0", "None", ""}
    except Exception as e:
        log.warning("cloud USB PUT failed %s: %s", sn[-6:], e)
        return False


def _read_quota_raw(sn: str) -> dict:
    path = quota_path(sn, OPS)
    return _read_json(path)


def _quota_ac_in(data: dict):
    if not isinstance(data, dict):
        return None
    if "inv.inputWatts" in data:
        return data.get("inv.inputWatts")
    return data.get("inv.acInWatts")


async def _starlink_ac(want_on: bool) -> bool | None:
    """Starlink is hardwired to Delta AC. Never switch AC on either pack."""
    log.info("starlink AC not switched (Delta AC permanent) want_on=%s", want_on)
    return True


def maybe_cloud_persist(sn: str) -> bool:
    existing = _read_quota_raw(sn)
    if skip_cloud_stomp(existing):
        log.info("keep BLE quota for %s; skip cloud", sn[-6:])
        return False
    if not internet_up():
        return False
    data = cloud_fetch_quota(sn)
    if not data:
        session_event("unreachable", sn=sn, extra={"via": "ble+api"})
        return False
    if skip_cloud_stomp(existing, data):
        log.info(
            "cloud leftover for %s soc=%s ac_in=%s; freeze (wait live Wi-Fi/API)",
            sn[-6:],
            soc_of(data),
            _quota_ac_in(data),
        )
        session_event(
            "unreachable",
            sn=sn,
            extra={
                "via": "cloud-stale",
                "cloud_soc": soc_of(data),
                "ac_in": _quota_ac_in(data),
            },
        )
        return False
    data = {**data, "_ava_source": "cloud"}
    out = persist_live_snapshot(sn, data, source="cloud", lock=False, ops=OPS)
    if out.get("ok"):
        log.info(
            "cloud quota %s soc=%s ac_in=%s pv=%s",
            sn[-6:],
            soc_of(data),
            _quota_ac_in(data),
            data.get("mppt.inWatts"),
        )
    return bool(out.get("ok"))


def apply_day_gate_from_disk() -> bool | None:
    sleeping = in_starlink_sleep()
    pv = total_fresh_pv(_read_quota_raw(DELTA_SN), _read_quota_raw(RIVER_SN))
    gen = classify_generator(_read_quota_raw(DELTA_SN), _read_quota_raw(RIVER_SN))
    if not gen.get("generator") and gen.get("detail") == "need_both_fresh":
        prev = _read_json(_gate_path()) or {}
        if prev.get("generator_detected"):
            gen = {
                **gen,
                "generator": True,
                "detail": "sticky_until_fresh",
                "delta_ac_in_w": prev.get("delta_ac_in_w") or gen.get("delta_ac_in_w"),
            }
    st = _read_json(_gate_path()) or {}
    st["starlink_sn"] = STARLINK_SN
    st["generator_detected"] = bool(gen.get("generator"))
    st["transfer"] = bool(gen.get("transfer"))
    st["total_pv_w"] = pv.get("total_w")
    st["off_at_w"] = 400.0
    st["on_at_w"] = 400.0
    delta_soc = soc_of(quota_data(_read_quota_raw(DELTA_SN)))
    hold = operator_present()
    now = time.time()
    pv_ok = bool(pv.get("ok"))
    total_w = pv.get("total_w") if pv_ok else None
    if pv_ok and total_w is not None and float(total_w) < PV_STOPPED_W:
        since = st.get("pv_zero_since")
        try:
            since_f = float(since) if since else now
        except (TypeError, ValueError):
            since_f = now
        if not since:
            since_f = now
        st["pv_zero_since"] = since_f
        pv_zero_for_s = max(0.0, now - since_f)
    else:
        if pv_ok:
            st["pv_zero_since"] = None
            pv_zero_for_s = 0.0
        else:
            try:
                pv_zero_for_s = float(st.get("pv_zero_for_s") or 0)
            except (TypeError, ValueError):
                pv_zero_for_s = 0.0
    st["pv_zero_for_s"] = round(pv_zero_for_s, 1)
    st["operator_hold"] = hold
    want = want_delta_usb_on(
        total_w,
        sleeping=sleeping,
        generator=bool(gen.get("generator")),
        delta_soc=delta_soc,
        pv_zero_for_s=pv_zero_for_s,
        operator_hold=hold,
    )
    st["delta_soc"] = delta_soc
    st["usb_min_soc"] = DELTA_USB_MIN_SOC
    st["ac_min_soc"] = DELTA_USB_MIN_SOC
    st["desired_usb"] = {True: "on", False: "off"}.get(want) if want is not None else st.get("desired_usb")
    st["desired_ac"] = st.get("desired_ac")
    if want is None and hold and (sleeping or pv_zero_for_s >= USB_NIGHT_OFF_S):
        st["decision_reason"] = "operator_hold_usb"
        st["last_decision"] = "hold_operator"
        st["last_skip_reason"] = "operator_hold"
    elif want is False and sleeping:
        st["decision_reason"] = "usb_night_sleep"
    elif want is False and pv_zero_for_s >= USB_NIGHT_OFF_S:
        st["decision_reason"] = "usb_night_off_pv_zero"
    elif want is False and delta_soc is not None and float(delta_soc) < DELTA_USB_MIN_SOC:
        st["decision_reason"] = "delta_soc_lt_3"
    elif want is None and gen.get("generator"):
        st["decision_reason"] = "generator"
        st["last_decision"] = "hold_generator"
        st["last_skip_reason"] = None
        st["generator_detail"] = gen.get("detail")
        st["delta_ac_in_w"] = gen.get("delta_ac_in_w")
    elif want is True:
        st["decision_reason"] = "total_pv_lt_400"
    elif want is False:
        st["decision_reason"] = "total_pv_gte_400"
    _write_json(_gate_path(), st)
    try:
        if str(AVA_HOME) not in sys.path:
            sys.path.insert(0, str(AVA_HOME))
        from apps.core.services.ecoflow_public import on_gate_update

        on_gate_update(gate=st, gen=gen)
    except Exception as e:
        log.info("ecoflow public skip: %s", e)
    return want


def _target_macs() -> set[str]:
    return {
        (os.getenv("AVA_ECOFLOW_BLE_MAC") or DEFAULT_DELTA_MAC).strip().upper(),
        (os.getenv("AVA_ECOFLOW_RIVER_BLE_MAC") or DEFAULT_RIVER_MAC).strip().upper(),
    }


async def _scan_seen(*, need_mac: str | None = None) -> dict:
    from bleak import BleakScanner

    need = (need_mac or "").strip().upper()
    async with _scan_lock:
        now = time.monotonic()
        cached = _scan_cache["seen"]
        if now - float(_scan_cache["at"]) < SCAN_CACHE_S and cached:
            if not need or need in cached:
                return cached
        seen: dict = {}

        def cb(d, adv):
            seen[d.address.upper()] = (d, adv)

        scanner = BleakScanner(detection_callback=cb)
        await scanner.start()
        await asyncio.sleep(SCAN_S)
        await scanner.stop()
        await asyncio.sleep(0.5)
        hit = {k: v for k, v in seen.items() if k in _target_macs()}
        if hit:
            _scan_cache["at"] = time.monotonic()
            _scan_cache["seen"] = seen
        else:
            _scan_cache["at"] = 0.0
            _scan_cache["seen"] = {}
        return seen


def _device_from_sn(ble_dev, adv, sn: str):
    from bleak.backends.scanner import AdvertisementData
    from eflib.devices import delta2, river2_pro

    if adv is None:
        adv = AdvertisementData(
            local_name=getattr(ble_dev, "name", None),
            manufacturer_data={},
            service_data={},
            service_uuids=[],
            tx_power=None,
            rssi=-127,
            platform_data=(),
        )
    if sn.startswith("R621") or sn.startswith("R623"):
        return river2_pro.Device(ble_dev, adv, sn)
    if sn.startswith("R331") or sn.startswith("R335"):
        return delta2.Device(ble_dev, adv, sn)
    return None


async def _connect_pack(user_id: str, mac: str, sn: str, model: str) -> None:
    from bleak import BleakScanner
    from eflib import NewDevice
    from eflib.connection import ConnectionState

    seen = await _scan_seen(need_mac=mac)
    rec = seen.get(mac.upper())
    if rec is None:
        async with _scan_lock:
            ble_dev = await BleakScanner.find_device_by_address(mac, timeout=SCAN_S)
        if ble_dev is None:
            raise RuntimeError(f"{model} not advertising ({mac})")
        rec = (ble_dev, None)
    ble_dev, adv = rec
    rssi = getattr(adv, "rssi", None) if adv is not None else None
    device = NewDevice(ble_dev, adv) if adv is not None else None
    if device is None:
        device = _device_from_sn(ble_dev, adv, sn)
    if device is None:
        raise RuntimeError("NewDevice returned None")
    session_event("connect", sn=sn, extra={"mac": mac, "rssi": rssi, "model": model})
    async with _connect_lock:
        await device.connect(user_id=user_id, max_attempts=3)
    last_quota = 0.0
    last_usb_want: bool | None = None
    try:
        state = await asyncio.wait_for(
            device.wait_until_authenticated_or_error(raise_on_error=False),
            timeout=45,
        )
        if state != ConnectionState.AUTHENTICATED:
            raise RuntimeError(f"auth failed: {state}")
        session_event("auth", sn=sn, extra={"mac": mac})
        log.info("authenticated %s %s", model, sn)
        _live_devices[sn] = device
        await asyncio.sleep(2)
        while True:
            sleeping = in_starlink_sleep()
            poll_s = NIGHT_WRITE_S if sleeping else DAY_WRITE_S
            now_m = time.monotonic()
            if now_m - last_quota >= poll_s:
                data = _map_pack(device, model=model)
                if "bms_bmsStatus.soc" in data or "pd.soc" in data:
                    persist_live_snapshot(sn, data, source="ble", lock=True, ops=OPS)
                    last_quota = now_m
                    log.info(
                        "ble %s soc=%s out=%s ac=%s in=%s sleep=%s",
                        model,
                        data.get("pd.soc"),
                        data.get("pd.wattsOutSum"),
                        data.get("inv.cfgAcEnabled"),
                        data.get("inv.inputWatts"),
                        sleeping,
                    )
            if sn == DELTA_SN:
                want = apply_day_gate_from_disk()
                if want is not None:
                    await maybe_apply_usb(device, want, last_usb_want, sn=sn)
                    last_usb_want = want
                if sleeping:
                    h, m = sunrise_hhmm()
                    write_night_state(
                        sleeping=sleeping,
                        sunrise=f"{h:02d}:{m:02d}",
                        poll_s=poll_s,
                    )
            await asyncio.sleep(TICK_S)
    finally:
        _live_devices.pop(sn, None)
        unlock_quota(sn, OPS)
        session_event("disconnect", sn=sn)
        try:
            await device.disconnect()
        except Exception:
            pass


async def _pack_supervisor(user_id: str, mac: str, sn: str, model: str) -> None:
    delay = 5
    while True:
        try:
            await _connect_pack(user_id, mac, sn, model)
            delay = 5
        except asyncio.CancelledError:
            raise
        except Exception as e:
            log.warning("%s session ended: %s", model, e)
            session_event("unreachable", sn=sn, extra={"error": str(e)[:200]})
            # BLE miss at range (generator ~200–300 ft) still uses Open API.
            unlock_quota(sn, OPS)
            cloud_ok = maybe_cloud_persist(sn)
            if sn == DELTA_SN:
                apply_day_gate_from_disk()
            if cloud_ok:
                log.info("cloud fallback wrote quota for %s", sn[-6:])
            else:
                log.info("recording frozen for %s until BLE or API returns", sn[-6:])
            await asyncio.sleep(delay)
            delay = min(60, delay * 2)


async def _run_midnight_seq() -> None:
    refresh_sun(online_ok=True)
    h, m = sunrise_hhmm()
    sunrise = f"{h:02d}:{m:02d}"
    set_gate_sleep(True)
    set_audio_silent(True)
    write_night_state(sleeping=True, sunrise=sunrise, poll_s=NIGHT_WRITE_S)
    _idle_stop()
    set_power_profile("power-saver")
    log.info("midnight seq: Starlink stays on Delta AC; River AC not switched")
    st = _read_json(_state_path())
    st["midnight_seq_date"] = _now().strftime("%Y-%m-%d")
    _write_json(_state_path(), st)
    _reboot()


async def _run_sunrise_seq() -> None:
    log.info("sunrise seq: Starlink already on Delta AC; River AC left on")
    up = False
    for _ in range(36):
        if internet_up():
            up = True
            break
        await asyncio.sleep(5)
    if not up:
        log.warning("sunrise: no internet; stay night stack")
        set_power_profile("power-saver")
        return
    set_power_profile("performance")
    set_audio_silent(False)
    set_gate_sleep(False)
    h, m = sunrise_hhmm()
    write_night_state(
        sleeping=False,
        river_ac=True,
        sunrise=f"{h:02d}:{m:02d}",
        poll_s=DAY_WRITE_S,
    )
    _write_json(
        _sunrise_flag_path(),
        {"pending": True, "at": _now().isoformat(timespec="seconds")},
    )
    _start_ava_console()


async def _night_orchestrator() -> None:
    last_sleep: bool | None = None
    while True:
        sleeping = in_starlink_sleep()
        today = _now().strftime("%Y-%m-%d")
        st = _read_json(_state_path())
        if last_sleep is None:
            set_gate_sleep(sleeping)
            set_audio_silent(sleeping)
            if sleeping:
                set_power_profile("power-saver")
            last_sleep = sleeping
            await asyncio.sleep(TICK_S)
            continue
        if sleeping and last_sleep is not True:
            if operator_present():
                log.info("midnight seq delayed; operator present")
                set_gate_sleep(True)
                set_audio_silent(True)
                write_night_state(
                    sleeping=True,
                    sunrise=f"{sunrise_hhmm()[0]:02d}:{sunrise_hhmm()[1]:02d}",
                    poll_s=NIGHT_WRITE_S,
                )
            elif st.get("midnight_seq_date") == today:
                last_sleep = True
            else:
                await _run_midnight_seq()
                last_sleep = True
        elif (not sleeping) and last_sleep is not False:
            if st.get("sunrise_seq_date") == today:
                last_sleep = False
            else:
                await _run_sunrise_seq()
                st = _read_json(_state_path())
                st["sunrise_seq_date"] = today
                _write_json(_state_path(), st)
                last_sleep = False
        await asyncio.sleep(TICK_S)


async def main() -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    _load_ava_env()
    user_id = os.getenv("AVA_ECOFLOW_USER_ID", "").strip()
    delta_mac = os.getenv("AVA_ECOFLOW_BLE_MAC", DEFAULT_DELTA_MAC).strip() or DEFAULT_DELTA_MAC
    river_mac = os.getenv("AVA_ECOFLOW_RIVER_BLE_MAC", DEFAULT_RIVER_MAC).strip() or DEFAULT_RIVER_MAC
    if not user_id.isdigit():
        log.error("AVA_ECOFLOW_USER_ID missing")
        return 2
    own = OPS / "state" / "ble-poller.own"
    own.parent.mkdir(parents=True, exist_ok=True)
    own.write_text(str(os.getpid()), encoding="utf-8")
    try:
        await asyncio.gather(
            _pack_supervisor(user_id, delta_mac, DELTA_SN, "Delta 2"),
            _pack_supervisor(user_id, river_mac, RIVER_SN, "River 2 Pro"),
            _night_orchestrator(),
        )
    finally:
        try:
            if own.is_file() and own.read_text(encoding="utf-8").strip() == str(os.getpid()):
                own.unlink()
        except OSError:
            pass
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(asyncio.run(main()))
    except KeyboardInterrupt:
        raise SystemExit(0)
