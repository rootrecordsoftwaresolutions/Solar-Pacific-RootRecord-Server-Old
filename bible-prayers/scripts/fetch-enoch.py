#!/usr/bin/env python3
"""Fetch R.H. Charles 1 Enoch (public domain, from Ethiopic) into store/versions."""
from __future__ import annotations

import csv
import html
import re
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
STORE = HERE.parent / "store" / "versions"
URL = "https://www.ccel.org/c/charles/otpseudepig/enoch.htm"
SRC = STORE / "enoch-charles.html"
OUT = STORE / "Enoch.csv"
UA = "RootRecord-bible-prayers/1.0 (local skill; public-domain fetch)"

MARKER = re.compile(
    r"(?:(?<=\s)|(?<=^))(\d+)(?:,(\d+))?([a-z])?(?=\s)",
    re.I | re.M,
)
CHAPTER = re.compile(r"\[Chapter\s+(\d+)\]")


def download() -> str:
    STORE.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(URL, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as resp:
        raw = resp.read()
    text = raw.decode("utf-8", errors="replace")
    SRC.write_text(text, encoding="utf-8")
    return text


def to_text(raw: str) -> str:
    text = re.sub(r"(?i)<br\s*/?>", "\n", raw)
    text = re.sub(r"(?i)</p>", "\n", text)
    text = re.sub(r"(?i)<p[^>]*>", "\n", text)
    text = re.sub(r"<[^>]+>", " ", text)
    text = html.unescape(text)
    for ch in ("⌈", "⌉", "†"):
        text = text.replace(ch, "")
    text = re.sub(r"[ \t]+", " ", text)
    return text


def parse_chapter(body: str) -> list[tuple[int, str]]:
    body = body.strip()
    body = re.split(r"\n\s*Section\s+[IVXLC]+\b", body, maxsplit=1)[0]
    body = re.split(r"\n\s*INTRODUCTION\b", body, maxsplit=1)[0].strip()
    if not body:
        return []
    padded = " " + body + " "
    matches = list(MARKER.finditer(padded))
    kept: list[re.Match[str]] = []
    expected = 1
    ranges: list[tuple[int, int]] = []
    for m in matches:
        a = int(m.group(1))
        b = int(m.group(2)) if m.group(2) else a
        if a != expected:
            continue
        kept.append(m)
        ranges.append((a, b))
        expected = b + 1
    if not kept:
        clean = re.sub(r"\s+", " ", body).strip()
        return [(1, clean)] if clean else []
    verses: dict[int, str] = {}
    for i, m in enumerate(kept):
        a, b = ranges[i]
        end = kept[i + 1].start() if i + 1 < len(kept) else len(padded)
        chunk = re.sub(r"\s+", " ", padded[m.end() : end]).strip()
        if not chunk:
            continue
        for n in range(a, b + 1):
            verses[n] = chunk
    return [(n, verses[n]) for n in sorted(verses)]


def parse(raw: str) -> list[tuple[int, int, str]]:
    text = to_text(raw)
    parts = CHAPTER.split(text)
    rows: list[tuple[int, int, str]] = []
    i = 1
    while i < len(parts) - 1:
        ch = int(parts[i])
        body = parts[i + 1]
        for vs, content in parse_chapter(body):
            if content:
                rows.append((ch, vs, content))
        i += 2
    return rows


def write_csv(rows: list[tuple[int, int, str]]) -> None:
    with OUT.open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["Book", "Chapter", "Verse", "Text"])
        for ch, vs, text in rows:
            w.writerow(["Enoch", ch, vs, text])


def main() -> int:
    raw = download()
    rows = parse(raw)
    write_csv(rows)
    chs = {r[0] for r in rows}
    print(f"ok {OUT} chapters={len(chs)} verses={len(rows)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
