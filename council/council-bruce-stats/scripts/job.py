"""Bruce measured desk sample — a few times a day, not a spam loop."""
from __future__ import annotations

import logging

log = logging.getLogger("ava.cron.council_bruce_stats")


async def run() -> dict:
    from apps.council.bruce_stats import tick

    out = tick()
    log.info("bruce stats telegram ok=%s", out.get("ok"))
    return out
