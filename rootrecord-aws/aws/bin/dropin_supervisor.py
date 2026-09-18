#!/usr/bin/env python3
"""Drop-in poller supervisor for chronological/*.py files.

Layout (create once; drop files anytime — absorbed on post-pack restart):
  chronological/always-on/*.py      — long-running loops (must have main/run)
  chronological/since-last-fire/*.py — invoked, then sleep INTERVAL_S (default 300)
  chronological/on-time/*.py        — invoked near :00/:15/:30/:45 (and pack slots)
  chronological/assets/**           — non-.py files auto-copied into work/assets for packs

Do not put secrets in these folders. Current.* writers should use work/<name>/Current*.
"""
from __future__ import annotations

import importlib.util
import logging
import os
import subprocess
import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import ROOT, WORK, ensure_dirs, now_hst, write_json

log = logging.getLogger("rr.dropins")
CHRONO = ROOT / "chronological"
INTERVAL_DEFAULT = int(os.environ.get("RR_DROPIN_INTERVAL_S", "300"))


def _load_module(path: Path):
    spec = importlib.util.spec_from_file_location(f"rr_dropin_{path.stem}", path)
    if spec is None or spec.loader is None:
        raise ImportError(str(path))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _run_once(path: Path) -> None:
    mod = _load_module(path)
    if hasattr(mod, "run"):
        out = mod.run()
        if hasattr(out, "__await__"):
            import asyncio

            asyncio.run(out)  # type: ignore[arg-type]
    elif hasattr(mod, "main"):
        mod.main()
    elif hasattr(mod, "poll_once"):
        mod.poll_once()
    else:
        raise RuntimeError(f"{path.name} needs run(), main(), or poll_once()")


def _always_on_worker(path: Path) -> None:
    while True:
        try:
            log.info("always-on start %s", path.name)
            _run_once(path)
            log.warning("always-on %s exited — restart in 5s", path.name)
        except Exception as exc:
            log.warning("always-on %s failed: %s", path.name, exc)
        time.sleep(5)


def _interval_worker(path: Path) -> None:
    while True:
        started = time.monotonic()
        try:
            _run_once(path)
            log.info("since-last-fire ok %s", path.name)
        except Exception as exc:
            log.warning("since-last-fire %s failed: %s", path.name, exc)
        # honor module INTERVAL_S if present
        try:
            mod = _load_module(path)
            interval = float(getattr(mod, "INTERVAL_S", INTERVAL_DEFAULT))
        except Exception:
            interval = float(INTERVAL_DEFAULT)
        delay = max(1.0, interval - (time.monotonic() - started))
        time.sleep(delay)


def _on_time_worker(path: Path) -> None:
    marks = {0, 10, 15, 25, 30, 40, 45, 55}
    last_fire = None
    while True:
        now = now_hst()
        key = (now.hour, now.minute)
        if now.minute in marks and key != last_fire and now.second < 20:
            try:
                _run_once(path)
                last_fire = key
                log.info("on-time ok %s at %s", path.name, now.strftime("%H:%M"))
            except Exception as exc:
                log.warning("on-time %s failed: %s", path.name, exc)
        time.sleep(2)


def sync_assets() -> int:
    """Auto-pack everything under chronological/ that is not .py and not Current*.

    Python drop-ins run via supervisor. Current.* stay as live writer names in work/.
    Non-code assets (json, txt, png, cfg, …) land in work/assets/ for the next zip.
    """
    dest = WORK / "assets"
    dest.mkdir(parents=True, exist_ok=True)
    n = 0
    if not CHRONO.is_dir():
        return 0
    for path in CHRONO.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix.lower() == ".py":
            continue
        if path.name.upper().startswith("CURRENT"):
            continue
        if path.name in ("README.txt", "README.md", ".keep"):
            continue
        rel = path.relative_to(CHRONO)
        target = dest / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        data = path.read_bytes()
        if not target.is_file() or target.read_bytes() != data:
            target.write_bytes(data)
            n += 1
    return n


def ensure_layout() -> None:
    for name in ("always-on", "since-last-fire", "on-time", "assets"):
        d = CHRONO / name
        d.mkdir(parents=True, exist_ok=True)
        readme = d / "README.txt"
        if not readme.is_file():
            readme.write_text(
                f"Drop files here ({name}). .py pollers auto-run after each pack restart.\n"
                "Non-.py under assets/ are packed automatically (not wiped from chronological/).\n",
                encoding="utf-8",
            )


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    ensure_dirs()
    ensure_layout()
    sync_assets()
    threads: list[threading.Thread] = []
    mapping = [
        (CHRONO / "always-on", _always_on_worker),
        (CHRONO / "since-last-fire", _interval_worker),
        (CHRONO / "on-time", _on_time_worker),
    ]
    for folder, worker in mapping:
        for path in sorted(folder.glob("*.py")):
            if path.name.startswith("_"):
                continue
            t = threading.Thread(target=worker, args=(path,), name=f"dropin-{path.stem}", daemon=True)
            t.start()
            threads.append(t)
            log.info("scheduled %s", path)
    write_json(
        WORK / "sysmon" / "dropins.json",
        {"ok": True, "threads": len(threads), "updated_at": now_hst().isoformat()},
    )
    log.info("dropin supervisor running threads=%s", len(threads))
    while True:
        sync_assets()
        time.sleep(60)


if __name__ == "__main__":
    main()
