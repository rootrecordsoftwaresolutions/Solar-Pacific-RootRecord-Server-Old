#!/usr/bin/env python3
"""Build a local SQLite index from the USDA FDC CSV dump. Numbers come from USDA, not us."""
from __future__ import annotations

import csv
import json
import os
import sqlite3
import sys
from pathlib import Path

MEDIA = Path(os.environ.get("AVA_MEDIA_DIR") or "/home/rootrecord/Media")
ROOT = MEDIA / "public" / "documents" / "nutrition-datasets"
CSV_DIR = ROOT / "usda" / "csv"
DB = ROOT / "indexes" / "usda.sqlite"
RECIPES = ROOT / "indexes" / "fndds-dishes.jsonl"

# FDC nutrient ids we keep (per 100 g). See nutrient.csv in the dump.
KEEP_NUTRIENTS = {
    "1008": "energy_kcal",
    "1003": "protein_g",
    "1004": "fat_g",
    "1005": "carb_g",
    "1079": "fiber_g",
    "1087": "calcium_mg",
    "1089": "iron_mg",
    "1092": "potassium_mg",
    "1106": "vitamin_a_rae_mcg",
    "1162": "vitamin_c_mg",
    "1114": "vitamin_d_ug",
    "1175": "vitamin_b12_mcg",
    "1177": "folate_mcg",
    "1185": "vitamin_k_ug",
}


def _csv_path(name: str) -> Path:
    hits = list(CSV_DIR.rglob(name))
    if not hits:
        raise FileNotFoundError(f"missing {name} under {CSV_DIR}")
    return hits[0]


def build() -> dict:
    if not CSV_DIR.is_dir():
        return {"ok": False, "error": "usda_csv_missing", "path": str(CSV_DIR)}
    DB.parent.mkdir(parents=True, exist_ok=True)
    if DB.is_file():
        DB.unlink()
    con = sqlite3.connect(str(DB))
    con.execute("PRAGMA journal_mode=WAL")
    con.execute("PRAGMA synchronous=NORMAL")
    con.execute(
        """
        CREATE TABLE food (
            fdc_id INTEGER PRIMARY KEY,
            description TEXT NOT NULL,
            data_type TEXT,
            food_category_id TEXT
        )
        """
    )
    con.execute(
        """
        CREATE TABLE food_nutrient (
            fdc_id INTEGER NOT NULL,
            nutrient TEXT NOT NULL,
            amount REAL,
            PRIMARY KEY (fdc_id, nutrient)
        )
        """
    )
    con.execute("CREATE INDEX food_desc ON food(description)")
    con.execute("CREATE INDEX food_type ON food(data_type)")
    con.execute(
        "CREATE VIRTUAL TABLE food_fts USING fts5(description, content='food', content_rowid='fdc_id')"
    )

    food_file = _csv_path("food.csv")
    print("import", food_file, flush=True)
    n_food = 0
    with food_file.open(newline="", encoding="utf-8", errors="replace") as fh:
        reader = csv.DictReader(fh)
        batch = []
        for row in reader:
            try:
                fid = int(row.get("fdc_id") or 0)
            except ValueError:
                continue
            if not fid:
                continue
            desc = (row.get("description") or "").strip()
            if not desc:
                continue
            batch.append(
                (
                    fid,
                    desc,
                    (row.get("data_type") or "").strip(),
                    (row.get("food_category_id") or "").strip(),
                )
            )
            if len(batch) >= 5000:
                con.executemany(
                    "INSERT OR REPLACE INTO food(fdc_id, description, data_type, food_category_id) VALUES (?,?,?,?)",
                    batch,
                )
                n_food += len(batch)
                batch.clear()
        if batch:
            con.executemany(
                "INSERT OR REPLACE INTO food(fdc_id, description, data_type, food_category_id) VALUES (?,?,?,?)",
                batch,
            )
            n_food += len(batch)
    con.execute("INSERT INTO food_fts(food_fts) VALUES('rebuild')")
    con.commit()
    print("foods", n_food, flush=True)

    nut_file = _csv_path("food_nutrient.csv")
    print("import nutrients", nut_file, flush=True)
    n_nut = 0
    with nut_file.open(newline="", encoding="utf-8", errors="replace") as fh:
        reader = csv.DictReader(fh)
        batch = []
        for row in reader:
            nid = str(row.get("nutrient_id") or "").strip()
            key = KEEP_NUTRIENTS.get(nid)
            if not key:
                continue
            try:
                fid = int(row.get("fdc_id") or 0)
                amt = float(row.get("amount") or 0)
            except ValueError:
                continue
            batch.append((fid, key, amt))
            if len(batch) >= 8000:
                con.executemany(
                    "INSERT OR REPLACE INTO food_nutrient(fdc_id, nutrient, amount) VALUES (?,?,?)",
                    batch,
                )
                n_nut += len(batch)
                batch.clear()
        if batch:
            con.executemany(
                "INSERT OR REPLACE INTO food_nutrient(fdc_id, nutrient, amount) VALUES (?,?,?)",
                batch,
            )
            n_nut += len(batch)
    con.commit()

    dishes = 0
    input_path = None
    try:
        input_path = _csv_path("input_food.csv")
    except FileNotFoundError:
        input_path = None
    if input_path and input_path.is_file():
        print("index dishes", input_path, flush=True)
        by_parent: dict[int, list[str]] = {}
        with input_path.open(newline="", encoding="utf-8", errors="replace") as fh:
            reader = csv.DictReader(fh)
            for row in reader:
                try:
                    parent = int(row.get("fdc_id") or 0)
                except ValueError:
                    continue
                name = (row.get("sr_description") or row.get("ingredient_description") or "").strip()
                if parent and name:
                    bucket = by_parent.setdefault(parent, [])
                    if name not in bucket and len(bucket) < 24:
                        bucket.append(name)
        with RECIPES.open("w", encoding="utf-8") as out:
            for fid, ings in by_parent.items():
                row = con.execute(
                    "SELECT description, data_type FROM food WHERE fdc_id=?", (fid,)
                ).fetchone()
                if not row:
                    continue
                desc, dtype = row
                if dtype and "survey" not in dtype.lower() and "recipe" not in dtype.lower():
                    # still keep if it has multiple inputs
                    if len(ings) < 2:
                        continue
                rec = {
                    "id": f"usda-{fid}",
                    "title": desc,
                    "source": "usda-fndds",
                    "ingredients": ings,
                    "fdc_id": fid,
                    "data_type": dtype,
                }
                out.write(json.dumps(rec, ensure_ascii=False) + "\n")
                dishes += 1
    con.close()
    meta = {
        "ok": True,
        "db": str(DB),
        "foods": n_food,
        "nutrient_rows": n_nut,
        "dishes": dishes,
        "recipes": str(RECIPES) if dishes else None,
        "bytes": DB.stat().st_size if DB.is_file() else 0,
    }
    (ROOT / "indexes" / "usda-index.json").write_text(json.dumps(meta, indent=2) + "\n")
    print(json.dumps(meta))
    return meta


