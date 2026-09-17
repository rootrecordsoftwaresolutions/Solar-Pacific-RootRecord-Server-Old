"""Council-writable goals. Local store only. No invented USD."""
from __future__ import annotations

import json
import re
import time
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

HST = ZoneInfo("Pacific/Honolulu")
STORE = Path.home() / ".ollama" / "skills" / "goals" / "store" / "council-goals.json"
IDEAS = Path.home() / ".ollama" / "skills" / "goals" / "store" / "skill-ideas"
GOAL_TAG = re.compile(
    r"<<<GOAL\s+(add|done|note)\s+([^>]*?)>>>",
    re.I,
)
SKILLIDEA_TAG = re.compile(r"<<<SKILLIDEA\s+([^>]*?)>>>", re.I)
MAX_GOALS = 40
TITLE_CAP = 80
NOTE_CAP = 280


def _now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime()) + "-10:00"


def _load() -> dict[str, Any]:
    if not STORE.is_file():
        return {"updated": _now(), "rules": [], "goals": []}
    try:
        data = json.loads(STORE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"updated": _now(), "rules": [], "goals": []}
    return data if isinstance(data, dict) else {"updated": _now(), "rules": [], "goals": []}


def _save(data: dict[str, Any]) -> None:
    STORE.parent.mkdir(parents=True, exist_ok=True)
    data = dict(data)
    data["updated"] = _now()
    tmp = STORE.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    tmp.replace(STORE)


def slug(title: str) -> str:
    raw = re.sub(r"[^a-z0-9]+", "-", (title or "").lower()).strip("-")
    return (raw or "goal")[:48]


def list_goals() -> dict[str, Any]:
    return _load()


def prompt_lines(*, cap: int = 700) -> str:
    data = _load()
    lines = ["Council goals (amend in-place; no invented money):"]
    for rule in (data.get("rules") or [])[:4]:
        lines.append(f"- {rule}")
    for g in (data.get("goals") or [])[:8]:
        if not isinstance(g, dict):
            continue
        lines.append(
            f"- {g.get('id')}: {g.get('title')} [{g.get('status')}] {g.get('next') or ''}"
        )
    blob = "\n".join(lines)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def add_goal(title: str, *, note: str = "", voice: str = "council") -> dict[str, Any]:
    title = (title or "").strip()[:TITLE_CAP]
    if not title:
        return {"ok": False, "error": "empty_title"}
    data = _load()
    goals = [g for g in (data.get("goals") or []) if isinstance(g, dict)]
    gid = slug(title)
    for g in goals:
        if g.get("id") == gid:
            g["note"] = (note or g.get("note") or "")[:NOTE_CAP]
            g["updated"] = _now()
            g["updated_by"] = voice
            data["goals"] = goals
            _save(data)
            return {"ok": True, "id": gid, "action": "amended"}
    if len(goals) >= MAX_GOALS:
        return {"ok": False, "error": "cap"}
    goals.append(
        {
            "id": gid,
            "title": title,
            "owner": voice,
            "status": "open",
            "kind": "ops",
            "note": note[:NOTE_CAP],
            "created": _now(),
            "updated_by": voice,
        }
    )
    data["goals"] = goals
    _save(data)
    return {"ok": True, "id": gid, "action": "added"}


def mark_done(goal_id: str, *, voice: str = "council") -> dict[str, Any]:
    data = _load()
    want = slug(goal_id)
    for g in data.get("goals") or []:
        if isinstance(g, dict) and g.get("id") == want:
            g["status"] = "done"
            g["updated"] = _now()
            g["updated_by"] = voice
            _save(data)
            return {"ok": True, "id": want}
    return {"ok": False, "error": "missing"}


def note_goal(goal_id: str, note: str, *, voice: str = "council") -> dict[str, Any]:
    data = _load()
    want = slug(goal_id)
    for g in data.get("goals") or []:
        if isinstance(g, dict) and str(g.get("id") or "").startswith(want[:12]):
            g["note"] = (note or "")[:NOTE_CAP]
            g["updated"] = _now()
            g["updated_by"] = voice
            _save(data)
            return {"ok": True, "id": g.get("id")}
    return {"ok": False, "error": "missing"}


def apply_goal_tags(raw: str, *, voice: str) -> None:
    for kind, body in GOAL_TAG.findall(raw or ""):
        text = (body or "").strip()[:NOTE_CAP]
        k = kind.lower()
        if k == "add":
            add_goal(text, voice=voice)
        elif k == "done":
            mark_done(text, voice=voice)
        elif k == "note":
            gid, _, rest = text.partition(" ")
            note_goal(gid, rest, voice=voice)
    for body in SKILLIDEA_TAG.findall(raw or ""):
        text = (body or "").strip()
        title, _, rest = text.partition("|")
        save_skill_idea(title.strip() or "idea", rest.strip() or title, voice=voice)


def save_skill_idea(title: str, body: str, *, voice: str) -> dict[str, Any]:
    IDEAS.mkdir(parents=True, exist_ok=True)
    name = slug(title) + ".md"
    path = IDEAS / name
    path.write_text(
        f"# {title}\n\nDraft by {voice} at {_now()}. Not live until owner/Cursor promotes.\n\n{body[:4000]}\n",
        encoding="utf-8",
    )
    return {"ok": True, "path": str(path)}


def main(argv: list[str] | None = None) -> int:
    import sys

    args = list(argv if argv is not None else sys.argv[1:])
    if not args or args[0] in {"list", "show"}:
        print(prompt_lines(cap=3500))
        return 0
    if args[0] == "add" and len(args) > 1:
        print(json.dumps(add_goal(" ".join(args[1:]), voice="cli")))
        return 0
    if args[0] == "done" and len(args) > 1:
        print(json.dumps(mark_done(args[1], voice="cli")))
        return 0
    print(json.dumps({"ok": False, "error": "usage"}))
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
