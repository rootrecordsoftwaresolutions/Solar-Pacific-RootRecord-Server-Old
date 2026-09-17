"""Bruce posts measured host/EcoFlow samples a few times a day. Never invents watts."""
from __future__ import annotations

import json
import time
from datetime import datetime
from zoneinfo import ZoneInfo

from .config import CONFIG_DIR
from .notify import post

HST = ZoneInfo("Pacific/Honolulu")
PATH = CONFIG_DIR / "bruce-stats.json"
HOURS = (7, 15, 21)


def _load() -> dict:
    if not PATH.is_file():
        return {}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return data if isinstance(data, dict) else {}


def _save(data: dict) -> None:
    PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def build_text() -> str:
    from apps.core.services import db_facts

    host = db_facts.host_line()
    eco = db_facts.ecoflow_line()
    return (
        "Desk sample (measured)\n"
        f"{host}\n"
        f"{eco}\n"
        "\n"
        "Bruce Monitor"
    )


def _holdoff() -> bool:
    from . import state as state_mod
    from .config import load_config

    st = state_mod.load_state(load_config().state_path)
    return str(st.get("discussion") or "on").lower() == "off"


def tick(*, force: bool = False) -> dict:
    if _holdoff() and not force:
        return {"ok": True, "skipped": True, "detail": "holdoff"}
    now = datetime.now(HST)
    slot = f"{now.strftime('%Y-%m-%d')}-{now.hour}"
    data = _load()
    if not force and data.get("last_slot") == slot:
        return {"ok": True, "skipped": True, "detail": "already"}
    out = post("bruce", build_text())
    if out.get("ok"):
        data["last_slot"] = slot
        data["updated"] = int(time.time())
        _save(data)
    return out


def maybe_slot() -> dict:
    """Post at 7:18 / 15:18 / 21:18 HST once per slot."""
    now = datetime.now(HST)
    if now.hour not in HOURS or now.minute < 18 or now.minute > 28:
        return {"ok": True, "skipped": True, "detail": "off slot"}
    slot = f"{now.strftime('%Y-%m-%d')}-{now.hour}"
    data = _load()
    if data.get("last_slot") == slot:
        return {"ok": True, "skipped": True, "detail": "already"}
    out = tick()
    return out
