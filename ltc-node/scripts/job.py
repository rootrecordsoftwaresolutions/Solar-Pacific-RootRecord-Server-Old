"""Scheduler hook: refresh Litecoin pending withdraw snapshot."""
from __future__ import annotations

import logging
import sys
from pathlib import Path

log = logging.getLogger("ava.cron.ltc_pending")

_SCRIPTS = Path(__file__).resolve().parent
if str(_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS))

from pending_balance import poll  # noqa: E402


async def run() -> None:
    snap = poll()
    log.info(
        "ltc pending %s pct=%s motion=%s",
        "ok" if snap.get("ok") else snap.get("detail") or "fail",
        snap.get("percent_to_next_withdraw"),
        snap.get("council", {}).get("action") if isinstance(snap.get("council"), dict) else None,
    )
