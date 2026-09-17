"""Boot prelims — refresh live facts BEFORE morning Boot Report / day-board.

Order: NOAA → NWS Hawaii by county → Kīlauea → Boot Report (file-only). Does not call Grok.
"""
from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

log = logging.getLogger("ava.cron.boot_prelims")
HST = ZoneInfo("Pacific/Honolulu")


def desk_report_kind(hour: int | None = None) -> str:
    """Morning file before noon HST; midday file after that (no leftover morning post)."""
    if hour is None:
        hour = datetime.now(HST).hour
    return "morning" if hour < 12 else "midday"


async def run(*, write_report: bool = True) -> dict:
    """Pull weather + volcano first, then write the slot Boot Report."""
    log.info("Boot prelims start  %s", datetime.now(timezone.utc).isoformat())
    out: dict = {"ok": True, "steps": {}, "grok": False}

    try:
        from apps.core.services import weather as noaa

        await noaa.run()
        out["steps"]["noaa"] = "ok"
    except Exception as e:
        log.exception("boot prelims NOAA failed")
        out["steps"]["noaa"] = f"fail:{type(e).__name__}"
        out["ok"] = False

    try:
        from apps.core.services import nws_hawaii as nws_hawaii_cron

        # Poll on boot; speak only when product hash is new (never force on recycle).
        nws_reason = "boot" if write_report else "midday_prelim"
        nws_out = await nws_hawaii_cron.run(
            reason=nws_reason,
            force_speak=False,
        )
        out["steps"]["nws_hawaii"] = {
            "ok": bool(nws_out.get("ok")),
            "alerts": nws_out.get("alerts"),
            "changed": nws_out.get("changed"),
            "source": nws_out.get("source"),
        }
    except Exception as e:
        log.exception("boot prelims NWS Hawaii counties failed")
        out["steps"]["nws_hawaii"] = f"fail:{type(e).__name__}"
        out["ok"] = False

    try:
        from apps.core.services import kilauea

        await kilauea.run()
        out["steps"]["kilauea"] = "ok"
    except Exception as e:
        log.exception("boot prelims Kīlauea failed")
        out["steps"]["kilauea"] = f"fail:{type(e).__name__}"
        out["ok"] = False

    # Clear live_wx cache so the Boot Report / chat see the new file + API.
    try:
        from apps.core.services import live_wx

        live_wx._cache = None
        await live_wx.weather_lines()
        out["steps"]["live_wx"] = "ok"
    except Exception as e:
        log.warning("boot prelims live_wx: %s", e)
        out["steps"]["live_wx"] = f"fail:{type(e).__name__}"

    if write_report:
        try:
            from apps.core.services import ollama as ollama_svc

            if ollama_svc.use_npu_chat():
                ready = await asyncio.to_thread(ollama_svc.wait_flm)
                out["steps"]["npu"] = "ready" if ready else "timeout"
                log.info("boot prelims NPU wait ready=%s", ready)
        except Exception as e:
            log.warning("boot prelims NPU wait: %s", e)
            out["steps"]["npu"] = "skip"
        try:
            from apps.core.services import boot_report, midday_report

            kind = desk_report_kind()
            # Local on-device path. Automation flags are advisory; no Grok.
            if kind == "midday":
                written = await asyncio.to_thread(
                    midday_report.write_midday_report, source="boot_prelims"
                )
            else:
                written = await asyncio.to_thread(
                    boot_report.write_boot_report, source="boot_prelims"
                )
            out["steps"]["boot_report"] = {
                "ok": written.get("ok"),
                "kind": kind,
                "dated": written.get("dated"),
                "current": written.get("current"),
                "bytes": written.get("bytes"),
                "engine": written.get("engine"),
                "scrub": written.get("scrub"),
                "automation": boot_report.morning_automation_enabled()
                if kind == "morning"
                else midday_report.midday_automation_enabled(),
                "grok": False,
                "tts": written.get("tts"),
            }
        except Exception as e:
            log.exception("boot prelims report write failed")
            out["steps"]["boot_report"] = f"fail:{type(e).__name__}"
            out["ok"] = False

    log.info("Boot prelims done ok=%s steps=%s", out["ok"], list(out["steps"]))
    return out
