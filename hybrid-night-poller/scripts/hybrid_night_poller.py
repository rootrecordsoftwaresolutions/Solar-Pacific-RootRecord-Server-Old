#!/usr/bin/env python3
"""Hybrid daily inserts every 30 minutes. Local files only. No Ava origin."""
from __future__ import annotations

import logging
import sys
import time
from pathlib import Path

AVA = Path.home() / "RootRecord" / "Ava-Core"
sys.path.insert(0, str(AVA))

INTERVAL_S = 30 * 60
log = logging.getLogger("hybrid-night")


def _tick() -> None:
    from apps.core.services.hybrid_reports import (
        ensure_hybrid_daily_report,
        update_hybrid_charge_status,
        update_hybrid_daily_report,
    )
    from datetime import datetime
    from zoneinfo import ZoneInfo

    now = datetime.now(ZoneInfo("Pacific/Honolulu"))
    ensure_hybrid_daily_report(now)
    update_hybrid_daily_report(now)
    update_hybrid_charge_status(now)


def main() -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    while True:
        try:
            _tick()
        except Exception as e:
            log.warning("hybrid insert skipped: %s", e)
        time.sleep(INTERVAL_S)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
