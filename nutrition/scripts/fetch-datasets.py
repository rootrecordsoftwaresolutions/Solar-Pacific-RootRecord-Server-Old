#!/usr/bin/env python3
"""Fetch USDA / CORGIS / Open Food Facts dumps onto Media. No Kaggle without ~/.kaggle."""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

UA = "RootRecord-nutrition-desk/1.0 (local OmniBook ingest; USDA CC0 + OFF ODbL)"
MEDIA = Path(os.environ.get("AVA_MEDIA_DIR") or "/home/rootrecord/Media")
ROOT = MEDIA / "public" / "documents" / "nutrition-datasets"
SKILL_LINK = Path.home() / ".ollama" / "skills" / "nutrition" / "store" / "datasets"

FILES = [
    {
        "id": "usda-full-csv",
        "url": "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_csv_2026-04-30.zip",
        "path": ROOT / "usda" / "FoodData_Central_csv_2026-04-30.zip",
        "extract_dir": ROOT / "usda" / "csv",
        "kind": "zip",
        "expect_unzip_gb": 3.1,
    },
    {
        "id": "corgis-food",
        "url": "https://corgis-edu.github.io/corgis/datasets/csv/food/food.csv",
        "path": ROOT / "corgis" / "food.csv",
        "kind": "file",
    },
    {
        "id": "off-products-csv",
        "url": "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz",
        "path": ROOT / "off" / "en.openfoodfacts.org.products.csv.gz",
        "extract_path": ROOT / "off" / "en.openfoodfacts.org.products.csv",
        "kind": "gz",
        "expect_unzip_gb": 9.0,
    },
]

SKIPPED = [
    {
        "id": "kaggle-food-nutrition",
        "url": "https://www.kaggle.com/datasets/utsavdey1410/food-nutrition-dataset",
        "reason": "Kaggle CLI token missing (~/.kaggle/kaggle.json). USDA FDC covers the same job.",
    },
    {
        "id": "kaggle-openfoodfacts",
        "url": "https://www.kaggle.com/datasets/openfoodfacts/world-food-facts",
        "reason": "Kaggle token missing. Official OFF CSV dump is used instead.",
    },
    {
        "id": "off-jsonl",
        "url": "https://static.openfoodfacts.org/data/openfoodfacts-products.jsonl.gz",
        "reason": "Compressed dump is ~12.8 GB by itself. CSV dump is the 10 GB path. Fetch only if asked.",
    },
]


def _curl(url: str, dest: Path) -> int:
    dest.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "curl",
        "-fL",
        "--retry",
        "5",
        "--retry-delay",
        "4",
        "-C",
        "-",
        "-A",
        UA,
        "-o",
        str(dest),
        url,
    ]
    print("fetch", url, "->", dest, flush=True)
    return subprocess.call(cmd)


def _bytes(path: Path) -> int:
    try:
        return path.stat().st_size
    except OSError:
        return 0


def tree_bytes(path: Path) -> int:
    if not path.exists():
        return 0
    if path.is_file():
        return path.stat().st_size
    total = 0
    for p in path.rglob("*"):
        if p.is_file():
            total += p.stat().st_size
    return total


def ensure_link() -> None:
    SKILL_LINK.parent.mkdir(parents=True, exist_ok=True)
    if SKILL_LINK.is_symlink() or SKILL_LINK.exists():
        return
    SKILL_LINK.symlink_to(ROOT)


def status() -> dict:
    files = []
    total = tree_bytes(ROOT)
    for spec in FILES:
        p = spec["path"]
        rec = {
            "id": spec["id"],
            "bytes": _bytes(p),
            "path": str(p),
            "present": p.is_file(),
        }
        if spec.get("extract_dir"):
            rec["extracted_bytes"] = tree_bytes(spec["extract_dir"])
        if spec.get("extract_path"):
            rec["extracted_bytes"] = _bytes(spec["extract_path"])
        files.append(rec)
    return {
        "root": str(ROOT),
        "bytes": total,
        "gib": round(total / (1024**3), 3),
        "files": files,
        "skipped": SKIPPED,
        "kaggle_json": (Path.home() / ".kaggle" / "kaggle.json").is_file(),
    }


def extract_one(spec: dict) -> None:
    kind = spec.get("kind")
    src = spec["path"]
    if not src.is_file():
        return
    if kind == "zip":
        dest = spec["extract_dir"]
        dest.mkdir(parents=True, exist_ok=True)
        marker = dest / ".extracted"
        if marker.is_file():
            return
        print("unzip", src, "->", dest, flush=True)
        rc = subprocess.call(["unzip", "-n", str(src), "-d", str(dest)])
        if rc == 0:
            marker.write_text(time.strftime("%Y-%m-%dT%H:%M:%S") + "-10:00\n")
    elif kind == "gz":
        dest = spec["extract_path"]
        if dest.is_file() and dest.stat().st_size > 1_000_000:
            return
        print("gunzip -k", src, flush=True)
        # gzip -dk keeps the .gz; writes dest without .gz
        rc = subprocess.call(["gzip", "-dkf", str(src)])
        if rc != 0:
            print("gzip extract failed", rc, flush=True)


def fetch(ids: list[str] | None = None, *, extract: bool = True) -> dict:
    ensure_link()
    ROOT.mkdir(parents=True, exist_ok=True)
    want = set(ids or [s["id"] for s in FILES])
    for spec in FILES:
        if spec["id"] not in want:
            continue
        dest = spec["path"]
        if dest.is_file() and dest.stat().st_size > 0:
            print("have", dest, dest.stat().st_size, flush=True)
        else:
            rc = _curl(spec["url"], dest)
            if rc != 0:
                print("curl failed", spec["id"], rc, flush=True)
                continue
        if extract:
            extract_one(spec)
    st = status()
    (ROOT / "STATUS.json").write_text(json.dumps(st, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"ok": True, "gib": st["gib"], "bytes": st["bytes"]}))
    return st


def main(argv: list[str] | None = None) -> int:
    args = list(argv if argv is not None else sys.argv[1:])
    if args and args[0] == "--status":
        print(json.dumps(status(), indent=2))
        return 0
    if args and args[0] == "--no-extract":
        fetch(extract=False)
        return 0
    ids = [a for a in args if not a.startswith("-")] or None
    fetch(ids)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
