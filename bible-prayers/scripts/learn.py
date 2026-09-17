#!/usr/bin/env python3
"""Silent Bible-desk learning. Refs and gaps only — never store what a person confessed."""
from __future__ import annotations

import json
import re
import time
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
STORE = HERE.parent / "store" / "learn"
EVENTS = STORE / "events.jsonl"
ALIASES_PATH = STORE / "aliases.json"
GAPS_PATH = STORE / "gaps.json"
MAX_EVENTS = 8000
PROMOTE_AFTER = 3

REF_FIND = re.compile(
    r"""
    \b
    (?P<book>(?:[123]\s+|I{1,3}\s+|1st\s+|2nd\s+|3rd\s+)?[A-Za-z][A-Za-z']{1,20}(?:\s+[A-Za-z][A-Za-z']{1,16}){0,3})
    \s+
    (?P<chapter>\d{1,3})
    \s*:\s*
    (?P<start>\d{1,3})
    (?:\s*[-–]\s*(?P<end>\d{1,3}))?
    \b
    """,
    re.I | re.X,
)
CORRECTION_RE = re.compile(
    r"\b(wrong verse|misquote|that's not|that is not|incorrect numbering|"
    r"should (?:be|read|say)|verse is actually|not what it says)\b",
    re.I,
)
VERSION_ASK = {
    "niv": "copyrighted",
    "esv": "copyrighted",
    "nlt": "copyrighted",
    "nasb": "copyrighted",
    "nkjv": "copyrighted",
    "csb": "copyrighted",
    "amp": "copyrighted",
    "msg": "copyrighted",
    "the message": "copyrighted",
    "kjv": "KJV",
    "king james": "KJV",
    "authorized": "KJV",
    "ylt": "YLT",
    "young": "YLT",
    "asv": "ASV",
    "bbe": "BBE",
    "basic english": "BBE",
    "darby": "Darby",
    "web": "WEB",
    "webu": "WEBU",
    "world english": "WEBU",
    "cpdv": "CPDV",
    "catholic": "CPDV",
    "douay": "CPDV",
    "vulgate": "CPDV",
    "wlc": "WLC",
    "hebrew": "WLC",
    "masoretic": "WLC",
    "byz": "Byz",
    "byzantine": "Byz",
    "tr": "TR",
    "textus receptus": "TR",
    "greek": "Byz",
    "enoch": "Enoch",
    "charles": "Enoch",
    "ethiopic": "Enoch",
    "ethiopian": "Enoch",
    "geez": "Enoch",
    "ge'ez": "Enoch",
    "lxx": "missing",
    "septuagint": "missing",
}
SEED_ALIASES = {
    "tobit": "Tobit",
    "judith": "Judith",
    "wisdom": "Wisdom",
    "wisdom of solomon": "Wisdom",
    "sirach": "Sirach",
    "ecclesiasticus": "Sirach",
    "baruch": "Baruch",
    "1 maccabees": "1 Maccabees",
    "2 maccabees": "2 Maccabees",
    "i maccabees": "1 Maccabees",
    "ii maccabees": "2 Maccabees",
    "1 mac": "1 Maccabees",
    "2 mac": "2 Maccabees",
    "prayer of manasses": "Prayer of Manasses",
    "prayer of manasseh": "Prayer of Manasses",
    "1 esdras": "1 Esdras",
    "2 esdras": "2 Esdras",
    "i esdras": "1 Esdras",
    "ii esdras": "2 Esdras",
    "laodiceans": "Laodiceans",
    "ethiopian enoch": "Enoch",
    "ethiopic": "Enoch",
    "1 enoch": "Enoch",
    "jubilees": "Jubilees",
}


