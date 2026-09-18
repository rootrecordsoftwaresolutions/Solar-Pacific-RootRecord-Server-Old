"""Append-only council chat log (for debug + follow-ups)."""
from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any

from .config import CONFIG_DIR

LOG = CONFIG_DIR / "chat.jsonl"


def append(event: dict[str, Any]) -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    row = dict(event)
    row["ts"] = int(time.time())
    with LOG.open("a", encoding="utf-8") as f:
        f.write(json.dumps(row, ensure_ascii=False) + "\n")


def recent(n: int = 40) -> list[dict[str, Any]]:
    if not LOG.is_file():
        return []
    lines = LOG.read_text(encoding="utf-8", errors="replace").splitlines()
    out = []
    for line in lines[-n:]:
        try:
            out.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return out


def last_from_user(user_id: int | str, n: int = 10) -> list[dict[str, Any]]:
    uid = str(user_id)
    return [r for r in recent(80) if str(r.get("from_id")) == uid and r.get("dir") == "in"][-n:]


def load_all() -> list[dict[str, Any]]:
    if not LOG.is_file():
        return []
    out: list[dict[str, Any]] = []
    try:
        raw = LOG.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return []
    for line in raw.splitlines():
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(row, dict):
            out.append(row)
    return out


def last_round_ask(chat_id: int | str) -> str:
    """Most recent inbound that started a thought session / proposal round."""
    from . import router

    for r in reversed(for_chat(chat_id)):
        if r.get("dir") != "in":
            continue
        text = str(r.get("text") or "")
        if router.is_round_start(text):
            return text[:800]
    return ""


def for_chat(
    chat_id: int | str,
    *,
    since_ts: int | None = None,
    until_ts: int | None = None,
) -> list[dict[str, Any]]:
    cid = str(chat_id)
    rows: list[dict[str, Any]] = []
    for r in load_all():
        if str(r.get("chat_id") or "") != cid:
            continue
        ts = int(r.get("ts") or 0)
        if since_ts is not None and ts < int(since_ts):
            continue
        if until_ts is not None and ts > int(until_ts):
            continue
        rows.append(r)
    return rows


def recent_humans(chat_id: int | str, n: int = 10) -> list[dict[str, Any]]:
    """Last n inbound human lines in a chat (newest last). Skips empty/bot rows."""
    want = max(1, int(n))
    out: list[dict[str, Any]] = []
    for r in reversed(for_chat(chat_id)):
        if r.get("dir") != "in":
            continue
        text = str(r.get("text") or "").strip()
        if not text:
            continue
        low = text.lower()
        if low.startswith("photo shared") or low.startswith("photo album"):
            continue
        out.append(r)
        if len(out) >= want:
            break
    out.reverse()
    return out


def recent_for_chat(chat_id: int | str, n: int = 8) -> list[dict[str, Any]]:
    return for_chat(chat_id)[-n:]


def format_history(
    rows: list[dict[str, Any]],
    cap: int = 1500,
    *,
    line_chars: int = 220,
) -> str:
    lines: list[str] = []
    for r in rows:
        who = r.get("voice") or r.get("display") or r.get("username") or "user"
        direction = "→" if r.get("dir") == "out" else "←"
        text = str(r.get("text") or "").replace("\n", " ")
        if line_chars > 0:
            text = text[:line_chars]
        if not text:
            continue
        lines.append(f"{direction} {who}: {text}")
    blob = "\n".join(lines)
    if cap > 0 and len(blob) > cap:
        blob = blob[-cap:]
    return blob
