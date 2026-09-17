"""Folded into hurricane_fetch. Leftover job id is a no-op."""
from __future__ import annotations

import logging

log = logging.getLogger("ava.cron.nhc_media")


async def run() -> None:
    log.info("nhc_media skipped — folded into hurricane_fetch")
