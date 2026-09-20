"""BLE/cloud snapshot persist + freshness. No invented watts."""
from __future__ import annotations

import json
import logging
import os
import sqlite3
import time
from datetime import datetime, timezone
from pathlib import Path

log = logging.getLogger("ecoflow-ble-store")

DELTA_SN = "R331ZAB5SG6S2858"
RIVER_SN = "R621ZA16XH6K1155"
STARLINK_SN = DELTA_SN
STALE_S = 180.0
BLE_FRESH_S = 60.0
PV_GATE_W = 400.0
PV_STOPPED_W = 1.0
USB_NIGHT_OFF_S = 300.0
DELTA_USB_MIN_SOC = 3.0
DELTA_AC_MIN_SOC = DELTA_USB_MIN_SOC
GEN_MATCH_W = 50.0

CREATE_SNAPSHOTS = """
CREATE TABLE IF NOT EXISTS snapshots (
  ts TEXT,
  sn TEXT,
  online INTEGER,
  soc REAL,
  in_w REAL,
  out_w REAL,
  solar_w REAL,
  raw_json TEXT,
  created_at TEXT
)
"""


def ops_root(ops: Path | None = None) -> Path:
    if ops is not None:
        return Path(ops)
    return Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store"


def quota_path(sn: str, ops: Path | None = None) -> Path:
    p = ops_root(ops) / "quota"
    p.mkdir(parents=True, exist_ok=True)
    return p / f"{sn}.json"


def history_path(sn: str, ops: Path | None = None) -> Path:
    p = ops_root(ops) / "history"
    p.mkdir(parents=True, exist_ok=True)
    return p / f"{sn}.jsonl"


def db_path(ops: Path | None = None) -> Path:
    return ops_root(ops) / "ecoflow-10s.db"


def session_log_path(ops: Path | None = None) -> Path:
    p = ops_root(ops) / "state"
    p.mkdir(parents=True, exist_ok=True)
    return p / "ble-session.jsonl"


def _num(v) -> float | None:
    try:
        if v is None or v == "":
            return None
        return float(v)
    except (TypeError, ValueError):
        return None


def quota_at_s(raw: dict) -> float | None:
    at = int(raw.get("at") or 0)
    if at <= 0:
        return None
    return at / 1000.0 if at > 1_000_000_000_000 else float(at)


def quota_age_s(raw: dict, now: float | None = None) -> float | None:
    at_s = quota_at_s(raw)
    if not at_s:
        return None
    return (now or time.time()) - at_s


def quota_source(raw: dict) -> str:
    src = str(raw.get("source") or "").lower()
    if src in {"ble", "cloud"}:
        return src
    data = ((raw.get("body") or {}).get("data") or {})
    inner = str((data or {}).get("_ava_source") or "").lower()
    if inner in {"ble", "cloud"}:
        return inner
    return ""


def quota_data(raw: dict) -> dict:
    data = ((raw.get("body") or {}).get("data") or raw.get("data") or {})
    return data if isinstance(data, dict) else {}


def is_fresh(raw: dict, *, max_age_s: float = STALE_S, now: float | None = None) -> bool:
    if not raw:
        return False
    age = quota_age_s(raw, now)
    if age is None or age > max_age_s:
        return False
    return True


def is_recordable(raw: dict, *, now: float | None = None) -> bool:
    """Fresh sample from ble or cloud this write window — usable in bank/gate."""
    if not is_fresh(raw, now=now):
        return False
    return quota_source(raw) in {"ble", "cloud"}


def pv_w(data: dict) -> float:
    for key in ("mppt.inWatts", "pd.wattsInSum"):
        n = _num(data.get(key))
        if n is not None:
            return max(0.0, n)
    return 0.0


def ac_in_w(data: dict) -> float:
    n = _num(data.get("inv.inputWatts") or data.get("pd.chgSunPower"))
    if n is not None:
        return max(0.0, n)
    return 0.0


def ac_out_w(data: dict) -> float:
    n = _num(data.get("inv.outputWatts") or data.get("pd.wattsOutSum"))
    if n is not None:
        return max(0.0, n)
    return 0.0


def usb_out_w(data: dict) -> float:
    total = 0.0
    hit = False
    for key in ("pd.typec1Watts", "pd.typec2Watts"):
        n = _num(data.get(key))
        if n is None:
            continue
        hit = True
        total += max(0.0, n)
    if hit:
        return total
    n = _num(data.get("pd.dcOutWatts"))
    return max(0.0, n) if n is not None else 0.0


