"""AVA Console / origin process identity so council can catch up on desk start."""
from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from apps.core import config


def state_path() -> Path:
    return config.STATE_DIR / "origin-session.json"


def write_started() -> dict[str, Any]:
    path = state_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "id": f"{os.getpid()}-{int(datetime.now(timezone.utc).timestamp())}",
        "pid": os.getpid(),
        "started_at": datetime.now(timezone.utc).isoformat(),
    }
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    return payload


def read_id() -> str:
    path = state_path()
    if not path.is_file():
        return ""
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return ""
    if not isinstance(data, dict):
        return ""
    return str(data.get("id") or "").strip()
