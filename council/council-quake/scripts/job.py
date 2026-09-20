"""Carly posts each new USGS quake to the council Telegram group, with her WAV."""
from __future__ import annotations

import logging

log = logging.getLogger("ava.cron.council_quake")


async def run() -> dict:
    from apps.council.quake_watch import tick_async

    out = await tick_async()
    if out.get("count"):
        log.info("council quake posted %s", out.get("posted"))
    else:
        log.debug("council quake %s", out)
    return out
