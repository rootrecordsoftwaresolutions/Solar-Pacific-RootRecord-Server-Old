#!/usr/bin/env python3
"""Operator notes. Local store only. No invented content."""
from __future__ import annotations

import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Any

STORE = Path(
    os.environ.get(
        "NOTES_STORE",
        str(Path.home() / ".ollama" / "skills" / "notes" / "store" / "notes.json"),
    )
)
BODY_CAP = 2000
MAX_NOTES = 2000
TRIGGER_RE = re.compile(
    r"(?:please\s+)?(?:add|make)\s+a?\s*note\b[\s:,—\-]*",
    re.I,
)


def _now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime()) + "-10:00"


def _now_id() -> str:
    return time.strftime("%Y%m%d-%H%M%S", time.localtime())


def _load() -> dict[str, Any]:
    if not STORE.is_file():
        return {"updated": _now(), "notes": []}
    try:
        data = json.loads(STORE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"updated": _now(), "notes": []}
    if not isinstance(data, dict):
        return {"updated": _now(), "notes": []}
    notes = data.get("notes")
    if not isinstance(notes, list):
        notes = []
    data["notes"] = notes
    return data


def _save(data: dict[str, Any]) -> None:
    STORE.parent.mkdir(parents=True, exist_ok=True)
    data = dict(data)
    data["updated"] = _now()
    notes = data.get("notes") if isinstance(data.get("notes"), list) else []
    data["notes"] = notes[-MAX_NOTES:]
    tmp = STORE.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    tmp.replace(STORE)


def extract_body(raw: str) -> str:
    """Pull note text from a sentence that contains an add-note trigger."""
    text = (raw or "").strip()
    if not text:
        return ""
    m = TRIGGER_RE.search(text)
    if m:
        text = text[m.end() :]
    text = text.strip(" \t\n\r\"'`")
    # "add a note that the pump failed" → "the pump failed"
    if text.lower().startswith("that "):
        text = text[5:].lstrip()
    return text[:BODY_CAP]


def add_note(body: str, *, source: str = "chat") -> dict[str, Any]:
    body = (body or "").strip()[:BODY_CAP]
    if not body:
        return {"ok": False, "error": "empty"}
    data = _load()
    notes = [n for n in (data.get("notes") or []) if isinstance(n, dict)]
    nid = _now_id()
    # Avoid id collision if two adds land in the same second.
    existing = {str(n.get("id") or "") for n in notes}
    if nid in existing:
        for i in range(2, 100):
            candidate = f"{nid}-{i}"
            if candidate not in existing:
                nid = candidate
                break
    row = {
        "id": nid,
        "body": body,
        "created": _now(),
        "source": (source or "chat")[:40],
    }
    notes.append(row)
    data["notes"] = notes
    _save(data)
    return {"ok": True, "id": nid, "body": body}


def list_notes(*, limit: int = 40) -> list[dict[str, Any]]:
    notes = [n for n in (_load().get("notes") or []) if isinstance(n, dict)]
    return notes[-max(1, limit) :]


def show_note(note_id: str) -> dict[str, Any] | None:
    want = (note_id or "").strip()
    if not want:
        return None
    for n in _load().get("notes") or []:
        if isinstance(n, dict) and str(n.get("id") or "") == want:
            return n
    for n in _load().get("notes") or []:
        if isinstance(n, dict) and str(n.get("id") or "").startswith(want):
            return n
    return None


def search_notes(query: str, *, limit: int = 40) -> list[dict[str, Any]]:
    q = (query or "").strip().lower()
    if not q:
        return list_notes(limit=limit)
    hits: list[dict[str, Any]] = []
    for n in _load().get("notes") or []:
        if not isinstance(n, dict):
            continue
        blob = f"{n.get('id', '')} {n.get('body', '')}".lower()
        if q in blob:
            hits.append(n)
    return hits[-max(1, limit) :]


def prompt_lines(*, limit: int = 20, cap: int = 2500) -> str:
    notes = list_notes(limit=limit)
    if not notes:
        return "No notes saved yet."
    lines = [f"Notes (last {len(notes)}):"]
    for n in notes:
        lines.append(f"- {n.get('id')}: {n.get('body')}")
    blob = "\n".join(lines)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def main(argv: list[str] | None = None) -> int:
    args = list(argv if argv is not None else sys.argv[1:])
    if not args or args[0] in {"list", "recent", "show-all"}:
        print(prompt_lines())
        return 0
    if args[0] == "add":
        if len(args) > 1:
            body = " ".join(args[1:])
        else:
            body = sys.stdin.read()
        # Allow full sentences: extract after trigger if present.
        extracted = extract_body(body)
        if TRIGGER_RE.search(body) and extracted:
            body = extracted
        print(json.dumps(add_note(body, source="cli")))
        return 0
    if args[0] == "extract" and len(args) > 1:
        print(extract_body(" ".join(args[1:])))
        return 0
    if args[0] == "show" and len(args) > 1:
        row = show_note(args[1])
        if not row:
            print(json.dumps({"ok": False, "error": "missing"}))
            return 1
        print(json.dumps(row, indent=2))
        return 0
    if args[0] == "search" and len(args) > 1:
        hits = search_notes(" ".join(args[1:]))
        if not hits:
            print("No matching notes.")
            return 0
        for n in hits:
            print(f"- {n.get('id')}: {n.get('body')}")
        return 0
    print(json.dumps({"ok": False, "error": "usage: add|list|recent|show|search|extract"}))
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
