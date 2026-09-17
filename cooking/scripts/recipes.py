#!/usr/bin/env python3
"""Recipes. Shared dishes save as source=user. Pantry + nutrition are other desks."""
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
        "COOKING_STORE",
        str(Path.home() / ".ollama" / "skills" / "cooking" / "store" / "recipes.json"),
    )
)
RECIPE_TAG = re.compile(r"<<<RECIPE\s+([^>]*?)>>>", re.I)
TITLE_CAP = 120
NOTE_CAP = 800
MAX_RECIPES = 400


def _now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime()) + "-10:00"


def slug(name: str) -> str:
    raw = re.sub(r"[^a-z0-9]+", "-", (name or "").lower()).strip("-")
    return (raw or "recipe")[:64]


def _load() -> dict[str, Any]:
    if not STORE.is_file():
        return {"updated": _now(), "recipes": []}
    try:
        data = json.loads(STORE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"updated": _now(), "recipes": []}
    return data if isinstance(data, dict) else {"updated": _now(), "recipes": []}


def _save(data: dict[str, Any]) -> None:
    STORE.parent.mkdir(parents=True, exist_ok=True)
    data = dict(data)
    data["updated"] = _now()
    tmp = STORE.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    tmp.replace(STORE)


def _split_ings(raw: Any) -> list[str]:
    if isinstance(raw, list):
        return [str(x).strip() for x in raw if str(x).strip()][:40]
    text = str(raw or "")
    parts = re.split(r"[,;\n]+", text)
    return [p.strip() for p in parts if p.strip()][:40]


def _ing_key(name: str) -> str:
    t = (name or "").lower()
    t = re.sub(r"\b(canned|fresh|frozen|dried|dry|chopped|diced|a|an|the)\b", " ", t)
    t = re.sub(r"[^a-z0-9]+", "-", t).strip("-")
    return t or "item"


def recipes() -> list[dict[str, Any]]:
    rows = _load().get("recipes") or []
    return [r for r in rows if isinstance(r, dict) and r.get("id")]


def get(recipe_id: str) -> dict[str, Any] | None:
    want = slug(recipe_id)
    for r in recipes():
        if r.get("id") == want or slug(str(r.get("title") or "")) == want:
            return r
    return None


def save_recipe(
    title: str,
    *,
    ingredients: Any = "",
    steps: list[str] | str = "",
    notes: str = "",
    source: str = "user",
    voice: str = "cli",
    cheap: bool | None = None,
) -> dict[str, Any]:
    title = (title or "").strip()[:TITLE_CAP]
    if not title:
        return {"ok": False, "error": "empty_title"}
    ings = _split_ings(ingredients)
    if isinstance(steps, str):
        step_list = [s.strip() for s in re.split(r"[\n;]+", steps) if s.strip()][:20]
    else:
        step_list = [str(s).strip() for s in (steps or []) if str(s).strip()][:20]
    src = "user" if source != "seed" else "seed"
    data = _load()
    rows = [r for r in (data.get("recipes") or []) if isinstance(r, dict)]
    rid = slug(title)
    for r in rows:
        if r.get("id") == rid:
            if r.get("source") == "user" and src == "seed":
                return {"ok": True, "id": rid, "action": "kept-user"}
            r["ingredients"] = ings or r.get("ingredients") or []
            if step_list:
                r["steps"] = step_list
            if notes:
                r["notes"] = notes[:NOTE_CAP]
            r["source"] = "user" if r.get("source") == "user" or src == "user" else r.get("source")
            r["updated"] = _now()
            r["updated_by"] = voice
            data["recipes"] = rows
            _save(data)
            return {"ok": True, "id": rid, "action": "amended", "source": r.get("source")}
    if len(rows) >= MAX_RECIPES:
        return {"ok": False, "error": "cap"}
    row = {
        "id": rid,
        "title": title,
        "source": src,
        "cheap": True if cheap is None and src == "seed" else bool(cheap),
        "ingredients": ings,
        "steps": step_list,
        "notes": notes[:NOTE_CAP],
        "created": _now(),
        "updated_by": voice,
        "submitted_by": voice if src == "user" else "seed",
    }
    rows.append(row)
    data["recipes"] = rows
    _save(data)
    return {"ok": True, "id": rid, "action": "added", "source": src}


def apply_recipe_tags(raw: str, *, voice: str) -> None:
    for body in RECIPE_TAG.findall(raw or ""):
        parts = [p.strip() for p in (body or "").split("|")]
        title = parts[0] if parts else ""
        ings = parts[1] if len(parts) > 1 else ""
        notes = parts[2] if len(parts) > 2 else "shared in chat"
        save_recipe(title, ingredients=ings, notes=notes, source="user", voice=voice)


def _pantry_mod():
    root = Path.home() / ".ollama" / "skills" / "pantry" / "scripts"
    if str(root) not in sys.path:
        sys.path.insert(0, str(root))
    import pantry as pantry_mod  # type: ignore

    return pantry_mod


def _nutrition_mod():
    root = Path.home() / ".ollama" / "skills" / "nutrition" / "scripts"
    if str(root) not in sys.path:
        sys.path.insert(0, str(root))
    import foods as foods_mod  # type: ignore

    return foods_mod


def _have_ingredient(name: str, have: set[str], pantry: Any | None) -> bool:
    keys = {_ing_key(name), slug(name)}
    if pantry is not None:
        try:
            keys.add(pantry.resolve_id(name))
            keys.add(pantry.resolve_id(_ing_key(name)))
        except Exception:
            pass
    return any(k in have for k in keys if k)