def usb_on(data: dict) -> bool | None:
    raw = data.get("pd.dcOutState")
    if raw is None:
        return None
    try:
        return bool(int(raw))
    except (TypeError, ValueError):
        return None


def soc_of(data: dict) -> float | None:
    for key in (
        "bms_bmsStatus.soc",
        "bms_bmsStatus.f32ShowSoc",
        "pd.soc",
        "soc",
    ):
        n = _num(data.get(key))
        if n is not None:
            return n
    return None


def history_row(sn: str, data: dict, *, source: str, online: bool = True) -> dict:
    soc = soc_of(data)
    solar = pv_w(data)
    in_w = ac_in_w(data) or _num(data.get("pd.wattsInSum")) or 0
    out_w = _num(data.get("pd.wattsOutSum")) or ac_out_w(data) or 0
    return {
        "at": int(time.time() * 1000),
        "deviceOnline": bool(online),
        "soc": soc,
        "solarW": solar,
        "inW": in_w,
        "outW": out_w,
        "offCircuit": not online,
        "source": source,
        "sn": sn,
    }


def skip_cloud_stomp(existing: dict, cloud_data: dict | None = None, *, now: float | None = None) -> bool:
    """Keep last BLE sample; EcoFlow Open API often returns leftover 1% SOC."""
    if quota_source(existing) == "ble" and is_fresh(existing, max_age_s=90, now=now):
        return True
    if not cloud_data:
        return False
    last = soc_of(quota_data(existing))
    cloud_soc = soc_of(cloud_data)
    if last is not None and cloud_soc is not None and last > 1.2 and cloud_soc <= 1.05:
        return True
    return False


def persist_live_snapshot(
    sn: str,
    data: dict,
    *,
    source: str,
    lock: bool,
    ops: Path | None = None,
) -> dict:
    """Write quota + jsonl + sqlite. Call only after a live BLE or API read."""
    if soc_of(data) is None and data.get("pd.soc") is None and data.get("bms_bmsStatus.soc") is None:
        return {"ok": False, "detail": "no_soc"}
    qpath = quota_path(sn, ops)
    if source == "cloud" and qpath.is_file():
        try:
            locked = (qpath.stat().st_mode & 0o222) == 0
            existing = json.loads(qpath.read_text(encoding="utf-8"))
        except Exception:
            locked = False
            existing = {}
        if locked or skip_cloud_stomp(existing if isinstance(existing, dict) else {}, data):
            return {"ok": False, "detail": "locked"}
    payload = {
        "at": int(time.time() * 1000),
        "status": 200,
        "body": {"code": "0", "message": "Success", "data": data},
        "source": source,
    }
    tmp = qpath.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(payload), encoding="utf-8")
    tmp.replace(qpath)
    try:
        qpath.chmod(0o444 if lock else 0o644)
    except OSError:
        pass
    row = history_row(sn, data, source=source, online=True)
    hpath = history_path(sn, ops)
    with hpath.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(row, separators=(",", ":")) + "\n")
        fh.flush()
        os.fsync(fh.fileno())
    append_sqlite(sn, row, ops=ops)
    return {"ok": True, "sn": sn, "source": source, "soc": row.get("soc")}


def append_sqlite(sn: str, row: dict, *, ops: Path | None = None) -> None:
    db = db_path(ops)
    now_iso = datetime.now(timezone.utc).isoformat()
    try:
        con = sqlite3.connect(str(db), timeout=5)
        try:
            con.execute(CREATE_SNAPSHOTS)
            con.execute(
                "INSERT INTO snapshots "
                "(ts, sn, online, soc, in_w, out_w, solar_w, raw_json, created_at) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    now_iso,
                    sn,
                    1 if row.get("deviceOnline") else 0,
                    row.get("soc"),
                    row.get("inW") or 0,
                    row.get("outW") or 0,
                    row.get("solarW") or 0,
                    json.dumps(row, separators=(",", ":")),
                    now_iso,
                ),
            )
            con.commit()
        finally:
            con.close()
    except Exception as e:
        log.warning("ecoflow sqlite append skipped: %s", e)


def unlock_quota(sn: str, ops: Path | None = None) -> None:
    q = quota_path(sn, ops)
    if q.is_file():
        try:
            q.chmod(0o644)
        except OSError:
            pass


def session_event(event: str, *, sn: str = "", extra: dict | None = None, ops: Path | None = None) -> None:
    row = {
        "at": datetime.now(timezone.utc).isoformat(),
        "event": event,
        "sn": sn,
    }
    if extra:
        row.update(extra)
    path = session_log_path(ops)
    with path.open("a", encoding="utf-8") as fh:
        fh.write(json.dumps(row, separators=(",", ":")) + "\n")


