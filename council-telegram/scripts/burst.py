"""Group back-to-back human lines into one reply. Persist so a 2nd message is not dropped.

Per-user bursts still exist. Same-chat due rows are coalesced into one room flush so
Alexander + Sara talking at once gets a single generalized summary — not a reply per line.
"""
from __future__ import annotations

import json
import threading
import time
from typing import Any, Callable

from .config import CONFIG_DIR

PATH = CONFIG_DIR / "burst.json"
_lock = threading.RLock()

# Quiet window after the last human line before we speak.
WAIT_S = 5.0
# Hard cap so a long pile still flushes.
MAX_S = 20.0
MAX_PARTS = 12
MAX_CHARS = 3200


def _now() -> float:
    return time.time()


def _load() -> dict[str, Any]:
    with _lock:
        if not PATH.is_file():
            return {"pending": {}, "idle": {}}
        try:
            data = json.loads(PATH.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            data = {}
        pending = data.get("pending")
        if not isinstance(pending, dict):
            pending = {}
        data["pending"] = pending
        idle = data.get("idle")
        if not isinstance(idle, dict):
            idle = {}
        data["idle"] = idle
        return data


def _save(data: dict[str, Any]) -> None:
    with _lock:
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        tmp = PATH.with_suffix(".tmp")
        tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        tmp.replace(PATH)


def key_of(chat_id: int | str, user_id: int | str, listen_voice: str) -> str:
    voice = listen_voice if listen_voice in ("ava", "bruce", "carly") else "ava"
    return f"{chat_id}:{user_id}:{voice}"


def stash_idle(
    *,
    chat_id: int | str,
    user_id: int | str,
    listen_voice: str,
    text: str,
    message_id: Any = None,
) -> None:
    now = _now()
    k = key_of(chat_id, user_id, listen_voice)
    data = _load()
    idle = data.setdefault("idle", {})
    row = idle.get(k) if isinstance(idle.get(k), dict) else {"parts": []}
    parts = row.get("parts") if isinstance(row.get("parts"), list) else []
    parts.append({"text": (text or "").strip(), "message_id": message_id, "ts": now})
    row["parts"] = [p for p in parts[-MAX_PARTS:] if now - float(p.get("ts") or 0) <= WAIT_S]
    idle[k] = row
    data["idle"] = idle
    _save(data)


def take_idle(chat_id: int | str, user_id: int | str, listen_voice: str) -> list[dict[str, Any]]:
    now = _now()
    k = key_of(chat_id, user_id, listen_voice)
    data = _load()
    idle = data.get("idle") if isinstance(data.get("idle"), dict) else {}
    row = idle.pop(k, None)
    data["idle"] = idle
    _save(data)
    if not isinstance(row, dict):
        return []
    parts = row.get("parts") if isinstance(row.get("parts"), list) else []
    return [p for p in parts if isinstance(p, dict) and now - float(p.get("ts") or 0) <= WAIT_S]


def has_pending(chat_id: int | str, user_id: int | str, listen_voice: str) -> bool:
    data = _load()
    row = data["pending"].get(key_of(chat_id, user_id, listen_voice))
    if not isinstance(row, dict):
        return False
    parts = row.get("parts")
    return bool(isinstance(parts, list) and parts)


def chat_has_pending(chat_id: int | str) -> bool:
    cid = str(chat_id)
    data = _load()
    for k, row in (data.get("pending") or {}).items():
        if not isinstance(row, dict):
            continue
        if str(row.get("chat_id") or "") == cid:
            parts = row.get("parts")
            if isinstance(parts, list) and parts:
                return True
        if str(k).startswith(f"{cid}:") or str(k).startswith(f"room:{cid}"):
            parts = row.get("parts") if isinstance(row, dict) else None
            if isinstance(parts, list) and parts:
                return True
    return False


def combined_text(row: dict[str, Any]) -> str:
    parts = row.get("parts") if isinstance(row.get("parts"), list) else []
    chunks: list[str] = []
    total = 0
    for p in parts:
        if not isinstance(p, dict):
            continue
        t = str(p.get("text") or "").strip()
        if not t:
            continue
        who = str(p.get("display") or p.get("username") or "").strip()
        line = f"{who}: {t}" if who and len(parts) > 1 else t
        if total + len(line) > MAX_CHARS:
            line = line[: max(0, MAX_CHARS - total)]
        chunks.append(line)
        total += len(line) + 1
        if total >= MAX_CHARS:
            break
    return "\n".join(chunks).strip()


def part_count(row: dict[str, Any]) -> int:
    parts = row.get("parts") if isinstance(row.get("parts"), list) else []
    return len([p for p in parts if isinstance(p, dict) and str(p.get("text") or "").strip()])


def wants_summary(row: dict[str, Any]) -> bool:
    """Multi-line chatter → one generalized answer, not per-message replies."""
    if row.get("summary_mode"):
        return True
    n = part_count(row)
    if n >= 2:
        return True
    text = combined_text(row)
    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
    if len(lines) >= 2:
        return True
    # Several ask-ish clauses in one paste
    if text.count("?") >= 2:
        return True
    return False


def ingest(
    *,
    chat_id: int | str,
    user_id: int | str,
    listen_voice: str,
    private: bool,
    message: dict[str, Any],
    text: str,
    user: dict[str, Any],
) -> dict[str, Any]:
    now = _now()
    voice = listen_voice if listen_voice in ("ava", "bruce", "carly") else "ava"
    k = key_of(chat_id, user_id, voice)
    body = (text or "").strip()
    mid = message.get("message_id")
    data = _load()
    row = data["pending"].get(k)
    if not isinstance(row, dict) or not isinstance(row.get("parts"), list):
        row = {
            "chat_id": chat_id,
            "user_id": user_id,
            "listen_voice": voice,
            "private": bool(private),
            "user": dict(user or {}),
            "message": dict(message or {}),
            "started": now,
            "parts": [],
        }
    row["user"] = dict(user or {})
    row["message"] = dict(message or {})
    row["private"] = bool(private)
    parts = row["parts"]
    idle = data.get("idle") if isinstance(data.get("idle"), dict) else {}
    idle_row = idle.pop(k, None)
    data["idle"] = idle
    if isinstance(idle_row, dict):
        for prior in idle_row.get("parts") or []:
            if isinstance(prior, dict) and prior.get("text") and now - float(prior.get("ts") or 0) <= WAIT_S:
                parts.append(prior)
    display = (
        str((user or {}).get("first_name") or "").strip()
        or str((user or {}).get("username") or "").strip()
        or str(user_id)
    )
    parts.append(
        {
            "text": body,
            "message_id": mid,
            "ts": now,
            "user_id": user_id,
            "username": str((user or {}).get("username") or ""),
            "display": display,
        }
    )
    row["parts"] = parts[-MAX_PARTS:]
    started = float(row.get("started") or now)
    due = now + WAIT_S
    if now - started >= MAX_S or len(row["parts"]) >= MAX_PARTS:
        due = now
    row["due"] = due
    data["pending"][k] = row
    _save(data)
    print(
        f"burst ingest key={k} n={len(row['parts'])} wait={max(0, due - now):.1f}s",
        flush=True,
    )
    return row


def pop_due(*, now: float | None = None) -> list[dict[str, Any]]:
    now = _now() if now is None else now
    data = _load()
    out: list[dict[str, Any]] = []
    keep: dict[str, Any] = {}
    for k, row in (data.get("pending") or {}).items():
        if not isinstance(row, dict):
            continue
        due = float(row.get("due") or 0)
        if due <= now:
            row = dict(row)
            row["_key"] = k
            out.append(row)
        else:
            keep[k] = row
    if out:
        data["pending"] = keep
        _save(data)
    return out


def coalesce_room(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Merge due bursts that share a chat into one summary flush."""
    if len(rows) <= 1:
        if rows and wants_summary(rows[0]):
            rows[0] = dict(rows[0])
            rows[0]["summary_mode"] = True
        return rows
    by_chat: dict[str, list[dict[str, Any]]] = {}
    for row in rows:
        cid = str(row.get("chat_id") or "")
        by_chat.setdefault(cid, []).append(row)
    out: list[dict[str, Any]] = []
    for cid, group in by_chat.items():
        if len(group) == 1 and not wants_summary(group[0]):
            out.append(group[0])
            continue
        if len(group) == 1 and wants_summary(group[0]):
            g0 = dict(group[0])
            g0["summary_mode"] = True
            out.append(g0)
            continue
        # Prefer Ava's listener row as the carrier; else first.
        lead = next((r for r in group if str(r.get("listen_voice")) == "ava"), group[0])
        merged = {
            "chat_id": lead.get("chat_id"),
            "user_id": lead.get("user_id"),
            "listen_voice": str(lead.get("listen_voice") or "ava"),
            "private": False,
            "user": dict(lead.get("user") or {}),
            "message": dict(lead.get("message") or {}),
            "started": min(float(r.get("started") or _now()) for r in group),
            "parts": [],
            "summary_mode": True,
            "_room_merge": True,
        }
        parts: list[dict[str, Any]] = []
        for r in sorted(group, key=lambda x: float(x.get("started") or 0)):
            for p in r.get("parts") or []:
                if isinstance(p, dict) and str(p.get("text") or "").strip():
                    parts.append(dict(p))
        # Stable by ts
        parts.sort(key=lambda p: float(p.get("ts") or 0))
        merged["parts"] = parts[-MAX_PARTS:]
        # Reply target = last human message in the pile
        if merged["parts"]:
            last = merged["parts"][-1]
            msg = dict(merged.get("message") or {})
            if last.get("message_id") is not None:
                msg["message_id"] = last.get("message_id")
            merged["message"] = msg
        out.append(merged)
        print(
            f"burst room-merge chat={cid} rows={len(group)} parts={len(merged['parts'])}",
            flush=True,
        )
    return out


def seconds_until_due() -> float | None:
    data = _load()
    soon: float | None = None
    now = _now()
    for row in (data.get("pending") or {}).values():
        if not isinstance(row, dict):
            continue
        due = float(row.get("due") or 0) - now
        if soon is None or due < soon:
            soon = due
    return soon


def poll_timeout(default: int = 25, *, drain: bool = False) -> int:
    if drain:
        return 0
    sec = seconds_until_due()
    if sec is None:
        return default
    return max(0, min(int(default), int(sec + 0.25)))


def as_update(row: dict[str, Any]) -> dict[str, Any]:
    msg = dict(row.get("message") or {})
    text = combined_text(row)
    msg["text"] = text
    parts = row.get("parts") if isinstance(row.get("parts"), list) else []
    last_id = None
    if parts and isinstance(parts[-1], dict):
        last_id = parts[-1].get("message_id")
    if last_id is not None:
        msg["message_id"] = last_id
    return {"message": msg}


def tick(cfg: Any, handle: Callable[..., Any]) -> int:
    due = coalesce_room(pop_due())
    if not due:
        return 0
    from . import state, trust

    n = 0
    for row in due:
        voice = str(row.get("listen_voice") or "ava")
        upd = as_update(row)
        if not str((upd.get("message") or {}).get("text") or "").strip():
            continue
        st = state.load_state(cfg.state_path)
        trust_data = trust.load_trust(cfg.trust_path)
        n_parts = part_count(row)
        summary = wants_summary(row)
        print(
            f"burst flush voice={voice} n={n_parts} summary={summary} "
            f"chat={row.get('chat_id')}",
            flush=True,
        )
        handle(
            cfg,
            st,
            trust_data,
            upd,
            listen_voice=voice,
            burst_flush=True,
            burst_parts=n_parts,
            burst_summary=summary,
        )
        n += 1
    return n
