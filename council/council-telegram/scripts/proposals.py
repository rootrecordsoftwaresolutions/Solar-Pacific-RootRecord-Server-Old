"""Plan-proposal files from council chatlogs. Ideas only — Cursor waits for /approve.

Bruce owns these files. One open daily plan (HST): rounds append, they never
rewrite the body. A new file is created only after the last one is finished
(/done, or a new HST day). Human notes and recovered transcripts append.
"""
from __future__ import annotations

import json
import re
import time
import uuid
from pathlib import Path
from typing import Any

from . import approval, chatlog
from .config import RUNS_DIR

PROPOSALS_DIR = RUNS_DIR / "proposals"
INDEX_PATH = RUNS_DIR / "proposals.json"
TRANSCRIPT_CAP = 40_000
LINE_CHARS = 2000
RECOVERED_MARK = "## Recovered transcript"
CLOSED_MARK = "## Closed"
CHANNEL_MARK = "## Channel backfill"


def _now() -> int:
    return int(time.time())


def new_plan_id() -> str:
    return "plan-" + uuid.uuid4().hex[:8]


def load_index() -> dict[str, Any]:
    if not INDEX_PATH.is_file():
        return {"items": {}}
    try:
        data = json.loads(INDEX_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"items": {}}
    if not isinstance(data.get("items"), dict):
        data["items"] = {}
    return data


def save_index(data: dict[str, Any]) -> None:
    RUNS_DIR.mkdir(parents=True, exist_ok=True)
    tmp = INDEX_PATH.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    tmp.replace(INDEX_PATH)


def get(pid: str) -> dict[str, Any] | None:
    pid = normalize_id(pid)
    if not pid:
        return None
    item = load_index().get("items", {}).get(pid)
    return item if isinstance(item, dict) else None


def normalize_id(raw: str) -> str:
    s = (raw or "").strip().lstrip("/")
    if s.lower().startswith("approve "):
        s = s.split(None, 1)[-1]
    s = s.strip()
    if not s:
        return ""
    if s.startswith("plan-"):
        return s
    return f"plan-{s}" if len(s) <= 12 and s.replace("-", "").isalnum() else s


def by_telegram_message(message_id: int | str | None) -> dict[str, Any] | None:
    if message_id is None:
        return None
    mid = str(message_id)
    for item in load_index().get("items", {}).values():
        if not isinstance(item, dict):
            continue
        if str(item.get("telegram_message_id") or "") == mid:
            return item
    return None


def latest_for_chat(chat_id: int | str) -> dict[str, Any] | None:
    cid = str(chat_id)
    items = [
        it
        for it in load_index().get("items", {}).values()
        if isinstance(it, dict) and str(it.get("chat_id") or "") == cid
    ]
    if not items:
        return None
    return max(items, key=lambda r: int(r.get("created") or 0))


def proposal_path(pid: str) -> Path:
    return PROPOSALS_DIR / f"{pid}.md"


def today_iso(now=None) -> str:
    from .boot_brief import today_iso as _today

    return _today(now)


def open_daily(chat_id: int | str, *, day: str | None = None) -> dict[str, Any] | None:
    cid = str(chat_id)
    want = day or today_iso()
    cands: list[dict[str, Any]] = []
    for it in load_index().get("items", {}).values():
        if not isinstance(it, dict):
            continue
        if str(it.get("chat_id") or "") != cid:
            continue
        if it.get("frozen"):
            continue
        if str(it.get("day") or "") != want:
            continue
        cands.append(it)
    if not cands:
        return None
    return max(cands, key=lambda r: int(r.get("created") or 0))


def _freeze_other_days(chat_id: int | str, day: str) -> None:
    cid = str(chat_id)
    data = load_index()
    for it in list(data.get("items", {}).values()):
        if not isinstance(it, dict):
            continue
        if str(it.get("chat_id") or "") != cid:
            continue
        if it.get("frozen"):
            continue
        other = str(it.get("day") or "")
        if other and other != day:
            freeze(str(it.get("id") or ""), successor_id="")


def _read_body(pid: str) -> str:
    path = proposal_path(pid)
    if not path.is_file():
        return ""
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return ""