def total_fresh_pv(delta_raw: dict, river_raw: dict, *, now: float | None = None) -> dict:
    total = 0.0
    used = []
    for raw, label in ((delta_raw, "delta"), (river_raw, "river")):
        if not is_recordable(raw, now=now):
            continue
        w = pv_w(quota_data(raw))
        total += w
        used.append({"pack": label, "pv_w": w, "source": quota_source(raw)})
    return {"ok": bool(used), "total_w": round(total, 1), "packs": used}


def classify_generator(delta_raw: dict, river_raw: dict, *, now: float | None = None) -> dict:
    """Generator if one pack AC-in and the other is not discharging that same amount.

    Transfer needs both packs fresh. Unmatched AC-in on a single fresh pack is
    still generator so the gate holds when the other radio drops.
    """
    d_ok = is_recordable(delta_raw, now=now)
    r_ok = is_recordable(river_raw, now=now)
    d = quota_data(delta_raw) if d_ok else {}
    r = quota_data(river_raw) if r_ok else {}
    d_in, d_out = ac_in_w(d), ac_out_w(d)
    r_in, r_out = ac_in_w(r), ac_out_w(r)
    d_usb, r_in_sum = usb_out_w(d), max(ac_in_w(r), _num(r.get("pd.wattsInSum")) or 0.0)
    if d_ok and r_ok:
        if d_usb > 0 and r_in_sum > 0 and abs(d_usb - r_in_sum) <= GEN_MATCH_W:
            return {"generator": False, "transfer": True, "detail": "delta_usb_to_river"}
        if d_out > 0 and r_in > 0 and abs(d_out - r_in) <= GEN_MATCH_W:
            return {"generator": False, "transfer": True, "detail": "delta_ac_to_river"}
        if r_out > 0 and d_in > 0 and abs(r_out - d_in) <= GEN_MATCH_W:
            return {"generator": False, "transfer": True, "detail": "river_ac_to_delta"}
        if (d_in > 0 and abs(r_out - d_in) > GEN_MATCH_W) or (
            r_in > 0 and abs(d_out - r_in) > GEN_MATCH_W
        ):
            return {
                "generator": True,
                "transfer": False,
                "detail": "external_ac_in",
                "delta_ac_in_w": d_in,
                "river_ac_in_w": r_in,
            }
        return {"generator": False, "transfer": False, "detail": "idle"}
    if d_ok and d_in > 0:
        return {
            "generator": True,
            "transfer": False,
            "detail": "external_ac_in_delta_only",
            "delta_ac_in_w": d_in,
            "river_ac_in_w": 0.0,
        }
    if r_ok and r_in > 0:
        return {
            "generator": True,
            "transfer": False,
            "detail": "external_ac_in_river_only",
            "delta_ac_in_w": 0.0,
            "river_ac_in_w": r_in,
        }
    return {"generator": False, "transfer": False, "detail": "need_both_fresh"}


def want_delta_usb_on(
    total_pv_w: float | None,
    *,
    sleeping: bool,
    generator: bool,
    delta_soc: float | None = None,
    pv_zero_for_s: float = 0.0,
    operator_hold: bool = False,
) -> bool | None:
    """None = hold. Day USB-C (Delta → River, ~100 W DC) follows total PV.

    Starlink stays on Delta AC and is never switched here. River AC stays on
    for the laptop. After total PV is fully stopped for USB_NIGHT_OFF_S, USB
    stays off until PV returns. Midnight sleep keeps USB off. operator_hold
    (Cursor / inhibit) skips that night USB-off. Generator holds. SOC under 3%
    forces USB off.
    """
    if generator:
        return None
    night_off = bool(sleeping) or float(pv_zero_for_s or 0) >= USB_NIGHT_OFF_S
    if night_off:
        if operator_hold:
            return None
        return False
    if delta_soc is None:
        return None
    if float(delta_soc) < DELTA_USB_MIN_SOC:
        return False
    if total_pv_w is None:
        return None
    return float(total_pv_w) < PV_GATE_W


def want_delta_ac_on(
    total_pv_w: float | None,
    *,
    sleeping: bool,
    generator: bool,
    delta_soc: float | None = None,
    pv_zero_for_s: float = 0.0,
    operator_hold: bool = False,
) -> bool | None:
    """Alias: 400 W gate now owns Delta USB, not AC."""
    return want_delta_usb_on(
        total_pv_w,
        sleeping=sleeping,
        generator=generator,
        delta_soc=delta_soc,
        pv_zero_for_s=pv_zero_for_s,
        operator_hold=operator_hold,
    )
