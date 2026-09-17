"""Owner/ops corrections that the council must not argue with."""
from __future__ import annotations

import json
import re
import time
from pathlib import Path
from typing import Any

from .config import CONFIG_DIR

PATH = CONFIG_DIR / "ops-corrections.json"
MAX = 12
LINE_CAP = 220
OPSFIX = re.compile(r"<<<OPSFIX\s+([^>]*?)>>>", re.I)
HINT = re.compile(
    r"(?i)("
    r"not a concern|chill out|you(?:'re| are) not thinking|"
    r"confusing global|heading.{0,60}japan|used the gpu|"
    r"stay on topic|that(?:'s| is) not(?: a)?|"
    r"listen to ops|ops (?:said|says|corrected)|3k miles|3000 miles|"
    r"not close enough|heading away"
    r")"
)


def _now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime()) + "-10:00"


def _load() -> dict[str, Any]:
    if not PATH.is_file():
        return {"updated": "", "items": []}
    try:
        data = json.loads(PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"updated": "", "items": []}
    return data if isinstance(data, dict) else {"updated": "", "items": []}


def _save(data: dict[str, Any]) -> None:
    PATH.parent.mkdir(parents=True, exist_ok=True)
    data = dict(data)
    data["updated"] = _now()
    tmp = PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    tmp.replace(PATH)


def add(text: str, *, source: str = "ops") -> dict[str, Any]:
    body = re.sub(r"\s+", " ", (text or "").strip())[:LINE_CAP]
    if not body:
        return {"ok": False}
    data = _load()
    items = [x for x in (data.get("items") or []) if isinstance(x, dict)]
    if items and str(items[-1].get("text") or "") == body:
        return {"ok": True, "skipped": True}
    items.append({"ts": _now(), "source": source[:24], "text": body})
    data["items"] = items[-MAX:]
    _save(data)
    return {"ok": True}


def note_owner(text: str) -> None:
    raw = text or ""
    for hit in OPSFIX.findall(raw):
        add(hit, source="tag")
    if HINT.search(raw) and not raw.strip().startswith("/"):
        add(raw, source="owner")


def prompt_lines(*, cap: int = 700) -> str:
    items = [x for x in (_load().get("items") or []) if isinstance(x, dict)]
    if not items:
        return (
            "Ops/operator corrections bind. Do not argue. "
            "Hawaiʻi is not Japan. West of Kauaʻi is toward Asia. "
            "A cyclone thousands of nautical miles west of Līhuʻe is not a local Hawaii threat."
        )
    lines = [
        "Ops/operator already corrected you. Accept it. Do not debate:",
    ]
    for row in items[-6:]:
        lines.append(f"- {row.get('text')}")
    blob = "\n".join(lines)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob
