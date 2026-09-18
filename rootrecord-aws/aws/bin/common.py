"""Shared paths and helpers for RootRecord AWS collectors."""
from __future__ import annotations

import json
import logging
import os
import time
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

HST = ZoneInfo("Pacific/Honolulu")
ROOT = Path(os.environ.get("RR_ROOT", "/home/ubuntu/rootrecord"))
WORK = ROOT / "work"
OUT = ROOT / "out"
LOGS = ROOT / "logs"
ETC = ROOT / "etc"
UA = {"User-Agent": "RootRecord/1.0 (https://rootrecord.cloud; aws-collector)"}

# Never log full URLs (httpx would print bot tokens in api.telegram.org paths)
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)



def now_hst() -> datetime:
    return datetime.now(HST)


def stamp() -> str:
    return now_hst().strftime("%Y%m%d-%H%M%S")


def ensure_dirs() -> None:
    for name in (
        "weather",
        "earthquakes",
        "radar",
        "chatlogs",
        "triggers",
        "hurricane",
        "noaa",
        "audio",
        "radio",
        "sysmon",
        "assets",
    ):
        (WORK / name).mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    LOGS.mkdir(parents=True, exist_ok=True)
    ETC.mkdir(parents=True, exist_ok=True)
    (ROOT / "chronological").mkdir(parents=True, exist_ok=True)
    (ROOT / "radio" / "media").mkdir(parents=True, exist_ok=True)


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(path)


def append_jsonl(path: Path, row: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(row, sort_keys=True) + "\n")


def load_dotenv(path: Path | None = None) -> None:
    """Minimal .env loader (no dependency). Does not print values."""
    env_path = path or (ETC / "secrets.env")
    if not env_path.is_file():
        return
    for line in env_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        key = key.strip()
        val = val.strip().strip("'").strip('"')
        if key and key not in os.environ:
            os.environ[key] = val


def sleep_until(interval_s: float, started: float) -> None:
    elapsed = time.monotonic() - started
    delay = max(0.0, interval_s - elapsed)
    if delay:
        time.sleep(delay)