def _ensure() -> None:
    STORE.mkdir(parents=True, exist_ok=True)
    if not ALIASES_PATH.is_file():
        ALIASES_PATH.write_text(json.dumps(SEED_ALIASES, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def aliases() -> dict[str, str]:
    _ensure()
    try:
        data = json.loads(ALIASES_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        data = {}
    out = dict(SEED_ALIASES)
    if isinstance(data, dict):
        for k, v in data.items():
            if isinstance(k, str) and isinstance(v, str) and k.strip() and v.strip():
                out[k.strip().lower()] = v.strip()
    return out


def note(*, kind: str, ref: str = "", book: str = "", version: str = "", ok: bool | None = None, surface: str = "") -> None:
    """Append one event. No message body. No names."""
    _ensure()
    row = {
        "at": int(time.time()),
        "kind": (kind or "event")[:40],
        "ref": (ref or "")[:80],
        "book": (book or "")[:60],
        "version": (version or "")[:20],
        "ok": ok,
        "surface": (surface or "")[:32],
    }
    try:
        with EVENTS.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")
    except OSError:
        return
    _trim()


def _trim() -> None:
    try:
        lines = EVENTS.read_text(encoding="utf-8").splitlines()
    except OSError:
        return
    if len(lines) <= MAX_EVENTS:
        return
    EVENTS.write_text("\n".join(lines[-MAX_EVENTS:]) + "\n", encoding="utf-8")


def extract_refs(text: str) -> list[str]:
    out: list[str] = []
    for m in REF_FIND.finditer(text or ""):
        book = re.sub(r"\s+", " ", m.group("book")).strip()
        stop = {
            "try", "and", "see", "read", "from", "in", "the", "wait", "no", "of",
            "please", "check", "look", "at", "vs", "verse", "also", "then", "but",
        }
        parts = book.split()
        while parts and parts[0].lower() in stop:
            parts = parts[1:]
        book = " ".join(parts)
        if not book:
            continue
        if book.lower() in {"chapter", "see", "read", "from", "in"}:
            continue
        if book.lower() == "psalm":
            book = "Psalms"
        ch = m.group("chapter")
        start = m.group("start")
        end = m.group("end")
        ref = f"{book} {ch}:{start}" + (f"-{end}" if end else "")
        out.append(ref)
    return out[:8]


def versions_named(text: str) -> list[tuple[str, str]]:
    blob = (text or "").lower()
    found: list[tuple[str, str]] = []
    for needle, mapped in VERSION_ASK.items():
        if needle in blob:
            found.append((needle, mapped))
    return found


def looks_like_correction(text: str) -> bool:
    return bool(CORRECTION_RE.search(text or ""))


def apply() -> dict[str, int]:
    """Promote repeated misses into aliases. Write gap counts. Silent."""
    _ensure()
    current = aliases()
    miss_books: Counter[str] = Counter()
    missing_versions: Counter[str] = Counter()
    copyrighted: Counter[str] = Counter()
    if EVENTS.is_file():
        for line in EVENTS.read_text(encoding="utf-8").splitlines():
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                continue
            kind = str(row.get("kind") or "")
            book = str(row.get("book") or "").strip().lower()
            ver = str(row.get("version") or "")
            if kind == "miss" and book:
                miss_books[book] += 1
            if kind == "version" and ver == "copyrighted":
                copyrighted[str(row.get("ref") or book)[:40]] += 1
            if kind == "version" and ver == "missing":
                missing_versions[str(row.get("ref") or book)[:40]] += 1
    promoted = 0
    known = {v.lower(): v for v in current.values()}
    for raw, n in miss_books.items():
        if n < PROMOTE_AFTER or raw in current:
            continue
        guess = known.get(raw) or known.get(raw.replace(" ", ""))
        if not guess:
            continue
        current[raw] = guess
        promoted += 1
    ALIASES_PATH.write_text(json.dumps(current, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    gaps = {
        "miss_books": miss_books.most_common(40),
        "copyrighted_asks": copyrighted.most_common(20),
        "missing_versions": missing_versions.most_common(20),
        "promoted": promoted,
    }
    GAPS_PATH.write_text(json.dumps(gaps, indent=2) + "\n", encoding="utf-8")
    return {"events_ok": 1, "promoted": promoted, "miss_kinds": len(miss_books)}


def summary() -> str:
    apply()
    gaps = {}
    if GAPS_PATH.is_file():
        try:
            gaps = json.loads(GAPS_PATH.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            gaps = {}
    misses = gaps.get("miss_books") or []
    lines = [f"learn aliases={len(aliases())} promoted={gaps.get('promoted') or 0}"]
    if misses:
        lines.append("top misses: " + ", ".join(f"{k}×{v}" for k, v in misses[:8]))
    copy = gaps.get("copyrighted_asks") or []
    if copy:
        lines.append("copyrighted asks (not stored): " + ", ".join(f"{k}×{v}" for k, v in copy[:6]))
    return "\n".join(lines)


def main() -> int:
    import argparse

    p = argparse.ArgumentParser(description="Silent Bible learn store")
    p.add_argument("--apply", action="store_true")
    p.add_argument("--summary", action="store_true")
    args = p.parse_args()
    if args.apply or args.summary:
        print(summary())
        return 0
    p.print_help()
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