def _append_section(path: Path, heading: str, body: str, *, once: bool = True) -> None:
    extra = (body or "").strip()
    if not extra:
        return
    heading = heading.strip()
    prev = ""
    if path.is_file():
        prev = path.read_text(encoding="utf-8")
        if once and heading and heading in prev:
            return
    block = f"\n{heading}\n\n{extra}\n" if heading else f"\n{extra}\n"
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as f:
        if prev and not prev.endswith("\n"):
            f.write("\n")
        f.write(block)


def transcript(
    chat_id: int | str,
    *,
    since_ts: int | None = None,
    until_ts: int | None = None,
) -> str:
    rows = chatlog.for_chat(chat_id, since_ts=since_ts, until_ts=until_ts)
    blob = chatlog.format_history(rows, cap=TRANSCRIPT_CAP, line_chars=LINE_CHARS)
    return blob or "(empty thread)"


def _window_for_new(chat_id: int | str, predecessor_id: str = "") -> tuple[int | None, int | None]:
    pred = get(predecessor_id) if predecessor_id else None
    if pred and pred.get("created"):
        return int(pred["created"]), _now()
    prev = latest_for_chat(chat_id)
    if prev and prev.get("created"):
        return int(prev["created"]), _now()
    return None, _now()


def render_markdown(
    *,
    pid: str,
    origin: str,
    chat_id: int | str,
    notes: str = "",
    predecessor_id: str = "",
    since_ts: int | None = None,
    until_ts: int | None = None,
) -> str:
    origin = (origin or "").strip() or "(no origin)"
    notes = (notes or "").strip()
    pred = (predecessor_id or "").strip()
    body = (
        f"# Council proposal `{pid}`\n\n"
        f"Ideas and features only. No production code in this file.\n"
        f"Owner: `/approve {pid}` sends this to the implementer. Humans: reply to the Telegram file.\n"
        f"Daily plan: Bruce amends this file in place. A new file is created only after `/done` or a new HST day.\n\n"
        f"## Ask\n\n{origin}\n\n"
    )
    if pred:
        body += (
            f"## Continues\n\n"
            f"Finished predecessor `{pred}` stays on disk. Bruce does not rewrite it.\n\n"
        )
    body += f"## Chatlog\n\n```\n{transcript(chat_id, since_ts=since_ts, until_ts=until_ts)}\n```\n"
    if notes:
        body += f"\n## Human notes\n\n{notes}\n"
    body += (
        "\n## Build later (implementer, after approve)\n\n"
        "Implement from this chatlog and the ask. Stay in Ava-Core. "
        "Live numbers only from the desk block.\n"
    )
    return body


def conclude(
    *,
    chat_id: int | str,
    origin: str,
    thread_id: str = "",
    predecessor_id: str = "",
    notes: str = "",
) -> dict[str, Any]:
    pid = new_plan_id()
    pred = normalize_id(predecessor_id) if predecessor_id else ""
    if pred and not get(pred):
        pred = ""
    since_ts, until_ts = _window_for_new(chat_id, pred)
    PROPOSALS_DIR.mkdir(parents=True, exist_ok=True)
    path = proposal_path(pid)
    if path.exists():
        pid = new_plan_id()
        path = proposal_path(pid)
    text = render_markdown(
        pid=pid,
        origin=origin,
        chat_id=chat_id,
        notes=notes,
        predecessor_id=pred,
        since_ts=since_ts,
        until_ts=until_ts,
    )
    path.write_text(text, encoding="utf-8")
    approval.create_approval(
        prompt_text=text[:20000],
        user_ask=origin,
        run_id=pid,
        aid=pid,
    )
    rec = {
        "id": pid,
        "created": _now(),
        "chat_id": str(chat_id),
        "thread_id": thread_id,
        "path": str(path),
        "origin": (origin or "")[:800],
        "telegram_message_id": "",
        "notes": notes[:4000] if notes else "",
        "predecessor_id": pred,
        "frozen": False,
        "day": today_iso(),
        "amendments": 0,
        "amended": _now(),
    }
    data = load_index()
    data["items"][pid] = rec
    save_index(data)
    return rec


def freeze(pid: str, *, successor_id: str = "", reason: str = "") -> None:
    pid = normalize_id(pid)
    data = load_index()
    item = data.get("items", {}).get(pid)
    if not isinstance(item, dict):
        return
    item["frozen"] = True
    if successor_id:
        item["successor_id"] = successor_id
    save_index(data)
    path = proposal_path(pid)
    if not path.is_file():
        return
    suc = f" Succeeded by `{successor_id}`." if successor_id else ""
    body = (
        reason
        or "Finished. Bruce will not rewrite this file. Next round may open a new daily plan."
    ).rstrip()
    _append_section(
        path,
        CLOSED_MARK,
        f"{body}{suc}",
    )


