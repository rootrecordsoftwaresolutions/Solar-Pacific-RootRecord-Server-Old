#!/usr/bin/env python3
"""Search local American / Hawaiian history packs. Do not invent hits."""
from __future__ import annotations

import argparse
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
SKILL = HERE.parent
REFS = SKILL / "references"
PACKS = {
    "american": REFS / "american.md",
    "hawaiian": REFS / "hawaiian.md",
}


def fold(text: str) -> str:
    nfd = unicodedata.normalize("NFD", text.casefold())
    stripped = "".join(ch for ch in nfd if unicodedata.category(ch) != "Mn")
    return stripped.replace("ʻ", "").replace("'", "")


def blocks(path: Path) -> list[tuple[str, str]]:
    text = path.read_text(encoding="utf-8")
    out: list[tuple[str, str]] = []
    heading = path.stem
    buf: list[str] = []
    for line in text.splitlines():
        if line.startswith("## "):
            if buf:
                out.append((heading, "\n".join(buf).strip()))
            heading = line[3:].strip()
            buf = [line]
        else:
            buf.append(line)
    if buf:
        out.append((heading, "\n".join(buf).strip()))
    return out


def search(query: str, names: list[str]) -> list[str]:
    q = fold(query)
    hits: list[str] = []
    for name in names:
        path = PACKS[name]
        if not path.is_file():
            continue
        for heading, body in blocks(path):
            if q in fold(heading) or q in fold(body):
                hits.append(f"## {name} / {heading}\n\n{body}")
    return hits


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("query", nargs="+", help="words to find in the packs")
    parser.add_argument(
        "--pack",
        choices=("american", "hawaiian"),
        help="limit to one pack",
    )
    args = parser.parse_args()
    query = " ".join(args.query).strip()
    names = [args.pack] if args.pack else list(PACKS)
    hits = search(query, names)
    if not hits:
        print("no hit on this desk", file=sys.stderr)
        return 1
    print("\n\n---\n\n".join(hits))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
