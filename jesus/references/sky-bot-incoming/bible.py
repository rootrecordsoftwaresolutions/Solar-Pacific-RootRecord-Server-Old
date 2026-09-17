#!/usr/bin/env python3
"""Load public-domain Bible CSVs (English + Hebrew WLC + Greek Byz/TR)."""
from __future__ import annotations

import csv
from pathlib import Path

BIBLES_DIR = Path(__file__).resolve().parent / "bibles"
VERSIONS = ("WLC", "Byz", "TR", "YLT", "KJV", "WEB", "ASV", "BBE", "Darby", "CPDV", "Enoch")


def _key(book: str, chapter: int, verse: int) -> str:
    return f"{book}|{chapter}|{verse}"


def load_version(abbr: str) -> dict[str, str]:
    path = BIBLES_DIR / f"{abbr}.csv"
    out: dict[str, str] = {}
    if not path.is_file():
        return out
    with path.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            book = (row.get("Book") or "").strip()
            try:
                ch = int(row.get("Chapter") or 0)
                vs = int(row.get("Verse") or 0)
            except ValueError:
                continue
            text = (row.get("Text") or "").strip()
            if book and ch and vs and text:
                out[_key(book, ch, vs)] = text
    return out


def load_all() -> dict[str, dict[str, str]]:
    return {abbr: load_version(abbr) for abbr in VERSIONS}


def parse_ref(ref: str) -> tuple[str, int, int] | None:
    ref = (ref or "").strip()
    if " " not in ref or ":" not in ref:
        return None
    book, rest = ref.rsplit(" ", 1)
    if ":" not in rest:
        return None
    ch_s, vs_s = rest.split(":", 1)
    try:
        return book.strip(), int(ch_s), int(vs_s)
    except ValueError:
        return None


def render_ref(store: dict[str, dict[str, str]], ref: str, versions: tuple[str, ...] | None = None) -> list[str]:
    parsed = parse_ref(ref)
    if not parsed:
        return []
    book, ch, vs = parsed
    key = _key(book, ch, vs)
    lines = []
    for abbr in versions or VERSIONS:
        text = (store.get(abbr) or {}).get(key)
        if text:
            lines.append(f"{ref} ({abbr}) — {text}")
    return lines