def amend(
    pid: str,
    *,
    origin: str,
    chat_id: int | str,
    notes: str = "",
) -> dict[str, Any] | None:
    data = load_index()
    item = data.get("items", {}).get(normalize_id(pid))
    if not isinstance(item, dict) or item.get("frozen"):
        return None
    n = int(item.get("amendments") or 0) + 1
    last = int(item.get("amended") or item.get("created") or 0)
    blob = transcript(chat_id, since_ts=last, until_ts=_now())
    extra = f"Ask:\n{(origin or '').strip() or '(no origin)'}\n\n```\n{blob}\n```"
    if (notes or "").strip():
        extra += f"\n\nNotes:\n{notes.strip()}"
    path = proposal_path(str(item.get("id")))
    _append_section(path, f"## Amendment {n}", extra, once=False)
    item["amendments"] = n
    item["amended"] = _now()
    item["path"] = str(path)
    save_index(data)
    pending = approval.get_pending(str(item.get("id")))
    if pending and path.is_file():
        approval.update_pending_prompt(str(item.get("id")), path.read_text(encoding="utf-8")[:20000])
    return dict(item)


def publish(
    *,
    chat_id: int | str,
    origin: str,
    thread_id: str = "",
    predecessor_id: str = "",
    notes: str = "",
) -> dict[str, Any]:
    """Amend today's open daily plan, or create one. Never rewrite a finished file."""
    day = today_iso()
    _freeze_other_days(chat_id, day)
    cur = open_daily(chat_id, day=day)
    if cur:
        rec = amend(str(cur["id"]), origin=origin, chat_id=chat_id, notes=notes)
        if rec:
            rec["amended_this_round"] = True
            return rec
    pred = ""
    if predecessor_id:
        pred = normalize_id(predecessor_id)
    elif cur is None:
        prev = latest_for_chat(chat_id)
        if prev and prev.get("frozen"):
            pred = str(prev.get("id") or "")
    rec = conclude(
        chat_id=chat_id,
        origin=origin,
        thread_id=thread_id,
        predecessor_id=pred,
        notes=notes,
    )
    rec["amended_this_round"] = False
    return rec


def backfill_channel(chat_id: int | str, pid: str | None = None) -> str:
    """Append the full group thread onto today's daily plan once — no rewrite."""
    rec = get(pid) if pid else open_daily(chat_id)
    if not rec:
        rec = publish(
            chat_id=chat_id,
            origin="Daily proposal — channel backfill and open asks.",
        )
    pid = str(rec.get("id") or "")
    path = proposal_path(pid)
    existing = path.read_text(encoding="utf-8") if path.is_file() else ""
    if CHANNEL_MARK in existing:
        return pid
    asks: list[str] = []
    for r in chatlog.for_chat(chat_id):
        if r.get("dir") != "in":
            continue
        who = r.get("display") or r.get("username") or "human"
        text = str(r.get("text") or "").replace("\n", " ").strip()
        if text:
            asks.append(f"- {who}: {text}")
    blob = transcript(chat_id)
    _append_section(
        path,
        CHANNEL_MARK,
        "Human asks from the Telegram group (not rewritten; appended):\n\n"
        + ("\n".join(asks) if asks else "(none)")
        + "\n\nFull thread:\n\n```\n"
        + blob
        + "\n```",
        once=True,
    )
    pending = approval.get_pending(pid)
    if pending and path.is_file():
        approval.update_pending_prompt(pid, path.read_text(encoding="utf-8")[:20000])
    return pid


def remember_telegram_message(pid: str, message_id: int | str) -> None:
    data = load_index()
    item = data.get("items", {}).get(pid)
    if not isinstance(item, dict):
        return
    item["telegram_message_id"] = str(message_id)
    save_index(data)


