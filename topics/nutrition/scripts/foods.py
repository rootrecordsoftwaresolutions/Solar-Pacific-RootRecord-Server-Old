#!/usr/bin/env python3
"""Nutrient-dense food database. Qualitative only. No invented milligrams."""
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
        "NUTRITION_STORE",
        str(Path.home() / ".ollama" / "skills" / "nutrition" / "store" / "foods.json"),
    )
)
NAME_CAP = 80
NOTE_CAP = 280
MAX_FOODS = 400


def _now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime()) + "-10:00"


def slug(name: str) -> str:
    raw = re.sub(r"[^a-z0-9]+", "-", (name or "").lower()).strip("-")
    return (raw or "food")[:48]


def _load() -> dict[str, Any]:
    if not STORE.is_file():
        return {"updated": _now(), "source": "desk seed", "foods": []}
    try:
        data = json.loads(STORE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"updated": _now(), "source": "desk seed", "foods": []}
    return data if isinstance(data, dict) else {"updated": _now(), "foods": []}


def _save(data: dict[str, Any]) -> None:
    STORE.parent.mkdir(parents=True, exist_ok=True)
    data = dict(data)
    data["updated"] = _now()
    tmp = STORE.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    tmp.replace(STORE)


def foods() -> list[dict[str, Any]]:
    rows = _load().get("foods") or []
    return [f for f in rows if isinstance(f, dict) and f.get("id")]


def get(food_id: str) -> dict[str, Any] | None:
    want = slug(food_id)
    for f in foods():
        if f.get("id") == want:
            return f
        aliases = f.get("aliases") or []
        if isinstance(aliases, list) and want in {slug(str(a)) for a in aliases}:
            return f
        if slug(str(f.get("name") or "")) == want:
            return f
    return None


def lookup(query: str = "", *, cheap_only: bool = False) -> list[dict[str, Any]]:
    q = (query or "").strip().lower()
    out: list[dict[str, Any]] = []
    for f in foods():
        if cheap_only and not f.get("cheap"):
            continue
        blob = " ".join(
            [
                str(f.get("id") or ""),
                str(f.get("name") or ""),
                str(f.get("category") or ""),
                " ".join(str(x) for x in (f.get("aliases") or [])),
                " ".join(str(x) for x in (f.get("standout") or [])),
                str(f.get("notes") or ""),
            ]
        ).lower()
        if q and q not in blob:
            continue
        out.append(f)
    rank = {"very-high": 0, "high": 1, "moderate": 2}
    out.sort(key=lambda r: (rank.get(str(r.get("density") or ""), 9), str(r.get("name") or "")))
    return out


def add_food(
    name: str,
    *,
    category: str = "",
    density: str = "high",
    standout: list[str] | None = None,
    cheap: bool = False,
    notes: str = "",
    aliases: list[str] | None = None,
    voice: str = "cli",
) -> dict[str, Any]:
    name = (name or "").strip()[:NAME_CAP]
    if not name:
        return {"ok": False, "error": "empty_name"}
    dens = (density or "high").strip().lower()
    if dens not in {"very-high", "high", "moderate"}:
        dens = "high"
    data = _load()
    rows = [f for f in (data.get("foods") or []) if isinstance(f, dict)]
    fid = slug(name)
    for f in rows:
        if f.get("id") == fid:
            f["updated"] = _now()
            f["updated_by"] = voice
            if category:
                f["category"] = category[:40]
            if notes:
                f["notes"] = notes[:NOTE_CAP]
            if standout:
                f["standout"] = [str(s)[:40] for s in standout[:12]]
            data["foods"] = rows
            _save(data)
            return {"ok": True, "id": fid, "action": "amended"}
    if len(rows) >= MAX_FOODS:
        return {"ok": False, "error": "cap"}
    rows.append(
        {
            "id": fid,
            "name": name,
            "category": (category or "food")[:40],
            "density": dens,
            "cheap": bool(cheap),
            "standout": [str(s)[:40] for s in (standout or [])[:12]],
            "aliases": [str(a)[:40] for a in (aliases or [])[:8]],
            "notes": notes[:NOTE_CAP],
            "source": "user" if voice != "seed" else "seed",
            "created": _now(),
            "updated_by": voice,
        }
    )
    data["foods"] = rows
    _save(data)
    return {"ok": True, "id": fid, "action": "added"}


def prompt_lines(*, query: str = "", cheap_only: bool = False, cap: int = 3500) -> str:
    rows = lookup(query, cheap_only=cheap_only)[:24]
    head = "Nutrient-dense foods (desk seed, not a lab):"
    if query:
        head = f"Nutrition lookup {query!r}:"
    lines = [head]
    if not rows:
        lines.append("- none on file")
    for f in rows:
        cheap = " cheap" if f.get("cheap") else ""
        stand = ", ".join(str(s) for s in (f.get("standout") or [])[:6])
        lines.append(
            f"- {f.get('id')}: {f.get('name')} [{f.get('density')}{cheap}] {stand}"
        )
    blob = "\n".join(lines)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def match_ingredient(name: str) -> dict[str, Any] | None:
    hit = get(name) or (lookup(name)[:1] or [None])[0]
    if hit:
        return hit
    usda = usda_search(name, limit=1)
    return usda[0] if usda else None


def usda_search(query: str, *, limit: int = 12) -> list[dict[str, Any]]:
    root = Path.home() / ".ollama" / "skills" / "nutrition" / "scripts"
    if str(root) not in sys.path:
        sys.path.insert(0, str(root))
    try:
        import index_usda
    except Exception:
        return []
    return index_usda.search(query, limit=limit)


def usda_lines(query: str, *, cap: int = 3500) -> str:
    rows = usda_search(query, limit=10)
    lines = [f"USDA FoodData Central (local dump) {query!r}:"]
    if not rows:
        lines.append("- index missing or no hit. Run fetch-datasets.sh then index_usda.py")
    for r in rows:
        n = r.get("per_100g") or {}
        bits = []
        if "protein_g" in n:
            bits.append(f"protein {n['protein_g']} g")
        if "iron_mg" in n:
            bits.append(f"iron {n['iron_mg']} mg")
        if "vitamin_b12_mcg" in n:
            bits.append(f"B12 {n['vitamin_b12_mcg']} mcg")
        extra = "; ".join(bits[:4])
        lines.append(f"- {r.get('fdc_id')}: {r.get('name')} [{r.get('data_type')}] {extra}".rstrip())
        lines.append("  per 100 g USDA, not a lab we ran")
    blob = "\n".join(lines)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def main(argv: list[str] | None = None) -> int:
    args = list(argv if argv is not None else sys.argv[1:])
    if not args or args[0] in {"list", "show-all"}:
        cheap = "--cheap" in args
        print(prompt_lines(cheap_only=cheap))
        return 0
    if args[0] == "lookup":
        cheap = "--cheap" in args
        q = " ".join(a for a in args[1:] if a != "--cheap")
        print(prompt_lines(query=q, cheap_only=cheap))
        return 0
    if args[0] == "show" and len(args) > 1:
        row = get(" ".join(args[1:]))
        if not row:
            print(json.dumps({"ok": False, "error": "missing"}))
            return 1
        print(json.dumps(row, indent=2))
        return 0
    if args[0] == "add" and len(args) > 1:
        print(json.dumps(add_food(" ".join(args[1:]), voice="cli")))
        return 0
    if args[0] == "usda":
        q = " ".join(args[1:])
        print(usda_lines(q))
        return 0
    print(json.dumps({"ok": False, "error": "usage"}))
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
