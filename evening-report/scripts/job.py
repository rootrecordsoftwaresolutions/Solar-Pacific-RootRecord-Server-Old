"""Evening long-form report — removed. Do not generate or speak it."""

from __future__ import annotations

import logging
from datetime import datetime, timezone

log = logging.getLogger("ava.cron.evening_report")


async def run():
    log.info("Evening report cron skipped — removed  %s", datetime.now(timezone.utc).isoformat())
    from apps.core.services import daily_report_board

    daily_report_board.ensure_today()
    daily_report_board.mark_skipped_optional("evening", reason="evening_removed")
    return {"ok": True, "skipped": True, "detail": "evening_removed"}