def search(query: str, *, limit: int = 12) -> list[dict]:
    if not DB.is_file():
        return []
    q = (query or "").strip()
    if not q:
        return []
    con = sqlite3.connect(str(DB))
    con.row_factory = sqlite3.Row
    try:
        token = " ".join(t for t in q.replace('"', " ").split() if t)
        fts_q = " ".join(f'"{t}"' for t in token.split()[:6])
        rows = con.execute(
            """
            SELECT f.fdc_id, f.description, f.data_type
            FROM food_fts fts
            JOIN food f ON f.fdc_id = fts.rowid
            WHERE food_fts MATCH ?
            LIMIT ?
            """,
            (fts_q, limit),
        ).fetchall()
    except sqlite3.OperationalError:
        rows = con.execute(
            "SELECT fdc_id, description, data_type FROM food WHERE description LIKE ? LIMIT ?",
            (f"%{q}%", limit),
        ).fetchall()
    out = []
    for row in rows:
        nuts = {
            r[0]: r[1]
            for r in con.execute(
                "SELECT nutrient, amount FROM food_nutrient WHERE fdc_id=?",
                (row["fdc_id"],),
            )
        }
        out.append(
            {
                "fdc_id": row["fdc_id"],
                "name": row["description"],
                "data_type": row["data_type"],
                "per_100g": nuts,
                "source": "usda-fdc",
            }
        )
    con.close()
    return out


def main(argv: list[str] | None = None) -> int:
    args = list(argv if argv is not None else sys.argv[1:])
    if args and args[0] == "search":
        print(json.dumps(search(" ".join(args[1:])), indent=2))
        return 0
    print(json.dumps(build(), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
