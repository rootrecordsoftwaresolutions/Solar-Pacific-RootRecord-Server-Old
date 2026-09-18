"""
Ava Core Scheduler — replaces cronRunner.mjs.
Uses APScheduler with AsyncIO backend. Runs all cron jobs locally;
Cloudflare Workers check the heartbeat and stand down when Ava is awake.

Always-on design: no sleep mode, no day/night throttle. Ava relays data
whenever the device is powered on — right up until actual power-off.
Cloudflare Workers take over automatically once the heartbeat stops.
"""

from __future__ import annotations

import inspect
import logging
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from pathlib import Path

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger

from . import config
from .heartbeat import write_heartbeat

log = logging.getLogger("ava.scheduler")

_instance: "Scheduler | None" = None

_SKILL_ASYNC_CRONS = {
    "ecoflow_quota": (
        Path.home()
        / ".ollama"
        / "skills"
        / "ecoflow-quota"
        / "scripts"
        / "ecoflow_quota.py"
    ),
    "drive_automation": (
        Path.home()
        / ".ollama"
        / "skills"
        / "ecoflow-river-car"
        / "scripts"
        / "drive_automation.py"
    ),
    "panels_cam": (
        Path.home()
        / ".ollama"
        / "skills"
        / "panels-cam"
        / "scripts"
        / "panels_grab.py"
    ),
    "energy_report": (
        Path.home()
        / ".ollama"
        / "skills"
        / "energy-report"
        / "scripts"
        / "energy_report.py"
    ),
    "council_health": (
        Path.home()
        / ".ollama"
        / "skills"
        / "council-health"
        / "scripts"
        / "job.py"
    ),
    "public_health": (
        Path.home()
        / ".ollama"
        / "skills"
        / "public-health"
        / "scripts"
        / "job.py"
    ),
    "kilauea": (
        Path.home() / ".ollama" / "skills" / "rr-kilauea" / "scripts" / "kilauea.py"
    ),
    "noaa": (
        Path.home() / ".ollama" / "skills" / "rr-noaa" / "scripts" / "weather.py"
    ),
    "nws_hawaii": (
        Path.home()
        / ".ollama"
        / "skills"
        / "nws-hawaii"
        / "scripts"
        / "nws_hawaii.py"
    ),
    "earthquake_hourly": (
        Path.home()
        / ".ollama"
        / "skills"
        / "earthquake-hourly"
        / "scripts"
        / "earthquake_hourly.py"
    ),
    "radar_archive": (
        Path.home() / ".ollama" / "skills" / "radar-archive" / "scripts" / "job.py"
    ),
    "official_weather_media": (
        Path.home()
        / ".ollama"
        / "skills"
        / "official-weather-media"
        / "scripts"
        / "job.py"
    ),
    "hurricane_fetch": (
        Path.home() / ".ollama" / "skills" / "hurricane-fetch" / "scripts" / "job.py"
    ),
    "hurricane_desk": (
        Path.home() / ".ollama" / "skills" / "hurricane-desk" / "scripts" / "job.py"
    ),
    "hurricane_radio": (
        Path.home() / ".ollama" / "skills" / "hurricane-radio" / "scripts" / "job.py"
    ),
    "hurricane_obs": (
        Path.home() / ".ollama" / "skills" / "hurricane-obs" / "scripts" / "job.py"
    ),
    "hurricane_tracker": (
        Path.home() / ".ollama" / "skills" / "hurricane-tracker" / "scripts" / "job.py"
    ),
    "nhc_media": (
        Path.home() / ".ollama" / "skills" / "nhc-media" / "scripts" / "job.py"
    ),
    "kilauea_cams": (
        Path.home() / ".ollama" / "skills" / "kilauea-cams" / "scripts" / "job.py"
    ),
    "council_quake": (
        Path.home() / ".ollama" / "skills" / "council-quake" / "scripts" / "job.py"
    ),
    "morning_report": (
        Path.home() / ".ollama" / "skills" / "morning-report" / "scripts" / "job.py"
    ),
    "morning_report_play": (
        Path.home() / ".ollama" / "skills" / "morning-report-play" / "scripts" / "job.py"
    ),
    "merged_morning": (
        Path.home() / ".ollama" / "skills" / "merged-morning" / "scripts" / "job.py"
    ),
    "midday_report": (
        Path.home() / ".ollama" / "skills" / "midday-report" / "scripts" / "job.py"
    ),
    "midday_report_play": (
        Path.home() / ".ollama" / "skills" / "midday-report-play" / "scripts" / "job.py"
    ),
    "late_report": (
        Path.home() / ".ollama" / "skills" / "late-report" / "scripts" / "job.py"
    ),
    "late_report_play": (
        Path.home() / ".ollama" / "skills" / "late-report-play" / "scripts" / "job.py"
    ),
    "hourly_chime": (
        Path.home() / ".ollama" / "skills" / "hourly-chime" / "scripts" / "job.py"
    ),
    "remaining_tasks": (
        Path.home() / ".ollama" / "skills" / "remaining-tasks" / "scripts" / "job.py"
    ),
    "morning_boot_replay": (
        Path.home()
        / ".ollama"
        / "skills"
        / "morning-boot-replay"
        / "scripts"
        / "job.py"
    ),
    "hourly_clip_reports": (
        Path.home()
        / ".ollama"
        / "skills"
        / "hourly-clip-reports"
        / "scripts"
        / "job.py"
    ),
    "report_readiness": (
        Path.home() / ".ollama" / "skills" / "report-readiness" / "scripts" / "job.py"
    ),
    "report_periodic_audio": (
        Path.home()
        / ".ollama"
        / "skills"
        / "report-periodic-audio"
        / "scripts"
        / "job.py"
    ),
    "day_reports_morning": (
        Path.home()
        / ".ollama"
        / "skills"
        / "day-reports-morning"
        / "scripts"
        / "job.py"
    ),
    "day_reports_midday": (
        Path.home()
        / ".ollama"
        / "skills"
        / "day-reports-midday"
        / "scripts"
        / "job.py"
    ),
    "day_reports_evening": (
        Path.home()
        / ".ollama"
        / "skills"
        / "day-reports-evening"
        / "scripts"
        / "job.py"
    ),
    "overnight": (
        Path.home() / ".ollama" / "skills" / "overnight-relay" / "scripts" / "job.py"
    ),
    "daily_reports_catchup": (
        Path.home()
        / ".ollama"
        / "skills"
        / "daily-reports-catchup"
        / "scripts"
        / "job.py"
    ),
    "cursor_fallback": (
        Path.home() / ".ollama" / "skills" / "cursor-fallback" / "scripts" / "job.py"
    ),
    "inbox_drain": (
        Path.home() / ".ollama" / "skills" / "inbox-drain" / "scripts" / "job.py"
    ),
    "vercel_builds": (
        Path.home() / ".ollama" / "skills" / "vercel-builds" / "scripts" / "job.py"
    ),
    "stripe_poll": (
        Path.home() / ".ollama" / "skills" / "stripe-poll" / "scripts" / "job.py"
    ),
    "ltc_pending": (
        Path.home() / ".ollama" / "skills" / "ltc-node" / "scripts" / "job.py"
    ),
    "api_prices": (
        Path.home() / ".ollama" / "skills" / "api-prices" / "scripts" / "job.py"
    ),
    "economy_brief": (
        Path.home() / ".ollama" / "skills" / "economy-brief" / "scripts" / "job.py"
    ),
    "adsense_report": (
        Path.home() / ".ollama" / "skills" / "adsense-eod" / "scripts" / "job.py"
    ),
    "admob_report": (
        Path.home() / ".ollama" / "skills" / "admob-eod" / "scripts" / "job.py"
    ),
    "minecraft_live": (
        Path.home() / ".ollama" / "skills" / "minecraft-live" / "scripts" / "job.py"
    ),
    "player_economy": (
        Path.home() / ".ollama" / "skills" / "player-economy" / "scripts" / "job.py"
    ),
    "d1_sync": (
        Path.home() / ".ollama" / "skills" / "d1-sync" / "scripts" / "job.py"
    ),
    "user_qrcodes": (
        Path.home() / ".ollama" / "skills" / "user-qrcodes" / "scripts" / "job.py"
    ),
    "account_import": (
        Path.home() / ".ollama" / "skills" / "account-import" / "scripts" / "job.py"
    ),
    "council_bruce_stats": (
        Path.home()
        / ".ollama"
        / "skills"
        / "council-bruce-stats"
        / "scripts"
        / "job.py"
    ),
    "governance_daily": (
        Path.home() / ".ollama" / "skills" / "governance-daily" / "scripts" / "job.py"
    ),
    "governance_self_update": (
        Path.home()
        / ".ollama"
        / "skills"
        / "governance-self-update"
        / "scripts"
        / "job.py"
    ),
    "log_cleanup": (
        Path.home() / ".ollama" / "skills" / "log-cleanup" / "scripts" / "job.py"
    ),
    "system_perf": (
        Path.home() / ".ollama" / "skills" / "system-perf" / "scripts" / "job.py"
    ),
    "solar_weather": (
        Path.home()
        / ".ollama"
        / "skills"
        / "hourly-solar-weather"
        / "scripts"
        / "job.py"
    ),
    "code_review": (
        Path.home() / ".ollama" / "skills" / "code-review" / "scripts" / "job.py"
    ),
    "broadcast_loop": (
        Path.home() / ".ollama" / "skills" / "broadcast-loop" / "scripts" / "job.py"
    ),
}


