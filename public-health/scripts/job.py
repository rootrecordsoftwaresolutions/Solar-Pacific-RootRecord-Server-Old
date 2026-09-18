"""Scheduler entry — status boards + Root Record Radio stay up."""
from __future__ import annotations

import logging

log = logging.getLogger("ava.cron.public_health")


async def run() -> dict:
    from pathlib import Path
    import importlib.util

    path = Path(__file__).resolve().parent / "public_health.py"
    spec = importlib.util.spec_from_file_location("public_health", path)
    if spec is None or spec.loader is None:
        raise ImportError(str(path))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    report = mod.check(alert=True, heal=True)
    log.info(
        "public-health ok=%s problems=%s alerted=%s heal=%s",
        report.get("ok"),
        report.get("problems"),
        report.get("alerted"),
        bool(report.get("heal")),
    )
    return report
