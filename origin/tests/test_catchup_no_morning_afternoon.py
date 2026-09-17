from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from apps.core.services.daily_report_board import (
    catchup_window_open,
    next_catchup_slot,
)

HST = ZoneInfo("Pacific/Honolulu")
AFTERNOON = datetime(2026, 9, 15, 14, 0, tzinfo=HST)
MORNING = datetime(2026, 9, 15, 10, 30, tzinfo=HST)


def test_catchup_window_morning_closes_at_noon():
    assert catchup_window_open("morning", MORNING) is True
    assert catchup_window_open("morning", AFTERNOON) is False
    assert catchup_window_open("midday", AFTERNOON) is True


def test_afternoon_catchup_skips_failed_morning(tmp_path: Path, monkeypatch):
    from apps.core.services import daily_report_board

    monkeypatch.setattr(daily_report_board, "STATE_PATH", tmp_path / "daily-reports-due.json")
    data = daily_report_board.ensure_today(now=AFTERNOON)
    data["slots"]["morning"]["status"] = "failed"
    data["slots"]["midday"]["status"] = "failed"
    daily_report_board._write(data)
    assert next_catchup_slot(now=AFTERNOON) == "midday"
    assert next_catchup_slot(now=MORNING) == "morning"


def test_play_if_due_skips_evening():
    import asyncio

    from apps.core.services import report_periodic_audio

    out = asyncio.run(report_periodic_audio.play_if_due("evening", reason="test", force=True))
    assert out.get("skipped") is True
    assert out.get("detail") == "evening_removed"


def test_play_if_due_skips_morning_after_noon(monkeypatch):
    import asyncio

    from apps.core.services import report_periodic_audio

    async def _run():
        out = await report_periodic_audio.play_if_due("morning", reason="test", force=True)
        assert out.get("skipped") is True
        assert out.get("detail") == "morning_after_noon"

    # Freeze "now" inside play_if_due by patching datetime used there.
    class _Frozen:
        @classmethod
        def now(cls, tz=None):
            return AFTERNOON

    monkeypatch.setattr(report_periodic_audio, "datetime", _Frozen)
    asyncio.run(_run())


def test_run_morning_slot_skips_after_noon(monkeypatch):
    import asyncio

    from apps.core.services import day_reports

    class _Frozen:
        @classmethod
        def now(cls, tz=None):
            return AFTERNOON

    monkeypatch.setattr(day_reports, "datetime", _Frozen)
    out = asyncio.run(day_reports.run_morning_slot())
    assert out.get("skipped") is True
    assert out.get("reason") == "after_noon"