NIGHT_POLL = {
    "fs-index",
    "fs_index",
    "council_quake",
    "council-quake",
    "official-weather-media",
    "official_weather_media",
    "nws-hawaii-counties",
    "nws_hawaii",
    "rr-kilauea",
    "kilauea",
    "rr-noaa",
    "noaa",
    "radar-archive",
    "radar_archive",
    "earthquake-hourly",
    "earthquake_hourly",
    "earthquake-m2-poll",
    "hurricane-fetch",
    "hurricane_fetch",
    "hurricane-desk",
    "hurricane_desk",
    "hourly-clip-prebuild",
}


def _skill_offloaded(cron_name: str) -> bool:
    """True when skill root has OFFLOADED (owned by rr-aws). Do not delete trees."""
    skill = _SKILL_ASYNC_CRONS.get(cron_name)
    if skill is None:
        return False
    try:
        return (Path(skill).resolve().parent.parent / "OFFLOADED").is_file()
    except Exception:
        return False


def night_sleeping() -> bool:
    try:
        from pathlib import Path
        import json

        p = Path.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store" / "state" / "night-mode.json"
        if not p.is_file():
            return False
        st = json.loads(p.read_text(encoding="utf-8"))
        return bool(st.get("sleeping"))
    except Exception:
        return False


