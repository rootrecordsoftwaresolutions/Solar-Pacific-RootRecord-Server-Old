"""Scheduler entry — council Ava/Bruce/Carly functional check."""
from __future__ import annotations

import logging

log = logging.getLogger("ava.cron.council_health")


async def run() -> dict:
    from pathlib import Path
    import importlib.util

    path = Path(__file__).resolve().parent / "council_health.py"
    spec = importlib.util.spec_from_file_location("council_health", path)
    if spec is None or spec.loader is None:
        raise ImportError(str(path))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    report = mod.check(alert=True, probe_chat=True)
    log.info(
        "council-health ok=%s problems=%s alerted=%s",
        report.get("ok"),
        report.get("problems"),
        report.get("alerted"),
    )
    return report