def from_pantry(*, cap: int = 3500) -> str:
    pantry = None
    try:
        pantry = _pantry_mod()
        have = pantry.have_ids()
    except Exception:
        have = set()
    lines = ["Cook from pantry (logged stock only):"]
    if not have:
        lines.append("- pantry empty on file; cheap seed recipes still listed below")
    scored: list[tuple[int, int, dict[str, Any], list[str], list[str]]] = []
    for r in recipes():
        ings = [str(x) for x in (r.get("ingredients") or [])]
        have_n = [x for x in ings if _have_ingredient(x, have, pantry)]
        miss = [x for x in ings if not _have_ingredient(x, have, pantry)]
        scored.append((len(miss), -len(have_n), r, have_n, miss))
    scored.sort(key=lambda t: (t[0], t[1], str(t[2].get("title") or "")))
    for miss_n, _, r, have_n, miss in scored[:16]:
        src = r.get("source")
        flag = "user" if src == "user" else "seed"
        if have and miss_n == 0 and r.get("ingredients"):
            state = "can cook"
        elif have:
            state = f"missing {miss_n}: {', '.join(miss[:5])}"
        else:
            state = "no stock logged"
        lines.append(f"- {r.get('id')} [{flag}] {r.get('title')} — {state}")
    blob = "\n".join(lines)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def nutrition_for(recipe: dict[str, Any]) -> list[str]:
    try:
        foods = _nutrition_mod()
    except Exception:
        return []
    hits: list[str] = []
    for ing in recipe.get("ingredients") or []:
        row = foods.match_ingredient(str(ing))
        if not row:
            continue
        dens = row.get("density")
        if dens in {"very-high", "high"}:
            hits.append(f"{row.get('name')} [{dens}]")
    return hits[:12]


def prompt_lines(*, cap: int = 3500, source: str | None = None) -> str:
    rows = recipes()
    if source:
        rows = [r for r in rows if r.get("source") == source]
    lines = ["Recipes (user-submitted stays user):"]
    if not rows:
        lines.append("- none on file")
    for r in rows:
        ings = ", ".join(str(x) for x in (r.get("ingredients") or [])[:8])
        lines.append(f"- {r.get('id')} [{r.get('source')}] {r.get('title')} — {ings}")
    blob = "\n".join(lines)
    if len(blob) > cap:
        return blob[: cap - 1] + "…"
    return blob


def show_text(recipe_id: str) -> str:
    r = get(recipe_id)
    if not r:
        return "missing"
    lines = [
        f"{r.get('title')} [{r.get('source')}]",
        "Ingredients: " + ", ".join(str(x) for x in (r.get("ingredients") or [])),
    ]
    steps = r.get("steps") or []
    if steps:
        lines.append("Steps:")
        for i, s in enumerate(steps, 1):
            lines.append(f"  {i}. {s}")
    if r.get("notes"):
        lines.append(f"Notes: {r.get('notes')}")
    dense = nutrition_for(r)
    if dense:
        lines.append("Dense matches (nutrition desk): " + "; ".join(dense))
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    args = list(argv if argv is not None else sys.argv[1:])
    if not args or args[0] in {"list", "show-all"} or args[0].startswith("--"):
        src = None
        if "--user" in args:
            src = "user"
        elif "--seed" in args:
            src = "seed"
        print(prompt_lines(source=src))
        return 0
    if args[0] == "from-pantry":
        print(from_pantry())
        return 0
    if args[0] == "show" and len(args) > 1:
        print(show_text(" ".join(args[1:])))
        return 0
    if args[0] == "save":
        title = ""
        ings = ""
        notes = ""
        rest: list[str] = []
        i = 1
        while i < len(args):
            if args[i] == "--title" and i + 1 < len(args):
                title = args[i + 1]
                i += 2
                continue
            if args[i] == "--ingredients" and i + 1 < len(args):
                ings = args[i + 1]
                i += 2
                continue
            if args[i] == "--notes" and i + 1 < len(args):
                notes = args[i + 1]
                i += 2
                continue
            rest.append(args[i])
            i += 1
        if not title and rest:
            blob = " ".join(rest)
            title, _, ings = blob.partition("|")
            ings, _, notes = ings.partition("|")
        print(
            json.dumps(
                save_recipe(
                    title.strip(),
                    ingredients=ings.strip(),
                    notes=notes.strip(),
                    source="user",
                    voice="cli",
                )
            )
        )
        return 0
    if args[0] in {"usda", "fndds"}:
        q = " ".join(args[1:]).lower()
        path = Path(
            os.environ.get("AVA_MEDIA_DIR") or "/home/rootrecord/Media"
        ) / "public" / "documents" / "nutrition-datasets" / "indexes" / "fndds-dishes.jsonl"
        if not path.is_file():
            print("FNDDS dish index missing. Fetch USDA dump and run index_usda.py")
            return 1
        hits = []
        with path.open(encoding="utf-8") as fh:
            for line in fh:
                if q and q not in line.lower():
                    continue
                try:
                    rec = json.loads(line)
                except json.JSONDecodeError:
                    continue
                hits.append(rec)
                if len(hits) >= 12:
                    break
        if not hits:
            print("no USDA dish hits")
            return 0
        for rec in hits:
            ings = ", ".join(str(x) for x in (rec.get("ingredients") or [])[:8])
            print(f"- {rec.get('id')} [{rec.get('source')}] {rec.get('title')} — {ings}")
        return 0
    print(json.dumps({"ok": False, "error": "usage"}))
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