def get_scheduler() -> "Scheduler | None":
    """The live Scheduler, or None before boot. Lets routes trigger jobs
    without importing main.py (which imports the routes)."""
    return _instance


def _job_wave(job_id: str) -> int:
    """Minimum AVA_CRON_WAVE required to register this job. Heartbeat is always 1."""
    wave1 = {
        "heartbeat",
        "rr-noaa",
        "rr-kilauea",
        "ecoflow-quota",
        "drive-automation",
        "panels-cam",
        "energy-report",
        "council-health",
        "public-health",
        "host-sample",
        "log-cleanup",
        "fs-index",
    }
    wave2 = {
        "hourly-solar-weather", "system-performance",
        "hurricane-fetch", "hurricane-desk", "hurricane-radio", "hurricane-obs",
    }
    wave3 = {
        "minecraft-live", "d1-sync", "player-economy-report", "user-qrcodes",
        "vercel-builds", "account-import", "stripe-poll", "ltc-pending", "inbox-drain",
    }
    wave4 = {
        "morning-report", "morning-report-play", "merged-morning-summary", "cursor-fallback",
        "economy-brief", "overnight-relay", "governance-daily", "governance-self-update",
        "api-prices", "day-reports-morning", "day-reports-midday", "day-reports-evening",
        "code-review", "midday-report", "midday-report-play", "evening-report",
        "evening-report-play", "evening-report-audio", "late-report", "late-report-play",
        "daily-reports-catchup",
    }
    wave5 = {"adsense-eod", "admob-eod"}
    wave6 = {
        "time-chime", "broadcast-loop", "kilauea-cams", "hourly-clip-prebuild",
        "hourly-clip-reports", "remaining-tasks",
    }
    if job_id in wave1 or job_id == "heartbeat":
        return 1
    if job_id in wave2:
        return 2
    if job_id in wave3:
        return 3
    if job_id in wave4:
        return 4
    if job_id in wave5:
        return 5
    if job_id in wave6:
        return 6
    return 1


class _WaveScheduler:
    """Wraps add_job so AVA_CRON_WAVE can omit later clones."""

    def __init__(self, inner):
        self._inner = inner

    def add_job(self, *args, **kwargs):
        job_id = kwargs.get("id") or ""
        need = _job_wave(job_id)
        if need > config.CRON_WAVE:
            log.info("Skipping cron %s (wave %s > AVA_CRON_WAVE=%s)", job_id, need, config.CRON_WAVE)
            return None
        func = args[0] if args else kwargs.get("func")
        rest = args[1:] if args else ()
        cron_name = getattr(func, "__name__", "") if func is not None else ""
        if cron_name and _skill_offloaded(cron_name):
            log.info(
                "Not registering OFFLOADED cron %s (job_id=%s, owned by rr-aws)",
                cron_name,
                job_id or "?",
            )
            return None

        async def guarded(*a, **k):
            if night_sleeping() and job_id not in NIGHT_POLL:
                log.info("night sleep skip %s", job_id)
                return None
            result = func(*a, **k)
            if inspect.isawaitable(result):
                return await result
            return result

        guarded.__name__ = getattr(func, "__name__", job_id or "job")
        return self._inner.add_job(guarded, *rest, **kwargs)

    def get_jobs(self):
        return self._inner.get_jobs()


