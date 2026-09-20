"""One-at-a-time Ollama job queue for public-safe Telegram council."""
from __future__ import annotations

import json
import threading
import time
import uuid
from pathlib import Path
from typing import Any

from .config import CONFIG_DIR

QUEUE_PATH = CONFIG_DIR / "queue.json"
_lock = threading.RLock()

MAX_LOOP_DEPTH = 6
MAX_QUEUE = 30
MAX_KEEP_DONE = 12
MAX_PENDING_SPEAK = 6


def _now() -> int:
    return int(time.time())


def load() -> dict[str, Any]:
    with _lock:
        if not QUEUE_PATH.is_file():
            return {"jobs": [], "updated": _now()}
        try:
            data = json.loads(QUEUE_PATH.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            data = {}
        if not isinstance(data.get("jobs"), list):
            data["jobs"] = []
        return data


def save(data: dict[str, Any]) -> None:
    with _lock:
        data = dict(data)
        data["updated"] = _now()
        QUEUE_PATH.parent.mkdir(parents=True, exist_ok=True)
        tmp = QUEUE_PATH.with_suffix(".tmp")
        tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        tmp.replace(QUEUE_PATH)


def prune(data: dict[str, Any]) -> dict[str, Any]:
    """Keep queued/running plus a short done tail so history cannot block a round chain."""
    jobs = data.get("jobs") if isinstance(data.get("jobs"), list) else []
    live = [j for j in jobs if j.get("status") in ("queued", "running")]
    done = [j for j in jobs if j.get("status") not in ("queued", "running")][-MAX_KEEP_DONE:]
    data["jobs"] = live + done
    return data


def enqueue(
    *,
    voice: str,
    prompt: str,
    chat_id: int | str,
    reply_to: int | None = None,
    source_message_id: int | None = None,
    thread_id: str | None = None,
    depth: int = 0,
    kind: str = "speak",
    meta: dict[str, Any] | None = None,
) -> dict[str, Any] | None:
    voice = (voice or "ava").lower()
    if voice not in ("ava", "bruce", "carly"):
        voice = "ava"
    if depth > MAX_LOOP_DEPTH:
        return None
    data = prune(load())
    pending = [j for j in data["jobs"] if j.get("status") == "queued"]
    live_n = sum(1 for j in data["jobs"] if j.get("status") in ("queued", "running"))
    if source_message_id is not None:
        for j in data["jobs"]:
            if (
                j.get("voice") == voice
                and j.get("source_message_id") == source_message_id
                and j.get("status") in ("queued", "running")
            ):
                return None
    if kind == "speak" and sum(1 for j in pending if j.get("kind") == "speak") >= MAX_PENDING_SPEAK:
        return None
    if live_n >= MAX_QUEUE:
        return None
    job = {
        "id": uuid.uuid4().hex[:10],
        "voice": voice,
        "prompt": (prompt or "")[:4000],
        "chat_id": str(chat_id),
        "reply_to": reply_to,
        "source_message_id": source_message_id,
        "thread_id": thread_id or uuid.uuid4().hex[:8],
        "depth": int(depth),
        "kind": kind,
        "meta": meta or {},
        "created": _now(),
        "status": "queued",
    }
    data["jobs"].append(job)
    save(data)
    return job


def peek() -> dict[str, Any] | None:
    data = load()
    for job in data["jobs"]:
        if job.get("status") == "queued":
            return job
    return None


def mark(job_id: str, status: str) -> None:
    data = load()
    for job in data["jobs"]:
        if job.get("id") == job_id:
            job["status"] = status
            job["updated"] = _now()
    prune(data)
    save(data)


def pending_count() -> int:
    return sum(1 for j in load()["jobs"] if j.get("status") == "queued")


def drop_brainstorm_continue_jobs() -> int:
    """On wrap/stop: drop queued thought-session rounds that are not the wrap round."""
    n = 0
    data = load()
    now = _now()
    for job in data["jobs"]:
        if job.get("status") not in ("queued", "running"):
            continue
        tid = str(job.get("thread_id") or "")
        meta = job.get("meta") if isinstance(job.get("meta"), dict) else {}
        if meta.get("brainstorm_wrap"):
            continue
        if tid.startswith("brainstorm-") and not tid.startswith("brainstorm-wrap-"):
            job["status"] = "failed"
            job["updated"] = now
            n += 1
    if n:
        save(data)
    return n


def drop_ceremony_jobs() -> int:
    """Fail leftover desk-session / boot-brief jobs so recycle cannot speak."""
    n = 0
    data = load()
    now = _now()
    for job in data["jobs"]:
        if job.get("status") not in ("queued", "running"):
            continue
        tid = str(job.get("thread_id") or "")
        if tid in ("desk-session", "boot-brief"):
            job["status"] = "failed"
            job["updated"] = now
            n += 1
    if n:
        save(data)
    return n


def abandon_stale_running(*, max_age_s: int = 90, force: bool = False) -> int:
    """Jobs left 'running' after a crash/hang never peek() again — fail them."""
    now = _now()
    n = 0
    data = load()
    for job in data["jobs"]:
        if job.get("status") != "running":
            continue
        started = int(job.get("updated") or job.get("created") or 0)
        if force or (now - started) >= int(max_age_s):
            job["status"] = "failed"
            job["updated"] = now
            n += 1
    if n:
        save(data)
    return n
