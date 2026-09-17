"""Evening report play — 17:28 HST. Queues current evening WAV (no TTS spend)."""

from __future__ import annotations

import logging
from datetime import datetime, timezone

log = logging.getLogger("ava.cron.evening_report_play")


async def run():
    log.info("Evening report play skipped — removed  %s", datetime.now(timezone.utc).isoformat())
    return {"ok": True, "skipped": True, "detail": "evening_removed"}