class Scheduler:
    def __init__(self):
        global _instance
        self._apscheduler = AsyncIOScheduler(timezone="Pacific/Honolulu")
        self._register_jobs()
        _instance = self

    def _register_jobs(self):
        s = _WaveScheduler(self._apscheduler)

        # ── Heartbeat (every 60s) ─────────────────────────────────────────────
        # Tells Cloudflare Workers Ava is alive → they stay in standby.
        # When this stops, CF workers auto-kick their fallback crons.
        s.add_job(write_heartbeat, IntervalTrigger(seconds=60),
                  id="heartbeat", name="CF heartbeat writer", misfire_grace_time=30)

        # ── NOAA / NWS weather (hourly) ───────────────────────────────────────
        s.add_job(self._run("noaa"), IntervalTrigger(minutes=60),
                  id="rr-noaa", name="NOAA weather", misfire_grace_time=180)
        s.add_job(self._run("radar_archive"), IntervalTrigger(minutes=10),
              id="radar-archive", name="NWS Hawaii radar archive", misfire_grace_time=180)
        s.add_job(self._run("official_weather_media"), IntervalTrigger(minutes=10),
              id="official-weather-media", name="Official NHC/NWS media", misfire_grace_time=180)

        # ── NWS Hawaiʻi by-county (local stitch+play). Offset from :00/:30 storms.
        s.add_job(
            self._run("nws_hawaii"),
            CronTrigger(minute="7,22,37,52"),
            id="nws-hawaii-counties",
            name="NWS Hawaii by county",
            misfire_grace_time=120,
        )

        # ── Kīlauea (hourly). Hash ignores the clock so unchanged USGS/HVO
        #    does not republish. Grok/Cursor synthesis is a separate 2×/day drain.
        s.add_job(self._run("kilauea"), IntervalTrigger(minutes=60),
                  id="rr-kilauea", name="Kīlauea", misfire_grace_time=180)

        # ── Time chime (:00 and :30 HST) — bell + time_HHMM.mp3 ───────────────
        # Uses all 48 clips (time_0000 … time_2330) via Stream Director → desktop + OBS
        s.add_job(self._run("hourly_chime"), CronTrigger(minute="0,30"),
                  id="time-chime", name="Time chime (:00/:30)", misfire_grace_time=180)

        # After :30 chime — avoid stacking on the mark
        s.add_job(self._run("remaining_tasks"), CronTrigger(minute=32),
                  id="remaining-tasks", name="Remaining tasks (:32, 1h + failed due)", misfire_grace_time=90)

        # One-day morning-boot MP3 replay (:32 until noon HST). State file ends it.
        s.add_job(self._run("morning_boot_replay"), CronTrigger(minute=32),
                  id="morning-boot-replay",
                  name="Morning boot MP3 replay (:32 until noon)",
                  misfire_grace_time=90)

        s.add_job(self._run_clip_prebuild, CronTrigger(minute=55),
                  id="hourly-clip-prebuild", name="Prebuild hourly clip reports", misfire_grace_time=120)

        # Stagger :00 voice/data pile-up — chime alone at :00
        s.add_job(self._run("hourly_clip_reports"), CronTrigger(minute=2),
                  id="hourly-clip-reports", name="Play hourly clip reports", misfire_grace_time=120)

        # Local EQ WAV: on the hour + poll for new local M≥2.0
        s.add_job(self._run("earthquake_hourly"), CronTrigger(minute=8),
                  id="earthquake-hourly", name="Earthquake hourly local WAV", misfire_grace_time=180)
        s.add_job(
            self._eq_poll_m2,
            IntervalTrigger(minutes=10),
            id="earthquake-m2-poll",
            name="Earthquake local M≥2 poll",
            misfire_grace_time=120,
        )
        s.add_job(
            self._run("council_quake"),
            IntervalTrigger(minutes=2),
            id="council-quake",
            name="Ava USGS quake Telegram",
            misfire_grace_time=90,
        )
        s.add_job(
            self._run("council_bruce_stats"),
            CronTrigger(hour="7,15,21", minute=18),
            id="council-bruce-stats",
            name="Bruce measured desk sample",
            misfire_grace_time=300,
        )

        s.add_job(self._run("solar_weather"), CronTrigger(minute=4),
                  id="hourly-solar-weather", name="Hourly solar+weather", misfire_grace_time=120)

        s.add_job(
            self._run_solar_notes,
            IntervalTrigger(minutes=30),
            id="solar-notes-quarter-hour",
            name="Solar Notes EcoFlow status (every 30 minutes)",
            misfire_grace_time=120,
        )
        s.add_job(
            self._run_hybrid_charge_status,
            IntervalTrigger(minutes=30),
            id="hybrid-charge-status",
            name="Hybrid charge status (every 30 minutes)",
            misfire_grace_time=120,
        )

        s.add_job(self._run("system_perf"), CronTrigger(minute=6),
                  id="system-performance", name="System performance", misfire_grace_time=120)

        # ── Player economy + Kīlauea multiplier (once per hour) ──────────────
        s.add_job(self._run("player_economy"), IntervalTrigger(minutes=60),
                  id="player-economy-report", name="Player economy", misfire_grace_time=180)

        # ── Morning report (10:00 generate) + 10:12 play ──────────────────────
        s.add_job(self._run("morning_report"), CronTrigger(hour=9, minute=0),
                  id="morning-report", name="Morning report", misfire_grace_time=600)

        s.add_job(
            self._run("report_readiness"),
            CronTrigger(minute="*/5"),
            id="report-readiness",
            name="Report readiness poll",
            misfire_grace_time=300,
        )
        s.add_job(
            self._run("report_periodic_audio"),
            CronTrigger(minute="*/5"),
            id="report-periodic-audio",
            name="Report audio periodic replay",
            misfire_grace_time=120,
        )

        s.add_job(self._run("morning_report_play"), CronTrigger(hour=9, minute=5),
                  id="morning-report-play", name="Morning report play", misfire_grace_time=300)

        s.add_job(self._run("day_reports_morning"), CronTrigger(hour=9, minute=10),
                  id="day-reports-morning", name="Morning slot reports", misfire_grace_time=300)

        s.add_job(self._run("midday_report"), CronTrigger(hour=12, minute=0),
                  id="midday-report", name="Midday status (12:00)", misfire_grace_time=600)

        s.add_job(self._run("midday_report_play"), CronTrigger(hour=12, minute=5),
                  id="midday-report-play", name="Midday report play", misfire_grace_time=300)

        s.add_job(self._run("day_reports_midday"), CronTrigger(hour=13, minute=0),
                  id="day-reports-midday", name="Midday slot reports", misfire_grace_time=300)

        # Due-board catch-up (mandatory only; never late)
        s.add_job(self._run("daily_reports_catchup"), CronTrigger(hour=14, minute=0),
                  id="daily-reports-catchup", name="Daily reports catch-up", misfire_grace_time=600)

        # Evening long-form generate/play removed (was looping evening-report-current.wav).

        s.add_job(self._run("day_reports_evening"), CronTrigger(hour=18, minute=0),
                  id="day-reports-evening", name="Evening slot reports", misfire_grace_time=300)

        # Optional late report 22:00 / play 22:12 — never boot catch-up
        s.add_job(self._run("late_report"), CronTrigger(hour=21, minute=0),
                  id="late-report", name="Late report (21:00)", misfire_grace_time=600)

        s.add_job(self._run("late_report_play"), CronTrigger(hour=21, minute=8),
                  id="late-report-play", name="Late report play", misfire_grace_time=300)

        s.add_job(self._run("late_report"), CronTrigger(hour=23, minute=30),
                  id="late-final-report", name="Final report (23:30)", misfire_grace_time=600)

        # After morning generate/play runway
        s.add_job(self._run("merged_morning"), CronTrigger(hour=10, minute=20),
                  id="merged-morning-summary", name="Merged morning summary", misfire_grace_time=300)

        s.add_job(self._run("cursor_fallback"), CronTrigger(hour="10,16", minute=22),
                  id="cursor-fallback", name="Cursor report fallback", misfire_grace_time=600)

        s.add_job(self._run("governance_daily"), CronTrigger(hour=10, minute=23),
                  id="governance-daily", name="RootRecord governance daily", misfire_grace_time=300)

        s.add_job(self._run("api_prices"), CronTrigger(hour=10, minute=25),
                  id="api-prices", name="Public API price catalog", misfire_grace_time=300)

        s.add_job(self._run("code_review"), CronTrigger(hour="11,17", minute=20),
                  id="code-review", name="Write review pack (no apply)", misfire_grace_time=600)

        s.add_job(self._run("governance_self_update"), IntervalTrigger(
                      hours=1,
                      start_date=datetime.now() + timedelta(hours=1),
                  ),
                  id="governance-self-update", name="Governance self-update after boot grace", misfire_grace_time=300)

        # ── Economy brief (15:00 HST daily) ──────────────────────────────────
        s.add_job(self._run("economy_brief"), CronTrigger(hour=15, minute=0),
                  id="economy-brief", name="Economy brief", misfire_grace_time=300)

        # ── AdSense reports (boot + end-of-day only) ─────────────────────────
        # Boot fires from main.py lifespan. EOD close at 21:00 HST.
        s.add_job(self._run_adsense_eod, CronTrigger(hour=21, minute=0),
                  id="adsense-eod", name="AdSense end-of-day report", misfire_grace_time=600)
        s.add_job(self._run_admob_eod, CronTrigger(hour=21, minute=5),
                  id="admob-eod", name="AdMob end-of-day report", misfire_grace_time=600)

        # Late-night relay — :20 so it does not clash with late report at 22:00
        s.add_job(self._run("overnight"),
                  CronTrigger(hour=22, minute=20),
                  id="overnight-relay", name="Late-night relay", misfire_grace_time=300)

        # OBS rotator + Kīlauea cam embeds are opt-in (Ava Ops obs). Not on boot.
        s.add_job(self._run("minecraft_live"), IntervalTrigger(minutes=10),
                  id="minecraft-live", name="Minecraft in-game detect", misfire_grace_time=120)

        # Hurricane desk — staged so fetch / build / radio / OBS never share a minute
        # with chimes, clip reports, or morning/midday/evening generate+play.
        s.add_job(self._run("hurricane_fetch"), CronTrigger(hour="5,9,12,16,20", minute=40),
                  id="hurricane-fetch", name="Hurricane fetch NHC/RAMMB/JTWC", misfire_grace_time=180)
        s.add_job(self._run("hurricane_desk"), CronTrigger(hour="5,9,12,20", minute=50),
                  id="hurricane-desk", name="Hurricane desk text + WAV", misfire_grace_time=180)
        s.add_job(self._run("hurricane_desk"), CronTrigger(hour=16, minute=55),
                  id="hurricane-desk-evening", name="Hurricane desk evening build", misfire_grace_time=180)
        s.add_job(self._run("hurricane_radio"), CronTrigger(hour=6, minute=35),
                  id="hurricane-radio-am", name="Hurricane desk on radio (06:35)", misfire_grace_time=180)
        s.add_job(self._run("hurricane_radio"), CronTrigger(hour=13, minute=12),
                  id="hurricane-radio-mid", name="Hurricane desk on radio (13:12)", misfire_grace_time=180)
        s.add_job(self._run("hurricane_radio"), CronTrigger(hour=17, minute=2),
                  id="hurricane-radio-pm", name="Hurricane desk on radio (17:02)", misfire_grace_time=180)

        s.add_job(self._run("ecoflow_quota"), IntervalTrigger(minutes=2),
                  id="ecoflow-quota", name="EcoFlow quota refresh", misfire_grace_time=60)

        s.add_job(self._run("drive_automation"), IntervalTrigger(minutes=30),
                  id="drive-automation", name="River car DC drive session", misfire_grace_time=120)

        s.add_job(self._run("panels_cam"), IntervalTrigger(minutes=15),
                  id="panels-cam", name="Panels cam still (River car DC)", misfire_grace_time=120)

        s.add_job(self._run("energy_report"), IntervalTrigger(minutes=30),
                  id="energy-report", name="Carly energy desk + Rear Shed still", misfire_grace_time=180)

        s.add_job(self._run("council_health"), IntervalTrigger(minutes=5),
                  id="council-health", name="Ava/Bruce/Carly systems health", misfire_grace_time=90)

        s.add_job(self._run("public_health"), IntervalTrigger(minutes=5),
                  id="public-health", name="Status + radio public health", misfire_grace_time=90)

        s.add_job(self._run_fs_index(), IntervalTrigger(minutes=15),
                  id="fs-index", name="Live directory index", misfire_grace_time=90)

        # Host CPU/RAM samples for solar/status desk history charts (~1/min)
        s.add_job(self._sample_host, IntervalTrigger(minutes=1),
                  id="host-sample", name="Host CPU/RAM sample", misfire_grace_time=45)

        s.add_job(self._run("log_cleanup"), CronTrigger(hour=4, minute=20),
                  id="log-cleanup", name="Delete log files older than 7 days", misfire_grace_time=300)

        s.add_job(self._run("user_qrcodes"), IntervalTrigger(hours=6),
                  id="user-qrcodes", name="User QR backfill", misfire_grace_time=120)

        s.add_job(self._run("account_import"), IntervalTrigger(hours=6),
                  id="account-import", name="Identity + membership import", misfire_grace_time=300)

        # ── D1 ← host MySQL (throttled). Full wallet rewrite every 5m burned free tier.
        s.add_job(self._run("d1_sync"), IntervalTrigger(hours=6),
                  id="d1-sync", name="MySQL → D1 Minecraft cache", misfire_grace_time=600)

        s.add_job(self._run("inbox_drain"), IntervalTrigger(minutes=5),
                  id="inbox-drain", name="CF offline inbox → local", misfire_grace_time=120)

        s.add_job(self._run("stripe_poll"), IntervalTrigger(minutes=30),
                  id="stripe-poll", name="Stripe finance snapshot", misfire_grace_time=180)

        s.add_job(self._run("ltc_pending"), IntervalTrigger(minutes=30),
                  id="ltc-pending", name="unMineable LTC pending withdraw", misfire_grace_time=600)

        s.add_job(self._run("vercel_builds"), IntervalTrigger(minutes=5),
                  id="vercel-builds", name="Vercel build logs → docs", misfire_grace_time=120)

        log.info("Registered %d cron jobs (night-sleep gated)",
                 len(s.get_jobs()))

    @staticmethod
    async def _sample_host():
        try:
            from apps.core.crons.since_last_fire.solar_weather import record_host_sample
            record_host_sample()
        except Exception as e:
            log.debug("host sample skipped: %s", e)
        try:
            from apps.core.services import sun_times, uptime_log, schedule_clock
            sun_times.refresh_if_stale()
            uptime_log.tick()
            schedule_clock.sample_day_start()
        except Exception as e:
            log.debug("sun/uptime sample skipped: %s", e)

    @staticmethod
    async def _run_adsense_eod():
        from apps.core.crons.on_time import adsense_report

        await adsense_report.run("eod")

    @staticmethod
    async def _run_admob_eod():
        from apps.core.crons.on_time import admob_report

        await admob_report.run("eod")

    @staticmethod
    async def _run_clip_prebuild():
        from apps.core.crons.since_last_fire import hourly_clip_reports

        await hourly_clip_reports.prebuild()

    @staticmethod
    async def _run_solar_notes():
        import asyncio

        from apps.core.services.hybrid_reports import update_hybrid_daily_report

        now = datetime.now(ZoneInfo("Pacific/Honolulu"))
        quarter = (now.minute // 15) * 15
        slot = now.replace(minute=quarter, second=0, microsecond=0)
        hybrid = await asyncio.to_thread(update_hybrid_daily_report, slot)
        if not hybrid.get("ok"):
            log.warning("Hybrid daily report update skipped: %s", hybrid.get("detail"))
        else:
            log.info("Hybrid daily report updated: %s", hybrid.get("path"))
        return hybrid

    @staticmethod
    async def _run_hybrid_charge_status():
        import asyncio

        from apps.core.services.hybrid_reports import update_hybrid_charge_status

        result = await asyncio.to_thread(update_hybrid_charge_status)
        if not result.get("ok"):
            log.warning("Hybrid charge status update skipped: %s", result.get("detail"))
        elif result.get("detail") == "inserted":
            log.info("Hybrid charge status inserted: %s", result.get("path"))
        return result

    @staticmethod
    async def _eq_poll_m2():
        from apps.core.services import earthquake_hourly

        await earthquake_hourly.run(reason="poll", force=False)

    @staticmethod
    def _run_fs_index():
        """Exec ~/.ollama/skills/fs-index/scripts/incremental_fs_index.py."""

        async def _job():
            import asyncio
            import sys
            import time
            from pathlib import Path

            from apps.core.services.mysql import log_cron_run

            started_at = int(time.time() * 1000)
            ok = False
            detail = ""
            error = ""
            script = (
                Path.home()
                / ".ollama"
                / "skills"
                / "fs-index"
                / "scripts"
                / "incremental_fs_index.py"
            )
            try:
                if not script.is_file():
                    raise FileNotFoundError(str(script))
                proc = await asyncio.create_subprocess_exec(
                    sys.executable,
                    str(script),
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.PIPE,
                    cwd=str(config.AVA_HOME),
                )
                try:
                    out, err = await asyncio.wait_for(proc.communicate(), timeout=180)
                except TimeoutError:
                    proc.kill()
                    raise TimeoutError("fs-index timed out")
                if proc.returncode != 0:
                    raise RuntimeError(
                        f"exit {proc.returncode} {(err or b'')[:300]!r}"
                    )
                ok = True
                detail = (out or b"").decode("utf-8", "replace").strip()[:200] or "ok"
                log.info("fs-index %s", detail)
            except Exception as exc:
                log.exception("Cron fs_index failed")
                error = str(exc)[:500]
            finally:
                finished_at = int(time.time() * 1000)
                try:
                    await log_cron_run(
                        "fs_index", started_at, finished_at, ok, detail, error
                    )
                except Exception:
                    pass

        _job.__name__ = "fs_index"
        return _job

    @staticmethod
    def _run(name: str):
        """Return an async callable that imports and runs a cron module by name.
        Writes start/finish records to ava_cron MySQL tables (matching old Node.js schema)."""
        async def _job():
            import importlib
            import time
            from apps.core.services.mysql import log_cron_run

            started_at = int(time.time() * 1000)
            ok = False
            detail = ""
            error = ""
            try:
                try:
                    import json
                    from pathlib import Path as _P
                    _nm = _P.home() / ".ollama" / "skills" / "ecoflow-ble-poller" / "store" / "state" / "night-mode.json"
                    if _nm.is_file():
                        _st = json.loads(_nm.read_text(encoding="utf-8"))
                    else:
                        _st = {}
                    if _st.get("sleeping") and name not in NIGHT_POLL:
                        log.info("night sleep skip cron %s", name)
                        return
                except Exception:
                    pass
                # Safety net: OFFLOADED skills should not register (see _WaveScheduler)
                if _skill_offloaded(name):
                    log.info("OFFLOADED skip cron %s (owned by rr-aws)", name)
                    return
                last = None
                mod = None
                skill = _SKILL_ASYNC_CRONS.get(name)
                if skill is not None:
                    import importlib.util

                    if not skill.is_file():
                        raise FileNotFoundError(str(skill))
                    spec = importlib.util.spec_from_file_location(
                        f"skill_cron_{name}", skill
                    )
                    if spec is None or spec.loader is None:
                        raise ImportError(str(skill))
                    mod = importlib.util.module_from_spec(spec)
                    spec.loader.exec_module(mod)
                else:
                    for pkg in (
                        "apps.core.crons.always_on",
                        "apps.core.crons.since_last_fire",
                        "apps.core.crons.on_time",
                        "apps.core.crons",
                    ):
                        try:
                            mod = importlib.import_module(f"{pkg}.{name}")
                            break
                        except ModuleNotFoundError as exc:
                            last = exc
                    if mod is None:
                        raise last or ModuleNotFoundError(name)
                if hasattr(mod, "run"):
                    await mod.run()
                    ok = True
                    detail = "ok"
                else:
                    log.warning("Cron %s has no run() function", name)
                    detail = "no_run_fn"
            except Exception as exc:
                log.exception("Cron %s failed", name)
                error = str(exc)[:500]
            finally:
                finished_at = int(time.time() * 1000)
                try:
                    await log_cron_run(name, started_at, finished_at, ok, detail, error)
                except Exception:
                    pass  # never let DB logging kill the scheduler
        _job.__name__ = name
        return _job

    async def start(self):
        if not config.ENABLE_SCHEDULER:
            log.info("Scheduler disabled (ENABLE_SCHEDULER=false)")
            return
        self._apscheduler.start()
        log.info("Scheduler started  timezone=Pacific/Honolulu  mode=always-on")
        try:
            from apps.core.services.feature_toggles import is_on

            self.set_obs_jobs(is_on("obs"))
        except Exception as e:
            log.warning("OBS job gate failed: %s", e)
        try:
            await write_heartbeat()
        except Exception as e:
            log.warning("Immediate heartbeat failed: %s", e)

    async def stop(self):
        if self._apscheduler.running:
            self._apscheduler.shutdown(wait=False)
            log.info("Scheduler stopped")

    def get_jobs(self) -> list[dict]:
        out = []
        for j in self._apscheduler.get_jobs():
            next_run = j.next_run_time.isoformat() if j.next_run_time else None
            next_at = int(j.next_run_time.timestamp() * 1000) if j.next_run_time else 0
            every_ms = 0
            trig = j.trigger
            interval = getattr(trig, "interval", None)
            if interval is not None:
                try:
                    every_ms = int(interval.total_seconds() * 1000)
                except Exception:
                    every_ms = 0
            out.append(
                {
                    "id": j.id,
                    "name": j.name,
                    "next_run": next_run,
                    "nextAt": next_at,
                    "everyMs": every_ms or 3_600_000,
                    "cronHint": j.name or str(trig),
                    "running": False,
                    "disabled": False,
                    "lastFiredAt": None,
                }
            )
        return out

    def ensure_midday_job(self) -> dict:
        """Hot path: register midday-report if missing without full recycle."""
        if self._apscheduler.get_job("midday-report"):
            job = self._apscheduler.get_job("midday-report")
            return {
                "ok": True,
                "added": False,
                "id": "midday-report",
                "next_run": job.next_run_time.isoformat() if job and job.next_run_time else None,
            }
        need = _job_wave("midday-report")
        if need > config.CRON_WAVE:
            return {
                "ok": False,
                "added": False,
                "id": "midday-report",
                "detail": f"wave {need} > AVA_CRON_WAVE={config.CRON_WAVE}",
            }
        self._apscheduler.add_job(
            self._run("midday_report"),
            CronTrigger(hour=12, minute=0, timezone="Pacific/Honolulu"),
            id="midday-report",
            name="Midday status (12:00)",
            misfire_grace_time=300,
            replace_existing=True,
        )
        job = self._apscheduler.get_job("midday-report")
        log.info("Hot-registered midday-report next=%s", job.next_run_time if job else None)
        return {
            "ok": True,
            "added": True,
            "id": "midday-report",
            "next_run": job.next_run_time.isoformat() if job and job.next_run_time else None,
        }

    def ensure_council_health_job(self) -> dict:
        """Hot path: register Ava/Bruce/Carly health check if missing."""
        jid = "council-health"
        if self._apscheduler.get_job(jid):
            job = self._apscheduler.get_job(jid)
            return {
                "ok": True,
                "added": False,
                "id": jid,
                "next_run": job.next_run_time.isoformat() if job and job.next_run_time else None,
            }
        need = _job_wave(jid)
        if need > config.CRON_WAVE:
            return {
                "ok": False,
                "added": False,
                "id": jid,
                "detail": f"wave {need} > AVA_CRON_WAVE={config.CRON_WAVE}",
            }
        self._apscheduler.add_job(
            self._run("council_health"),
            IntervalTrigger(minutes=5),
            id=jid,
            name="Ava/Bruce/Carly systems health",
            misfire_grace_time=90,
            replace_existing=True,
        )
        job = self._apscheduler.get_job(jid)
        log.info("Hot-registered council-health next=%s", job.next_run_time if job else None)
        return {
            "ok": True,
            "added": True,
            "id": jid,
            "next_run": job.next_run_time.isoformat() if job and job.next_run_time else None,
        }

    def ensure_public_health_job(self) -> dict:
        """Hot path: register status/radio public health if missing."""
        jid = "public-health"
        if self._apscheduler.get_job(jid):
            job = self._apscheduler.get_job(jid)
            return {
                "ok": True,
                "added": False,
                "id": jid,
                "next_run": job.next_run_time.isoformat() if job and job.next_run_time else None,
            }
        need = _job_wave(jid)
        if need > config.CRON_WAVE:
            return {
                "ok": False,
                "added": False,
                "id": jid,
                "detail": f"wave {need} > AVA_CRON_WAVE={config.CRON_WAVE}",
            }
        self._apscheduler.add_job(
            self._run("public_health"),
            IntervalTrigger(minutes=5),
            id=jid,
            name="Status + radio public health",
            misfire_grace_time=90,
            replace_existing=True,
        )
        job = self._apscheduler.get_job(jid)
        log.info("Hot-registered public-health next=%s", job.next_run_time if job else None)
        return {
            "ok": True,
            "added": True,
            "id": jid,
            "next_run": job.next_run_time.isoformat() if job and job.next_run_time else None,
        }

    def set_obs_jobs(self, enabled: bool) -> dict:
        """Schedule OBS rotator + Kīlauea embed refresh only when Ava Ops obs is on."""
        specs = (
            ("broadcast-loop", IntervalTrigger(seconds=20), "OBS daily loop rotator", 30, "broadcast_loop"),
            (
                "kilauea-cams",
                IntervalTrigger(minutes=5),
                "Kīlauea V1/V2/V3 embed refresh",
                90,
                "kilauea_cams",
            ),
            (
                "hurricane-obs",
                CronTrigger(hour="6,17", minute=10),
                "Hurricane OBS slides",
                120,
                "hurricane_obs",
            ),
        )
        if not enabled:
            removed = []
            for job_id, *_ in specs:
                if self._apscheduler.get_job(job_id):
                    self._apscheduler.remove_job(job_id)
                    removed.append(job_id)
            if removed:
                log.info("OBS jobs unscheduled (obs toggle off): %s", ",".join(removed))
            return {"ok": True, "enabled": False, "present": False}
        wrapped = _WaveScheduler(self._apscheduler)
        added: list[str] = []
        blocked: str | None = None
        for job_id, trigger, name, grace, cron_name in specs:
            need = _job_wave(job_id)
            if need > config.CRON_WAVE:
                blocked = f"wave {need} > AVA_CRON_WAVE={config.CRON_WAVE}"
                continue
            if self._apscheduler.get_job(job_id):
                continue
            wrapped.add_job(
                self._run(cron_name),
                trigger,
                id=job_id,
                name=name,
                misfire_grace_time=grace,
            )
            added.append(job_id)
        if added:
            log.info("OBS jobs scheduled (obs toggle on): %s", ",".join(added))
        present = self._apscheduler.get_job("broadcast-loop") is not None
        if blocked and not present:
            return {"ok": False, "enabled": False, "detail": blocked}
        return {
            "ok": True,
            "enabled": True,
            "present": present,
            "added": bool(added),
        }

    async def run_job_now(self, job_id: str) -> dict:
        """Run a registered job immediately, out of band from its schedule.

        Awaited inline so the caller gets the real outcome instead of a
        fire-and-forget ack; cron bodies also log themselves to ava_cron.
        """
        job = self._apscheduler.get_job(job_id)
        if job is None:
            return {
                "ok": False,
                "detail": f"unknown job {job_id!r}",
                "known": [j.id for j in self._apscheduler.get_jobs()],
            }
        try:
            result = job.func()
            if inspect.isawaitable(result):
                await result
            return {"ok": True, "id": job_id, "name": job.name}
        except Exception as exc:
            log.exception("Manual run of %s failed", job_id)
            return {"ok": False, "id": job_id, "name": job.name, "detail": str(exc)}
