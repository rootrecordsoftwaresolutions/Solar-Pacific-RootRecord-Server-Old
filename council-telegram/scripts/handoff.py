"""Copy allowlisted desk files into a zip for non-API / manual use.

Never includes secrets. Originals stay put. Bruce sends the zip.
"""
from __future__ import annotations

import json
import zipfile
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

from .config import RUNS_DIR
from .desk_read import _denied, resolve

HST = ZoneInfo("Pacific/Honolulu")
REPO = Path(__file__).resolve().parents[2]
HANDOFF_DIR = Path.home() / ".ollama" / "skills" / "state" / "store" / "handoff"
MAX_FILES = 80
MAX_FILE_BYTES = 1_500_000
MARKER = "HANDOFF_ZIP="


def _safe_under(root: Path, path: Path) -> bool:
    try:
        path.resolve().relative_to(root.resolve())
        return True
    except ValueError:
        return False


def zip_allowed(path: Path) -> bool:
    if not path.is_file():
        return False
    return _safe_under(HANDOFF_DIR, path) and path.suffix.lower() == ".zip"


def parse_zip_line(text: str) -> Path | None:
    for line in (text or "").splitlines():
        line = line.strip()
        if line.startswith(MARKER):
            raw = line[len(MARKER) :].strip()
            path = Path(raw)
            if zip_allowed(path):
                return path
    return None


def _copy_into(staging: Path, src: Path, arcname: str) -> dict[str, Any] | None:
    if not src.is_file() or _denied(src):
        return None
    size = src.stat().st_size
    if size > MAX_FILE_BYTES:
        return None
    dest = staging / arcname
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(src.read_bytes())
    return {"arc": arcname, "bytes": size, "from": src.name}


def default_sources() -> list[tuple[Path, str]]:
    rows: list[tuple[Path, str]] = []
    from apps.core.services.ecoflow_public import live_path, write_ops_guide

    live = live_path()
    if live.is_file():
        rows.append((live, "ecoflow-live.json"))
    try:
        guide = write_ops_guide()
        if guide.is_file():
            rows.append((guide, guide.name))
    except OSError:
        pass
    from . import boot_brief

    reports = boot_brief.select_clean_reports()
    for path in reports:
        rows.append((path, f"status/{path.name}"))
    plans = RUNS_DIR / "proposals"
    if plans.is_dir():
        for path in sorted(plans.glob("plan-*.md"), key=lambda p: p.name):
            rows.append((path, f"proposals/{path.name}"))
    return rows


def extra_from_specs(specs: list[str]) -> list[tuple[Path, str]]:
    out: list[tuple[Path, str]] = []
    for spec in specs:
        path = resolve(spec)
        if path and path.is_file():
            out.append((path, Path(spec).name))
    return out


def bundle(*, extra_specs: list[str] | None = None) -> dict[str, Any]:
    HANDOFF_DIR.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(HST).strftime("%Y%m%d-%H%M%S")
    staging = HANDOFF_DIR / f"staging-{stamp}"
    staging.mkdir(parents=True, exist_ok=True)
    manifest: list[dict[str, Any]] = []
    seen: set[str] = set()
    for src, arc in [*default_sources(), *extra_from_specs(extra_specs or [])]:
        if len(manifest) >= MAX_FILES:
            break
        if arc in seen:
            continue
        seen.add(arc)
        row = _copy_into(staging, src, arc)
        if row:
            manifest.append(row)
    (staging / "MANIFEST.json").write_text(
        json.dumps({"at": datetime.now(HST).isoformat(timespec="seconds"), "files": manifest}, indent=2)
        + "\n",
        encoding="utf-8",
    )
    zpath = HANDOFF_DIR / f"council-handoff-{stamp}.zip"
    with zipfile.ZipFile(zpath, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for p in staging.rglob("*"):
            if p.is_file():
                zf.write(p, p.relative_to(staging).as_posix())
    return {
        "ok": True,
        "zip": str(zpath),
        "count": len(manifest),
        "files": [m["arc"] for m in manifest],
    }


def send_to_chat(cfg: Any, chat_id: int | str, *, caption: str | None = None) -> dict[str, Any]:
    """Build the zip and attach it as Bruce. Works whether Ollama is up or down."""
    from . import sanitize, telegram

    out = bundle()
    path = Path(out.get("zip") or "")
    if not out.get("ok") or not zip_allowed(path):
        return {"ok": False, "detail": "zip_failed", **out}
    cap = caption or (
        "Thought-session copies — all proposal markdown plus ecoflow-live. "
        "No secrets. For manual / non-API use."
    )
    res = telegram.send_document(
        cfg.token_for("bruce"),
        chat_id,
        path,
        caption=sanitize.sanitize_outbound(cap, voice="bruce")[:900],
    )
    return {
        "ok": bool(res.get("ok")),
        "zip": str(path),
        "count": out.get("count"),
        "telegram": res.get("ok"),
    }
