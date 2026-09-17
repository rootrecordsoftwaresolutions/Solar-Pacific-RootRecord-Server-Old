"""Pending Cursor implement approvals."""
from __future__ import annotations

import json
import time
import uuid
from pathlib import Path
from typing import Any

from .config import RUNS_DIR


def _now() -> int:
    return int(time.time())


def _approvals_path() -> Path:
    return RUNS_DIR / "approvals.json"


def load_approvals() -> dict[str, Any]:
    p = _approvals_path()
    if not p.is_file():
        return {"pending": {}, "history": []}
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"pending": {}, "history": []}
    data.setdefault("pending", {})
    data.setdefault("history", [])
    return data


def save_approvals(data: dict[str, Any]) -> None:
    RUNS_DIR.mkdir(parents=True, exist_ok=True)
    p = _approvals_path()
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(p)


def create_approval(
    prompt_text: str,
    user_ask: str,
    run_id: str | None = None,
    aid: str | None = None,
) -> str:
    data = load_approvals()
    aid = (aid or "").strip() or uuid.uuid4().hex[:10]
    data["pending"][aid] = {
        "id": aid,
        "created": _now(),
        "status": "pending",
        "prompt_text": prompt_text,
        "user_ask": user_ask,
        "run_id": run_id or "",
    }
    save_approvals(data)
    return aid


def lookup_id(raw: str) -> str:
    s = (raw or "").strip()
    if not s:
        return ""
    data = load_approvals()
    pending = data.get("pending") or {}
    if s in pending:
        return s
    if f"plan-{s}" in pending:
        return f"plan-{s}"
    if s.startswith("plan-") and s[5:] in pending:
        return s[5:]
    hist = data.get("history") or []
    for item in reversed(hist):
        iid = str(item.get("id") or "")
        if iid == s or iid == f"plan-{s}":
            return iid
    return s


def get_pending(aid: str) -> dict[str, Any] | None:
    data = load_approvals()
    key = lookup_id(aid)
    return data.get("pending", {}).get(key) or data.get("pending", {}).get(aid)


def latest_pending() -> dict[str, Any] | None:
    pending = load_approvals().get("pending") or {}
    if not pending:
        return None
    item = max(pending.values(), key=lambda r: int(r.get("created") or 0))
    return item if isinstance(item, dict) else None


def update_pending_prompt(aid: str, prompt_text: str) -> None:
    data = load_approvals()
    key = lookup_id(aid)
    item = data.get("pending", {}).get(key)
    if not isinstance(item, dict):
        return
    item["prompt_text"] = prompt_text
    save_approvals(data)


def find_history(aid: str) -> dict[str, Any] | None:
    key = lookup_id(aid)
    for item in reversed(load_approvals().get("history") or []):
        if str(item.get("id") or "") in {aid, key}:
            return item
    return None


def resolve(aid: str, status: str) -> dict[str, Any] | None:
    data = load_approvals()
    key = lookup_id(aid)
    item = data.get("pending", {}).pop(key, None) or data.get("pending", {}).pop(aid, None)
    if not item:
        return None
    item["status"] = status
    item["resolved"] = _now()
    data.setdefault("history", []).append(item)
    # keep history bounded
    if len(data["history"]) > 200:
        data["history"] = data["history"][-200:]
    save_approvals(data)
    return item
