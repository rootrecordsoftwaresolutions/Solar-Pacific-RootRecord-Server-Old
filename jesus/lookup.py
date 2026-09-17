#!/usr/bin/env python3
"""Allowlisted HTTPS fact lookup for Jesus. Wikipedia only. No cookies."""
from __future__ import annotations

import json
import re
import urllib.error
import urllib.parse
import urllib.request

UA = "RootRecord-sky-bot/1.0 (local; Wikipedia fact check)"
WIKI = "https://en.wikipedia.org"


def _get(url: str, timeout: float = 4) -> dict | list | str | None:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
    except (urllib.error.URLError, TimeoutError, OSError):
        return None
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw


def _search_title(query: str) -> str:
    qs = urllib.parse.urlencode(
        {"action": "opensearch", "search": query[:80], "limit": 1, "namespace": 0, "format": "json"}
    )
    got = _get(f"{WIKI}/w/api.php?{qs}")
    if not isinstance(got, list) or len(got) < 2:
        return ""
    titles = got[1] or []
    return str(titles[0]) if titles else ""


def _summary(title: str) -> str:
    path = urllib.parse.quote(title.replace(" ", "_"))
    got = _get(f"{WIKI}/api/rest_v1/page/summary/{path}")
    if not isinstance(got, dict):
        return ""
    extract = str(got.get("extract") or "").strip()
    return re.sub(r"\s+", " ", extract)[:320]


SELF_Q_RE = re.compile(
    r"\b(your role|who are you|what are you|are you (real|jesus|ai|a bot|a model)|"
    r"hang up|persona|experiment)\b",
    re.I,
)
WEAK_TERMS = {
    "the", "and", "you", "about", "that", "this", "with", "from", "just", "have",
    "what", "when", "does", "how", "why", "who", "where", "your", "they", "them",
    "something", "going", "know", "like", "very", "been", "role", "jesus", "christ",
    "lord", "please", "would", "could",
}


def lookup(query: str) -> str:
    if SELF_Q_RE.search(query or ""):
        return ""
    q = re.sub(r"\s+", " ", (query or "").strip())[:120]
    if len(q) < 8:
        return ""
    words = [w for w in re.findall(r"[A-Za-z]{4,}", q) if w.lower() not in WEAK_TERMS]
    if len(words) < 2:
        return ""
    candidates: list[str] = [" ".join(words[:4])]
    seen: set[str] = set()
    for cand in candidates[:2]:
        if cand in seen:
            continue
        seen.add(cand)
        title = _search_title(cand)
        if not title:
            continue
        title_l = title.lower()
        if not any(w.lower() in title_l for w in words[:4]):
            continue
        extract = _summary(title)
        if extract:
            return f"{title}: {extract}"
    return ""


def needs_lookup(text: str) -> bool:
    t = (text or "").strip()
    if len(t) < 16:
        return False
    if SELF_Q_RE.search(t):
        return False
    if re.search(r"\b(lmao|lol|rofl|haha)\b", t, re.I):
        return False
    if t.endswith("?"):
        return True
    return bool(re.match(r"(how|why|what|who|where)\b", t, re.I))