def append_human_note(pid: str, note: str, chat_id: int | str) -> dict[str, Any] | None:
    del chat_id
    data = load_index()
    item = data.get("items", {}).get(pid)
    if not isinstance(item, dict):
        return None
    blob_note = (note or "").strip()
    if not blob_note:
        return item
    prev = str(item.get("notes") or "")
    item["notes"] = (prev + "\n" + blob_note).strip()[:4000]
    path = proposal_path(pid)
    existing = path.read_text(encoding="utf-8") if path.is_file() else ""
    heading = "## Human notes (append)" if "## Human notes (append)" not in existing else ""
    _append_section(path, heading, blob_note, once=False)
    save_index(data)
    pending = approval.get_pending(pid)
    if pending and path.is_file():
        approval.update_pending_prompt(pid, path.read_text(encoding="utf-8")[:20000])
    return item


def backfill_rewritten(chat_id: int | str | None = None) -> list[str]:
    """Append recovered full-line transcripts onto plans that were filed from a sliding window."""
    data = load_index()
    items = [it for it in data.get("items", {}).values() if isinstance(it, dict)]
    if chat_id:
        cid = str(chat_id)
        items = [it for it in items if str(it.get("chat_id") or "") == cid]
    items.sort(key=lambda r: int(r.get("created") or 0))
    touched: list[str] = []
    for i, item in enumerate(items):
        pid = str(item.get("id") or "")
        if not pid:
            continue
        path = proposal_path(pid)
        if not path.is_file():
            continue
        existing = path.read_text(encoding="utf-8")
        if RECOVERED_MARK in existing:
            continue
        cid = str(item.get("chat_id") or chat_id or "")
        if not cid:
            continue
        since = int(items[i - 1]["created"]) if i else None
        until = int(item.get("created") or 0) or None
        recovered = transcript(cid, since_ts=since, until_ts=until)
        if recovered == "(empty thread)":
            continue
        _append_section(
            path,
            RECOVERED_MARK,
            "Full lines from the group log for this filing window (not the truncated rewrite):\n\n"
            f"```\n{recovered}\n```",
        )
        item["frozen"] = True
        item["backfilled"] = True
        touched.append(pid)
    save_index(data)
    return touched


def list_pending_ids() -> list[str]:
    data = approval.load_approvals()
    items = data.get("pending") or {}
    keys = sorted(items.keys(), key=lambda k: int(items[k].get("created") or 0), reverse=True)
    return keys


def looks_owner_implemented(text: str) -> bool:
    """Owner said the proposals are already in — not a request to build them."""
    low = (text or "").strip().lower()
    if not low:
        return False
    if re.search(
        r"\b(please|can you|could you|should we|let's|lets|need to|going to|gonna|will|wanna)\b.{0,40}\b(implement|add|ship|merge)\b",
        low,
    ):
        return False
    has_obj = bool(re.search(r"\b(proposal|proposals|plans|daily plan|plan-[a-z0-9]+)\b", low))
    has_backend = bool(re.search(r"\b(backend|codebase|repo|origin|ava-core)\b", low))
    i_did = bool(
        re.search(
            r"\bi(?:['’]ve| have)?\s+(?:just\s+)?(?:implemented|shipped|merged|wired|built|added|landed|pushed|finished)\b",
            low,
        )
    )
    already = bool(re.search(r"\balready\s+(?:implemented|shipped|merged|added|done|in)\b", low))
    they_are = bool(
        re.search(
            r"\b(?:proposal|plan)s?\b.{0,48}\b(?:are|is|were)\s+(?:in|done|live|shipped|implemented|merged)\b",
            low,
        )
    )
    if they_are:
        return True
    if already and (has_obj or has_backend):
        return True
    if i_did and (has_obj or has_backend):
        return True
    return False


def clear_implemented(chat_id: int | str) -> list[str]:
    """Freeze this chat’s open plans and drop pending Cursor approvals. No Cursor launch."""
    cid = str(chat_id)
    cleared: list[str] = []
    data = load_index()
    for it in list((data.get("items") or {}).values()):
        if not isinstance(it, dict):
            continue
        if str(it.get("chat_id") or "") != cid:
            continue
        pid = str(it.get("id") or "")
        if not pid:
            continue
        if not it.get("frozen"):
            freeze(
                pid,
                reason="Owner implemented these already. Not sending to the implementer. Next round may open a new daily plan.",
            )
        if pid not in cleared:
            cleared.append(pid)
    pending_ids = list(list_pending_ids())
    for aid in pending_ids:
        rec = get(aid)
        if rec and str(rec.get("chat_id") or "") not in {"", cid}:
            continue
        if approval.resolve(aid, "implemented"):
            if aid not in cleared:
                cleared.append(aid)
    return cleared
