"""Local Hawaiian dictionary lookup (Wiktionary extract). Prompt stays short."""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import Any

from .hawaiian_norm import fold_hawaiian, tokens

SKILLS_DIR = Path(__file__).resolve().parent
INDEX_CANDIDATES = (
    Path.home() / ".ollama" / "skills" / "state" / "store" / "hawaiian-dictionary" / "index.json",
    SKILLS_DIR / "hawaiian-glossary" / "index.json",
)

# Common English that would false-hit short Hawaiian homographs.
ENGLISH_STOP = frozenset(
    {
        "about",
        "after",
        "again",
        "being",
        "could",
        "does",
        "doing",
        "every",
        "first",
        "group",
        "hello",
        "later",
        "maybe",
        "never",
        "other",
        "please",
        "really",
        "should",
        "thanks",
        "their",
        "there",
        "these",
        "thing",
        "think",
        "those",
        "three",
        "today",
        "under",
        "until",
        "where",
        "which",
        "would",
        "write",
    }
)

# Place / English clock words — not a request to speak Hawaiian.
PLACE_ENGLISH = frozenset(
    {"hawaii", "hawaiian", "kilauea", "hilo", "maui", "oahu", "kauai", "honolulu"}
)
WEAK_HITS = frozenset({"make", "made", "papa", "mama", "luna", "hope", "name", "mele"})


@lru_cache(maxsize=1)
def _load_index() -> dict[str, list[dict[str, Any]]]:
    for path in INDEX_CANDIDATES:
        if not path.is_file():
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        entries = data.get("entries") if isinstance(data, dict) else None
        if isinstance(entries, dict):
            return entries
    return {}


def lookup_folded(folded: str) -> list[dict[str, Any]]:
    if not folded:
        return []
    raw = _load_index().get(folded) or []
    return [e for e in raw if isinstance(e, dict) and e.get("word")]


def message_looks_hawaiian(text: str) -> bool:
    s = text or ""
    if any(ch in s for ch in ("ā", "ē", "ī", "ō", "ū", "Ā", "Ē", "Ī", "Ō", "Ū", "ʻ")):
        return True
    folded = fold_hawaiian(s)
    for phrase in (
        "hawaiian standard time",
        "hawaii standard time",
        "hawaii pacific solar",
        "hawaii pacific",
    ):
        folded = folded.replace(phrase, " ")
    if "olelo" in folded or "hawaiian language" in folded or "speak hawaiian" in folded:
        return True
    hints = (
        "aloha",
        "mahalo",
        "aina",
        "ohana",
        "mauka",
        "makai",
        "kamaaina",
        "malihini",
        "wikiwiki",
        "hooponopono",
    )
    return any(h in folded for h in hints)


def lookup_in_text(text: str, *, limit: int = 8) -> list[dict[str, Any]]:
    """Return distinct dictionary hits for tokens in text. Not used on every chat."""
    haw_ctx = message_looks_hawaiian(text)
    seen: set[tuple[str, str]] = set()
    hits: list[dict[str, Any]] = []
    for raw in tokens(text):
        folded = fold_hawaiian(raw)
        if len(folded) < 4:
            continue
        if folded in ENGLISH_STOP:
            continue
        if folded in PLACE_ENGLISH:
            continue
        if folded in WEAK_HITS and not haw_ctx:
            continue
        if len(folded) < 5 and not haw_ctx and "ʻ" not in raw and not any(
            c in raw for c in "āēīōūĀĒĪŌŪ"
        ):
            continue
        for entry in lookup_folded(folded):
            key = (str(entry.get("word")), str(entry.get("pos")))
            if key in seen:
                continue
            seen.add(key)
            hits.append(entry)
            if len(hits) >= limit:
                return hits
    return hits


def format_hits(hits: list[dict[str, Any]], *, cap: int = 700) -> str:
    lines: list[str] = []
    used = 0
    for entry in hits:
        word = str(entry.get("word") or "").strip()
        pos = str(entry.get("pos") or "").strip()
        glosses = [str(g) for g in (entry.get("glosses") or []) if str(g).strip()]
        if not word or not glosses:
            continue
        gloss = "; ".join(glosses[:2])
        line = f"- {word}" + (f" ({pos})" if pos else "") + f": {gloss}"
        if used + len(line) + 1 > cap:
            break
        lines.append(line)
        used += len(line) + 1
    return "\n".join(lines)
